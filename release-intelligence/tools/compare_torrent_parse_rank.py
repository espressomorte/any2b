"""Run in an isolated environment with the pinned torrent-parse-rank checkout installed.

python compare_torrent_parse_rank.py --checkout /path/to/checkout --output docs/torrent-parse-rank-audit
No pre-cleaning or Toloka rules are applied to library inputs.
"""
import argparse
import hashlib
import importlib.metadata
import json
import pathlib
import platform
import subprocess
from collections import Counter

from PTT import parse_title
from RTN import parse as rtn_parse


def main():
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument('--checkout', type=pathlib.Path, required=True)
    args.add_argument('--output', type=pathlib.Path, required=True)
    options = args.parse_args()
    fixture = pathlib.Path(__file__).resolve().parents[1] / 'src/test/resources/toloka-random-20.tsv'
    rows = [line.split('\t') for line in fixture.read_text().splitlines() if line and not line.startswith('#')]
    revision = subprocess.check_output(['git', '-C', str(options.checkout), 'rev-parse', 'HEAD'], text=True).strip()
    counts = {'PTT': Counter(), 'RTN': Counter()}
    records = []
    def codec(value):
        return {'hevc': 'H.265', 'avc': 'H.264', 'h264': 'H.264', 'h265': 'H.265', 'x264': 'H.264', 'x265': 'H.265'}.get(str(value).lower(), value or '')
    def source(value):
        # Match the Kotlin fixture's existing BluRay bucket. DVD is kept coarse.
        return {'BluRay REMUX': 'BluRay', 'BDRemux': 'BluRay', 'Blu-ray': 'BluRay', 'WEB-DLRip': 'WEBDLRip'}.get(value, value or '')
    for row in rows:
        expected = dict(zip(['id', 'rawTitle', 'titles', 'year', 'source', 'resolution', 'codec', 'audio', 'subtitles', 'coverage'], row))
        record = {'expected': expected, 'parsers': {}}
        for name, parse in [('PTT', lambda title: parse_title(title, False)), ('RTN', lambda title: rtn_parse(title, json=True))]:
            raw = parse(row[1])
            title = raw.get('title', raw.get('parsed_title', ''))
            normalized = {'title': title, 'year': raw.get('year'), 'source': source(raw.get('quality')), 'resolution': raw.get('resolution', '').replace('unknown', ''), 'codec': codec(raw.get('codec'))}
            checks = {'oneExactAlias': title in row[2].split(' ~ '), 'allAliases': [title] == row[2].split(' ~ '), 'year': normalized['year'] == int(row[3]), 'source': normalized['source'] == row[4], 'resolution': normalized['resolution'] == row[5], 'codec': normalized['codec'] == row[6]}
            # Compare seasons and episode numbers only; this API has no episodeTotal.
            coverage = row[9]
            if coverage.startswith('S') and ':' in coverage:
                season, span = coverage[1:].split(':')
                start, end = map(int, span.split('-'))
                checks['seasonAndEpisodes'] = raw.get('seasons', []) == [int(season)] and raw.get('episodes', []) == list(range(start, end + 1))
            elif coverage.startswith('A:'):
                start, end = map(int, coverage[2:].split('/')[0].split('-'))
                checks['seasonAndEpisodes'] = not raw.get('seasons') and raw.get('episodes', []) == list(range(start, end + 1))
            else:
                checks['seasonAndEpisodes'] = not raw.get('seasons') and not raw.get('episodes')
            expected_languages = set(filter(None, (row[7] + ',' + row[8]).split(',')))
            checks['languageUnion'] = set(raw.get('languages', [])) == expected_languages
            for field, passed in checks.items():
                counts[name][field] += int(passed)
            record['parsers'][name] = {'raw': raw, 'normalized': normalized, 'checks': checks}
        records.append(record)
    result = {'repository': 'https://github.com/g0ldyy/torrent-parse-rank', 'revision': revision, 'python': platform.python_version(), 'fixtureSha256': hashlib.sha256(fixture.read_bytes()).hexdigest(), 'dependencies': {name: importlib.metadata.version(name) for name in ['torrent-parse-rank', 'regex', 'pydantic', 'orjson']}, 'summary': counts, 'items': records}
    options.output.parent.mkdir(parents=True, exist_ok=True)
    options.output.with_suffix('.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    report = ['# torrent-parse-rank: ті самі 20 релізів Toloka', '', f'Revision: `{revision}`. Python {platform.python_version()}. Виклики `PTT.parse_title(raw, False)` і `RTN.parse(raw, json=True)`, без pre-cleaning.', '', 'Очікування: `toloka-random-20.tsv`. Нормалізуються лише назви кодеків, WEB-DLRip/WEBDLRip і BluRay/BDRemux відповідно до наявної Kotlin-моделі. DVD залишається DVD: це не доказ DVDRemux. Розділення title на aliases не додається. Метрика seasonAndEpisodes не перевіряє total, тому слабша за контракт Kotlin.', '', '| Перевірка | PTT | RTN |', '| --- | --- | --- |']
    for field in counts['PTT']:
        report.append(f'| {field} | {counts["PTT"][field]}/20 | {counts["RTN"][field]}/20 |')
    report += ['', 'Окремих полів audioLanguages/subtitleLanguages та episodeTotal API не повертає. Загальне languages не зараховується як аудіо: воно може містити мови субтитрів. languageUnion порівнює лише об’єднання очікуваних мов, без поділу на доріжки. Повний критерій нашого контракту бібліотека самостійно не покриває; повний відсоток замість цього не обчислюється.', '', '| ID | PTT title | RTN title | Помилки PTT | Помилки RTN |', '| --- | --- | --- | --- | --- |']
    def cell(value):
        return str(value).replace('|', '\\|').replace('\n', ' ')
    for record in records:
        parsers = record['parsers']
        report.append('| ' + ' | '.join([record['expected']['id'], cell(parsers['PTT']['normalized']['title']), cell(parsers['RTN']['normalized']['title'])] + [', '.join(key for key, passed in parsers[name]['checks'].items() if not passed) or '—' for name in ['PTT', 'RTN']]) + ' |')
    options.output.with_suffix('.md').write_text('\n'.join(report) + '\n')
    print(json.dumps({'revision': revision, 'summary': counts}, indent=2))


if __name__ == '__main__':
    main()
