# Offline timezone database (format 2)

This complete derivative database is distributed under ODbL 1.0; see NOTICE.txt
and ODbL-1.0.txt. It contains no app-user data. Its original upstream archive,
release and checksums are in manifest.json and the SQLite metadata table.

`polygons` stores one Polygon per row: `id`, IANA `zone`, longitude bounds
`west/east`, latitude bounds `south/north`, and a zlib-compressed `rings` BLOB.
All original boundary vertices and holes are retained, rounded to 1e-7 degrees
(less than 1 cm); polygons are not simplified.

After zlib decompression, all numbers use big-endian byte order:

1. A signed 32-bit ring count.
2. For each ring, a signed 32-bit vertex count.
3. For each vertex, signed 64-bit longitude and latitude deltas, in units of
   1e-7 degrees. Start each ring at `(0,0)` and add the deltas. The first ring is
   the outer boundary; following rings are holes. Rings include their closing
   vertex.

`cells` is a lookup index, not a geographic approximation: a 5-degree world grid
maps candidate polygon IDs. Cell number is `floor((lat+90)/5)*72 +
floor((lon+180)/5)`, clamped to row 0–35 and column 0–71. Bounds and full polygon
containment must still be tested. Ambiguous overlaps have no single zone.

Reproduce the database with `scripts/prepare_timezone_catalog.py` from the
original timezone-boundary-builder archive. Ordinary SQLite and zlib readers
can independently inspect, decode and manipulate this database.
