#!/usr/bin/env python3
"""Serve the local review page and proxy read-only AniTube title searches."""

import html
import json
import re
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BASE_URL = "https://anitube.in.ua"
ANILIST_URL = "https://graphql.anilist.co"
RESULT_RE = re.compile(
    r'''itemprop=["']name["'][^>]*>\s*<a\s+href=["']([^"']*anitube\.in\.ua/(\d+)-[^"']+\.html)["'][^>]*>(.*?)</a>''',
    re.IGNORECASE | re.DOTALL,
)


def search_anitube(query):
    url = BASE_URL + "/anime/?" + urllib.parse.urlencode({
        "do": "search", "subaction": "search", "story": query,
    })
    request = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120 Safari/537.36",
        "Referer": BASE_URL + "/",
    })
    with urllib.request.urlopen(request, timeout=30) as response:
        page = response.read().decode("utf-8", errors="replace")
    results, seen = [], set()
    for match in RESULT_RE.finditer(page):
        page_url = html.unescape(match.group(1))
        news_id = int(match.group(2))
        if news_id in seen:
            continue
        seen.add(news_id)
        title = re.sub(r"<[^>]+>", "", match.group(3))
        results.append({"newsId": news_id, "title": html.unescape(title).strip(), "pageUrl": page_url})
    return results


def get_anilist_anime(anime_id):
    payload = {"query": "query ($id: Int) { Media(id: $id, type: ANIME) { id title { romaji english native } seasonYear episodes format siteUrl } }", "variables": {"id": anime_id}}
    request = urllib.request.Request(ANILIST_URL, data=json.dumps(payload).encode("utf-8"), headers={
        "Content-Type": "application/json", "Accept": "application/json", "User-Agent": "Any2B review/1.0",
    }, method="POST")
    with urllib.request.urlopen(request, timeout=30) as response:
        result = json.load(response)
    media = result.get("data", {}).get("Media")
    return media if media and media.get("id") == anime_id else None


class ReviewHandler(BaseHTTPRequestHandler):
    def send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urllib.parse.urlsplit(self.path)
        if parsed.path == "/api/anilist/anime":
            raw_id = urllib.parse.parse_qs(parsed.query).get("id", [""])[0]
            if not raw_id.isdecimal() or int(raw_id) <= 0:
                return self.send_json(400, {"error": "Некоректний AniList ID"})
            try:
                anime = get_anilist_anime(int(raw_id))
                return self.send_json(200, anime) if anime else self.send_json(404, {"error": "Тайтл не знайдено на AniList"})
            except (OSError, urllib.error.URLError, UnicodeError, ValueError, json.JSONDecodeError) as error:
                return self.send_json(502, {"error": f"Не вдалося перевірити AniList: {error}"})
        if parsed.path == "/api/anitube/search":
            query = urllib.parse.parse_qs(parsed.query).get("query", [""])[0].strip()
            if not query or len(query) > 200:
                return self.send_json(400, {"error": "Пошуковий запит порожній або задовгий"})
            try:
                return self.send_json(200, search_anitube(query))
            except (OSError, urllib.error.URLError, UnicodeError) as error:
                return self.send_json(502, {"error": f"Не вдалося виконати пошук AniTube: {error}"})
        if parsed.path in ("/", "/anilist-review.html"):
            body = (ROOT / "anilist-review.html").read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            return self.wfile.write(body)
        self.send_error(404)

    def log_message(self, fmt, *args):
        print("[review-server] " + fmt % args)


if __name__ == "__main__":
    address = ("127.0.0.1", 8765)
    print(f"AniList/AniTube review: http://{address[0]}:{address[1]}")
    ThreadingHTTPServer(address, ReviewHandler).serve_forever()
