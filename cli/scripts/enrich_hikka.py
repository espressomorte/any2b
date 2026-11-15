#!/usr/bin/env python3
"""Enrich parseSearchResults.json with Hikka Ukrainian titles and MAL IDs.

For each AniTube record, try the direct Hikka AniTube integration first. If it
returns HTTP 404, search Hikka's anime catalog by title. Search results are
accepted only when exactly one result matches the source year (or when a
single result is returned and the source year is unavailable).

Usage:
    python3 cli/scripts/enrich_hikka.py [path/to/parseSearchResults.json]
"""

from __future__ import annotations

import argparse
import json
import random
import time
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


API = "https://api.hikka.io"
DEFAULT_INPUT = Path(__file__).resolve().parents[2] / "parseSearchResults.json"
FIELDS = ("hikkaTitleUa", "hikkaMalId")


def request_json(url: str, *, body: dict[str, Any] | None = None) -> tuple[int, Any]:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"User-Agent": "any2b-hikka-enrichment/1", "Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = Request(url, data=data, headers=headers, method="POST" if data is not None else "GET")
    last_error: Exception | None = None
    for attempt in range(4):
        try:
            with urlopen(req, timeout=15) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            if error.code == 404:
                error.read()
                return error.code, None
            if error.code not in (429, 500, 502, 503, 504):
                raise
            last_error = error
            error.read()
            retry_after = error.headers.get("Retry-After")
            delay = float(retry_after) if retry_after and retry_after.isdigit() else 1.5 ** attempt
        except (URLError, TimeoutError, OSError) as error:
            last_error = error
            delay = 1.5 ** attempt
        if attempt < 3:
            time.sleep(delay + random.uniform(0, 0.5))
    assert last_error is not None
    raise last_error


def get_hikka_by_anitube_id(news_id: int) -> tuple[int, Any]:
    try:
        return request_json(f"{API}/integrations/anitube/anime/{news_id}")
    except HTTPError as error:
        # Read the error body so the HTTP response is fully consumed.
        error.read()
        return error.code, None


def search_by_title(title: str, year: int | None) -> dict[str, Any] | None:
    query = urlencode({"size": 20})
    _, result = request_json(f"{API}/anime?{query}", body={"query": title})
    matches = result.get("list", []) if isinstance(result, dict) else []
    if year is not None:
        same_year = [item for item in matches if item.get("year") == year]
        if len(same_year) == 1:
            return same_year[0]
        # A wrong or ambiguous year is safer as an unresolved record.
        return None
    return matches[0] if len(matches) == 1 else None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", nargs="?", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--force", action="store_true", help="refresh records already enriched")
    parser.add_argument("--delay", type=float, default=0.25, help="seconds between requests (default: 0.25)")
    parser.add_argument("--dry-run", action="store_true", help="report changes without writing the JSON")
    args = parser.parse_args()

    data = json.loads(args.input.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise SystemExit("Expected the input JSON to be a list of AniTube records")

    stats = {"direct": 0, "search_fallback": 0, "not_found": 0, "ambiguous": 0, "errors": 0, "skipped": 0}
    failures: list[dict[str, Any]] = []
    for index, record in enumerate(data, 1):
        if not args.force and all(record.get(field) for field in FIELDS):
            stats["skipped"] += 1
            continue

        title = str(record.get("title") or "").strip()
        news_id = record.get("newsId")
        year = record.get("year")
        if not title or not isinstance(news_id, int):
            stats["errors"] += 1
            failures.append({"newsId": news_id, "title": title, "error": "missing title or invalid newsId"})
            continue

        try:
            status, item = get_hikka_by_anitube_id(news_id)
            source = "direct"
            if status == 404:
                stats["not_found"] += 1
                item = search_by_title(title, year if isinstance(year, int) else None)
                source = "search_fallback"
                time.sleep(args.delay)
            elif status != 200 or not isinstance(item, dict):
                stats["errors"] += 1
                continue

            if not item:
                stats["ambiguous"] += 1
                continue
            hikka_title = item.get("title_ua")
            mal_id = item.get("mal_id")
            if not isinstance(hikka_title, str) or not isinstance(mal_id, int):
                stats["errors"] += 1
                continue
            record["hikkaTitleUa"] = hikka_title
            record["hikkaMalId"] = mal_id
            stats[source] += 1
        except (HTTPError, URLError, TimeoutError, OSError, ValueError, json.JSONDecodeError) as error:
            stats["errors"] += 1
            failures.append({"newsId": news_id, "title": title,
                "error": f"{type(error).__name__}: {error}"})

        if index % 100 == 0:
            print(f"Processed {index}/{len(data)}: {stats}", flush=True)
        time.sleep(args.delay + random.uniform(0, min(args.delay, 0.2)))

    if not args.dry_run:
        args.input.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        error_path = args.input.with_name(f"{args.input.stem}-errors.json")
        error_path.write_text(json.dumps(failures, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"file": str(args.input), "total": len(data), "stats": stats, "dry_run": args.dry_run}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
