#!/usr/bin/env python3
"""Build a bidirectional-title, TMDB-to-AniTube index for the Android TV app.

Inputs:
  * hikkaMapped.json: AniTube newsId, Hikka Ukrainian title, MAL ID
  * AniBridge v3: MAL/AniList/TMDB links and episode ranges
  * AniList GraphQL: English, romaji and native title variants

The generated index is optimized for TMDB detail lookup. Each TMDB movie or
season key contains all matching AniTube releases and preserves AniBridge's
episode-range translation. Title lookup arrays are also emitted for local
search by Hikka Ukrainian, AniTube Ukrainian, English and romaji titles.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
import subprocess
import sys
import time
from collections import defaultdict
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
HIKKA_DEFAULT = ROOT / "hikkaMapped.json"
PARSE_RESULTS_DEFAULT = ROOT / "parseSearchResults.json"
BRIDGE_URL = "https://github.com/anibridge/anibridge-mappings/releases/latest/download/mappings.min.json"
ANILIST_URL = "https://graphql.anilist.co"
ANILIST_CACHE_DEFAULT = ROOT / ".local/anilist-title-cache.json"
OUTPUT_DEFAULT = ROOT / "tv-player/src/main/assets/hikka-anitube-index.json"
BATCH_SIZE = 15


def curl_json(url: str, *, data: dict[str, Any] | None = None, timeout: int = 45) -> Any:
    command = ["curl", "-4", "--fail", "--silent", "--show-error", "--location",
               "--retry", "4", "--retry-all-errors", "--retry-delay", "1",
               "--connect-timeout", "8", "--max-time", str(timeout), url]
    if data is not None:
        command[1:1] = ["-X", "POST", "-H", "Content-Type: application/json",
                        "--data-binary", "@-"]
    result = subprocess.run(command, input=json.dumps(data) if data is not None else None,
        capture_output=True, text=True, check=True)
    return json.loads(result.stdout)


def load_bridge(path: Path | None) -> tuple[dict[str, Any], str]:
    if path:
        return json.loads(path.read_text(encoding="utf-8")), str(path)
    return curl_json(BRIDGE_URL, timeout=90), BRIDGE_URL


def anilist_titles(ids: set[int], cache_path: Path, delay: float) -> dict[int, dict[str, Any]]:
    cache: dict[str, Any] = {}
    if cache_path.exists():
        try:
            cache = json.loads(cache_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            cache = {}
    missing = sorted(ids - {int(key) for key in cache if key.isdigit()})
    failed_ids: list[int] = []
    for offset in range(0, len(missing), BATCH_SIZE):
        batch = missing[offset:offset + BATCH_SIZE]
        aliases = " ".join(
            f'a{index}: Media(id: {ani_id}, type: ANIME) '
            "{ id title { romaji english native } synonyms }"
            for index, ani_id in enumerate(batch)
        )
        payload = {"query": "query { " + aliases + " }"}
        last_error: Exception | None = None
        for attempt in range(5):
            try:
                response = curl_json(ANILIST_URL, data=payload, timeout=45)
                last_error = None
                break
            except (subprocess.CalledProcessError, json.JSONDecodeError) as error:
                last_error = error
                time.sleep(min(2 ** attempt, 30) + random.uniform(0, 1))
        if last_error is not None:
            failed_ids.extend(batch)
            print(f"AniList batch failed ({batch[0]}..{batch[-1]}): {last_error}",
                  file=sys.stderr, flush=True)
            continue
        for index, ani_id in enumerate(batch):
            media = (response.get("data") or {}).get(f"a{index}")
            if media:
                cache[str(ani_id)] = media
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        cache_path.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
        print(f"AniList titles: {min(offset + len(batch), len(missing))}/{len(missing)} missing fetched",
              flush=True)
        if delay:
            time.sleep(delay)
    anilist_titles.failed_ids = failed_ids
    return {int(key): value for key, value in cache.items() if key.isdigit()}


def ranges_for(target_map: dict[str, Any]) -> list[dict[str, str]]:
    return [{"malRange": str(mal_range), "tmdbRange": str(tmdb_range)}
            for mal_range, tmdb_range in target_map.items()]


def mapped_episode_count(target: dict[str, Any]) -> int | None:
    episodes: set[int] = set()
    for item in target["episodeRanges"]:
        value = item["malRange"]
        for part in re.split(r"[|,]", value):
            match = re.fullmatch(r"\s*(\d+)\s*(?:-\s*(\d*)\s*)?", part)
            if not match or (match.group(2) == "" and "-" in part):
                return None
            start = int(match.group(1))
            end = int(match.group(2)) if match.group(2) else start
            if end < start or end - start > 10000:
                return None
            episodes.update(range(start, end + 1))
    return len(episodes) or None


def has_explicit_season(title: str) -> bool:
    return bool(re.search(
        r"(?:\bseason\s*0*\d+\b|\b0*\d+\s*(?:st|nd|rd|th)?\s+season\b|"
        r"\bсезон\s*0*\d+\b|\b0*\d+\s*сезон\b|\bs\s*0*\d+\b)",
        title, re.IGNORECASE,
    ))


def explicit_season_number(title: str) -> int | None:
    match = re.search(
        r"\bseason\s*0*(\d+)\b|\b0*(\d+)\s*(?:st|nd|rd|th)?\s+season\b|"
        r"\bсезон\s*0*(\d+)\b|\b0*(\d+)\s*сезон\b|\bs\s*0*(\d+)\b",
        title, re.IGNORECASE,
    )
    if not match:
        return None
    return next((int(value) for value in match.groups() if value), None)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--hikka", type=Path, default=HIKKA_DEFAULT)
    parser.add_argument("--parse-results", type=Path, default=PARSE_RESULTS_DEFAULT,
        help="AniTube catalog JSON containing direct pageUrl values keyed by newsId")
    parser.add_argument("--anibridge", type=Path,
        help="Local AniBridge mappings JSON; defaults to downloading the latest release")
    parser.add_argument("--anilist-cache", type=Path, default=ANILIST_CACHE_DEFAULT)
    parser.add_argument("--output", type=Path, default=OUTPUT_DEFAULT)
    parser.add_argument("--delay", type=float, default=0.15,
        help="pause between AniList batches")
    args = parser.parse_args()

    hikka_rows = json.loads(args.hikka.read_text(encoding="utf-8"))
    parse_rows = json.loads(args.parse_results.read_text(encoding="utf-8"))
    releases = {}
    for row in parse_rows:
        try:
            news_id = int(row["newsId"])
        except (KeyError, TypeError, ValueError):
            continue
        if news_id > 0 and row.get("pageUrl"):
            releases[str(news_id)] = {
                "newsId": news_id,
                "pageUrl": row["pageUrl"],
                "title": row.get("title", ""),
                "posterUrl": row.get("posterUrl"),
                "year": row.get("year"),
                "airedEpisodes": row.get("airedEpisodes"),
                "totalEpisodes": row.get("totalEpisodes"),
                "durationMinutes": row.get("durationMinutes"),
            }
    bridge, bridge_source = load_bridge(args.anibridge)
    schema_version = (bridge.get("$meta") or {}).get("schema_version", "unknown")

    hikka_by_mal: dict[int, list[dict[str, Any]]] = defaultdict(list)
    for row in hikka_rows:
        try:
            mal_id, news_id = int(row["hikkaMalId"]), int(row["newsId"])
        except (KeyError, TypeError, ValueError):
            continue
        if mal_id <= 0 or news_id <= 0:
            continue
        hikka_by_mal[mal_id].append(row)

    # Bridge is intentionally directional, so build the reverse TMDB index from
    # every MAL source descriptor and its target descriptors.
    tmdb_entries: dict[str, list[dict[str, Any]]] = defaultdict(list)
    series_entries: dict[str, dict[tuple[int, int], dict[str, Any]]] = defaultdict(dict)
    targets_by_mal: dict[int, list[tuple[str, str, int | None, dict[str, Any]]]] = defaultdict(list)
    mal_to_anilist: dict[int, set[int]] = defaultdict(set)
    mapped_release_ids: set[int] = set()
    title_rows: dict[tuple[str, str, int], dict[str, Any]] = {}

    for source, targets in bridge.items():
        if not source.startswith("mal:") or not isinstance(targets, dict):
            continue
        mal_id = int(source.split(":", 1)[1])
        if mal_id not in hikka_by_mal:
            continue
        for target, episode_map in targets.items():
            if target.startswith("anilist:"):
                try:
                    mal_to_anilist[mal_id].add(int(target.split(":", 1)[1]))
                except ValueError:
                    pass
                continue
            parts = target.split(":")
            if len(parts) == 3 and parts[0] == "tmdb_show" and parts[2].startswith("s"):
                try:
                    tmdb_id, season = int(parts[1]), int(parts[2][1:])
                except ValueError:
                    continue
                key, media_type = f"tmdb_show:{tmdb_id}:s{season}", "tv"
            elif len(parts) == 2 and parts[0] == "tmdb_movie":
                try:
                    tmdb_id = int(parts[1])
                except ValueError:
                    continue
                key, media_type = f"tmdb_movie:{tmdb_id}", "movie"
            else:
                continue
            if not isinstance(episode_map, dict):
                continue
            episode_ranges = ranges_for(episode_map)
            targets_by_mal[mal_id].append((key, media_type, season if media_type == "tv" else None,
                                           {"tmdbId": tmdb_id, "episodeRanges": episode_ranges}))

    # A MAL entry can span many TMDB seasons (for example, One Piece). Keep
    # those relations at series scope; only a single-target MAL mapping is a
    # safe exact-season assignment for every Hikka release attached to it.
    scoped_keys_by_release: dict[tuple[int, int], set[str]] = {}
    for mal_id, targets in targets_by_mal.items():
        unique_targets = {target[0]: target for target in targets}
        for release in hikka_by_mal[mal_id]:
            news_id = int(release["newsId"])
            if len(unique_targets) == 1:
                scoped_keys_by_release[(mal_id, news_id)] = set(unique_targets)
                continue
            catalog = releases.get(str(news_id), {})
            titles = (release.get("title", ""), release.get("hikkaTitleUa", ""),
                      catalog.get("title", ""))
            episode_count = catalog.get("totalEpisodes") or catalog.get("airedEpisodes")
            if not episode_count or not any(has_explicit_season(title) for title in titles):
                continue
            matching = [key for key, target in unique_targets.items()
                        if target[1] == "tv" and mapped_episode_count(target[3]) == episode_count]
            if len(matching) == 1:
                scoped_keys_by_release[(mal_id, news_id)] = set(matching)
    for mal_id, targets in targets_by_mal.items():
        for key, media_type, season, target in targets:
            tmdb_id = int(target["tmdbId"])
            for release in hikka_by_mal[mal_id]:
                news_id = int(release["newsId"])
                mapped_release_ids.add(news_id)
                exact_scope = key in scoped_keys_by_release.get((mal_id, news_id), set())
                row = {
                    "tmdbType": media_type,
                    "tmdbId": tmdb_id,
                    "tmdbSeason": season,
                    "malId": mal_id,
                    "aniListIds": [],
                    "newsId": news_id,
                    "anitubeTitleUa": release.get("title", ""),
                    "hikkaTitleUa": release.get("hikkaTitleUa", ""),
                    "malRange": [item["malRange"] for item in target["episodeRanges"]],
                    "tmdbRange": [item["tmdbRange"] for item in target["episodeRanges"]],
                    "episodeRanges": target["episodeRanges"],
                    "year": release.get("year"),
                    "scope": "exact" if exact_scope else "series",
                }
                if row["scope"] == "exact":
                    tmdb_entries[key].append(row)
                else:
                    series_key = f"tmdb_show:{tmdb_id}" if media_type == "tv" else f"tmdb_movie:{tmdb_id}"
                    series_entry = series_entries[series_key].setdefault((news_id, mal_id), {
                        "newsId": news_id,
                        "malId": mal_id,
                        "scope": "series",
                        "tmdbTargets": [],
                    })
                    target_record = {"key": key, "episodeRanges": target["episodeRanges"]}
                    if target_record not in series_entry["tmdbTargets"]:
                        series_entry["tmdbTargets"].append(target_record)

    ani_ids = {ani_id for ids in mal_to_anilist.values() for ani_id in ids}
    for entries in tmdb_entries.values():
        for entry in entries:
            entry["aniListIds"] = sorted(mal_to_anilist[entry["malId"]])
    for rows in series_entries.values():
        for row in rows.values():
            row["aniListIds"] = sorted(mal_to_anilist[row["malId"]])
    ani_titles = anilist_titles(ani_ids, args.anilist_cache, args.delay)

    # Build AniList and title indexes from every Hikka release, including
    # releases whose MAL/AniBridge path has no TMDB target yet.
    by_anilist: dict[str, dict[tuple[int, int], dict[str, Any]]] = defaultdict(dict)
    tvdb_seasons_by_mal: dict[int, set[int]] = defaultdict(set)
    for source, targets in bridge.items():
        if not source.startswith("mal:") or not isinstance(targets, dict):
            continue
        try:
            mal_id = int(source.split(":", 1)[1])
        except ValueError:
            continue
        for target in targets:
            parts = target.split(":")
            if len(parts) == 3 and parts[0] == "tvdb_show" and parts[2].startswith("s"):
                try:
                    tvdb_seasons_by_mal[mal_id].add(int(parts[2][1:]))
                except ValueError:
                    pass

    exact_by_release: dict[int, list[dict[str, Any]]] = defaultdict(list)
    for entries in tmdb_entries.values():
        for entry in entries:
            entry["aniListIds"] = sorted(mal_to_anilist[entry["malId"]])
            entry["aniListTitles"] = [ani_titles[ani_id] for ani_id in entry["aniListIds"]
                                      if ani_id in ani_titles]
            exact_by_release[entry["newsId"]].append(entry)

    for mal_id, mal_releases in hikka_by_mal.items():
        ani_ids_for_mal = sorted(mal_to_anilist[mal_id])
        for release in mal_releases:
            news_id = int(release["newsId"])
            catalog = releases.get(str(news_id), {})
            release_title = release.get("title") or catalog.get("title", "")
            hikka_title = release.get("hikkaTitleUa", "")
            season_scope = None
            tvdb_seasons = tvdb_seasons_by_mal.get(mal_id, set())
            if len(tvdb_seasons) == 1:
                season_scope = next(iter(tvdb_seasons))
            if season_scope is None:
                season_scope = explicit_season_number(f"{release_title} {hikka_title}")
            ani_list_titles = [ani_titles[ani_id] for ani_id in ani_ids_for_mal
                               if ani_id in ani_titles]
            mappings = exact_by_release.get(news_id, [])
            for ani_id in ani_ids_for_mal:
                by_anilist[str(ani_id)][(news_id, mal_id)] = {
                    "newsId": news_id,
                    "malId": mal_id,
                    "aniListIds": ani_ids_for_mal,
                    "seasonScope": season_scope,
                    "aniListTitles": ani_list_titles,
                    "tmdbMappings": [{
                        "tmdbType": item["tmdbType"], "tmdbId": item["tmdbId"],
                        "tmdbSeason": item["tmdbSeason"],
                        "episodeRanges": item["episodeRanges"],
                    } for item in mappings],
                }

            all_names = []
            for media in ani_list_titles:
                title = media.get("title") or {}
                all_names.extend([
                    ("english", title.get("english")),
                    ("romaji", title.get("romaji")),
                    ("native", title.get("native")),
                ])
                all_names.extend(("synonym", value) for value in media.get("synonyms", []))
            name_records = [
                {"language": "anitube_uk", "value": release_title},
                {"language": "hikka_uk", "value": hikka_title},
            ] + [{"language": lang, "value": value} for lang, value in all_names if value]
            for name in name_records:
                norm = " ".join(name["value"].casefold().split())
                if norm:
                    title_rows[(name["language"], norm, news_id)] = {
                        "language": name["language"], "title": name["value"],
                        "newsId": news_id, "malId": mal_id, "aniListIds": ani_ids_for_mal,
                    }

    for key, entries in tmdb_entries.items():
        # Deduplicate equivalent MAL mappings while retaining distinct releases.
        tmdb_entries[key] = list({(item["malId"], item["newsId"],
            json.dumps(item["episodeRanges"], sort_keys=True)): item for item in entries}.values())
        tmdb_entries[key].sort(key=lambda item: (item["malId"], item["newsId"]))

    hikka_ids = {int(row["newsId"]) for rows in hikka_by_mal.values() for row in rows}
    missing_ids = sorted(hikka_ids - mapped_release_ids)
    hikka_sha256 = hashlib.sha256(args.hikka.read_bytes()).hexdigest()
    bridge_sha256 = hashlib.sha256(json.dumps(bridge, ensure_ascii=False,
        separators=(",", ":")).encode("utf-8")).hexdigest()
    anilist_cache_sha256 = hashlib.sha256(args.anilist_cache.read_bytes()).hexdigest()
    output = {
        "meta": {
            "schemaVersion": 1,
            "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "anibridgeSchema": schema_version,
            "sourceVersions": {
                "anibridgeSchema": schema_version,
                "hikkaMappedSha256": hikka_sha256,
                "parseSearchResultsSha256": hashlib.sha256(args.parse_results.read_bytes()).hexdigest(),
                "aniListTitleCacheSha256": anilist_cache_sha256,
            },
            "anibridgeSource": bridge_source,
            "hikkaSource": str(args.hikka),
            "releaseCount": len(hikka_ids),
            "tmdbKeyCount": len(tmdb_entries),
        },
        "byTmdb": dict(sorted(tmdb_entries.items())),
        "byAniList": {key: sorted(rows.values(), key=lambda item: (item["malId"], item["newsId"]))
                      for key, rows in sorted(by_anilist.items())},
        "bySeries": {key: sorted(rows.values(), key=lambda item: (item["malId"], item["newsId"]))
                     for key, rows in sorted(series_entries.items())},
        "releases": releases,
        "byTitle": sorted(title_rows.values(), key=lambda item: (item["language"], item["title"].casefold(), item["newsId"])),
    }
    content_payload = json.dumps({"byTmdb": output["byTmdb"], "byAniList": output["byAniList"],
                                  "bySeries": output["bySeries"],
                                  "releases": output["releases"], "byTitle": output["byTitle"]},
        ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    output["meta"]["contentHash"] = hashlib.sha256(content_payload).hexdigest()
    output["meta"]["sourceVersions"]["anibridgeSha256"] = bridge_sha256
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, separators=(",", ":")) + "\n",
                           encoding="utf-8")
    report = {
        "hikkaRecords": len(hikka_ids),
        "aniBridgeMatchedReleases": len(mapped_release_ids),
        "directPageUrls": sum(1 for news_id in hikka_ids if str(news_id) in releases),
        "exactTmdbReleases": len({item["newsId"] for rows in tmdb_entries.values() for item in rows}),
        "seriesScopeReleases": len({item["newsId"] for rows in series_entries.values() for item in rows.values()}),
        "hikkaWithoutTmdbTarget": len(missing_ids),
        "hikkaWithoutTmdbSample": missing_ids[:100],
        "aniListIds": len(ani_ids),
        "aniListTitlesFound": len(ani_titles),
        "aniListTitleFailures": getattr(anilist_titles, "failed_ids", []),
        "tmdbKeys": len(tmdb_entries),
        "titleLookupRows": len(output["byTitle"]),
        "output": str(args.output),
    }
    report_path = args.output.with_name("hikka-anitube-index-report.json")
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Builder failed: {error}", file=sys.stderr)
        raise SystemExit(1)
