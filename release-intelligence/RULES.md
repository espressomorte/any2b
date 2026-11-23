# Rule provenance and scope

The technical vocabulary and handler ordering were expanded after running
[g0ldyy/torrent-parse-rank](https://github.com/g0ldyy/torrent-parse-rank/tree/e21b3e77987c4a51c8168d9d59de9a718a48c990)
version 0.1.0, revision `e21b3e77987c4a51c8168d9d59de9a718a48c990`, on the frozen Toloka fixture.

Reference locations: `crates/ptt-core/src/generated/handlers.json` and
`crates/ptt-core/src/lib.rs`. The upstream project declares GPL-3.0-or-later;
its README credits [PTT](https://github.com/dreulavelle/PTT) and
[RTN](https://github.com/dreulavelle/rank-torrent-name).

This module does not vendor upstream implementation files or the generated
handler table. `TechnicalRules.kt` contains independently expressed JVM rules
for the observed technical spellings and ordering: interlaced/progressive
resolutions, WEB/BD/DVD sources, codecs, HDR/DV, and theatrical editions.
This is a selective behavioral adaptation, not a complete PTT/RTN port.
Do not import the upstream table wholesale without preserving its applicable
license and attribution and evaluating compatibility with distribution of this project.

Local rules intentionally differ:

- Preserve all title candidates and their original UTF-16 positions.
- Parse Ukrainian season/episode blocks atomically, including unknown totals.
- Distinguish DVDRemux from DVD and retain the total episode count.
- Keep subtitle languages separate from audio languages and retain track counts.
- Never infer Russian audio from Cyrillic, or mono audio from a word in a title.
- Preserve unknown brackets and report ambiguity/conflicts.

Reproduction and the original comparison are documented in
[the adaptation report](../docs/release-parser-rule-adaptation.md).
Parser changes are evaluated against the same frozen, manually reviewed
fixtures; expectations must not be rewritten to match parser output.

Run `./gradlew :release-intelligence:test :core:test`. Production search still
uses its existing parser: neither core nor tv-player depends on this module yet.
