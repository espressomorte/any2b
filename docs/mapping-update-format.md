# AniTube mapping update format

This describes the wire format for future app-side catalog updates. It does not enable automatic updates; clients must not apply daily updates until a publisher and a trusted delivery endpoint exist.

## Snapshot

Each immutable snapshot is a UTF-8 JSON document:

```json
{
  "schemaVersion": 1,
  "snapshotId": "anitube-2026-11-04T00:00:00Z",
  "generatedAt": "2026-11-04T00:00:00Z",
  "sourceCatalogRevision": "<catalog revision>",
  "items": []
}
```

`items` uses the existing mapping entry schema. `snapshotId` is unique and immutable. A client may replace its local mapping only after parsing and validating the whole document and confirming that every item has a valid positive AniTube ID and the mapping's existing required fields.

## Delta

A delta is an immutable change from exactly one snapshot:

```json
{
  "schemaVersion": 1,
  "baseSnapshotId": "anitube-2026-11-04T00:00:00Z",
  "targetSnapshotId": "anitube-2026-11-05T00:00:00Z",
  "generatedAt": "2026-11-05T00:00:00Z",
  "upserts": [],
  "deletedAniTubeIds": []
}
```

Upserts replace the complete entry for the same AniTube ID. Deletions remove that ID. A delta is applicable only when the client's current snapshot ID equals `baseSnapshotId`; otherwise the client must fetch a full snapshot. Apply changes to a temporary copy, validate the result, then atomically swap it into place and record `targetSnapshotId`.

## Compatibility and trust

- A client must reject unsupported `schemaVersion` values and malformed entries without changing its current data.
- Manual user confirmations take precedence over downloaded suggestions and must be stored separately from generated mapping data.
- Snapshots and deltas must be served over HTTPS. Before automatic updates are enabled, publish a checksum or signature alongside each immutable document and validate it before applying.
- Keep old snapshots available long enough for clients to recover from interrupted or incompatible delta chains.
