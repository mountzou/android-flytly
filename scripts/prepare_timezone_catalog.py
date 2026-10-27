#!/usr/bin/env python3
"""Build an offline spatial index retaining every timezone-boundary-builder vertex.

Usage: python3 scripts/prepare_timezone_catalog.py /path/timezones-with-oceans.geojson.zip --release 2026e
The distributed SQLite file is the machine-readable derivative database (ODbL).
"""
import argparse, hashlib, json, math, sqlite3, struct, zipfile, zlib, tempfile, os
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('archive', type=Path)
p.add_argument('--release', required=True)
args = p.parse_args()
root = Path(__file__).resolve().parents[1] / 'app/src/main/assets/timezones'
root.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(args.archive) as archive:
    name = next(n for n in archive.namelist() if n.endswith('.json'))
    with archive.open(name) as stream:
        source = json.load(stream)
descriptor, temporary_name = tempfile.mkstemp(prefix='flytly-timezones-', suffix='.sqlite', dir='/private/tmp')
os.close(descriptor)
temporary = Path(temporary_name)
db = sqlite3.connect(temporary)
db.executescript('''PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF;
CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);
CREATE TABLE polygons(id INTEGER PRIMARY KEY,zone TEXT NOT NULL,west REAL,east REAL,south REAL,north REAL,rings BLOB NOT NULL);
CREATE TABLE cells(cell INTEGER NOT NULL,polygon INTEGER NOT NULL,PRIMARY KEY(cell,polygon)) WITHOUT ROWID;
''')
regions = points = 0
for feature in source['features']:
    shape = feature['geometry']
    polygons = [shape['coordinates']] if shape['type'] == 'Polygon' else shape['coordinates']
    for polygon in polygons:
        regions += 1
        polygon = [[(round(v[0] * 10_000_000), round(v[1] * 10_000_000)) for v in ring] for ring in polygon]
        vertices = [(lon / 10_000_000, lat / 10_000_000) for ring in polygon for lon, lat in ring]
        west, east = min(v[0] for v in vertices), max(v[0] for v in vertices)
        south, north = min(v[1] for v in vertices), max(v[1] for v in vertices)
        data = bytearray(struct.pack('>i', len(polygon)))
        for ring in polygon:
            data += struct.pack('>i', len(ring))
            points += len(ring)
            previous_lon = previous_lat = 0
            for lon, lat in ring:
                data += struct.pack('>qq', lon - previous_lon, lat - previous_lat)
                previous_lon, previous_lat = lon, lat
        db.execute('INSERT INTO polygons VALUES(?,?,?,?,?,?,?)', (regions, feature['properties']['tzid'], west, east, south, north, zlib.compress(data, 9)))
        x0, x1 = max(0, math.floor((west + 180) / 5)), min(71, math.floor((east + 180) / 5))
        y0, y1 = max(0, math.floor((south + 90) / 5)), min(35, math.floor((north + 90) / 5))
        db.executemany('INSERT INTO cells VALUES(?,?)', ((y * 72 + x, regions) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)))
    print(feature['properties']['tzid'], regions, flush=True)
metadata = {'format': '2', 'release': args.release, 'source': f'https://github.com/evansiroky/timezone-boundary-builder/releases/tag/{args.release}',
            'archiveSHA256': hashlib.sha256(args.archive.read_bytes()).hexdigest(), 'license': 'ODbL-1.0',
            'polygons': str(regions), 'points': str(points), 'zones': str(len(source['features'])), 'geometry': 'all original vertices retained, rounded to 1e-7 degrees (under 1 cm), delta-int64 zlib rings; no polygon simplification'}
db.executemany('INSERT INTO metadata VALUES(?,?)', metadata.items())
db.commit()
assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
db.execute('VACUUM'); db.close()
output = root / 'boundaries.sqlite'
temporary.replace(output)
output.chmod(0o644)
metadata.update(databaseSHA256=hashlib.sha256(output.read_bytes()).hexdigest(), databaseBytes=output.stat().st_size)
(root / 'manifest.json').write_text(json.dumps(metadata, indent=2) + '\n')
(root / 'catalog.sha256').write_text(''.join(hashlib.sha256((root / name).read_bytes()).hexdigest() + '  ' + name + '\n' for name in ['boundaries.sqlite', 'manifest.json']))
print(json.dumps(metadata), flush=True)
