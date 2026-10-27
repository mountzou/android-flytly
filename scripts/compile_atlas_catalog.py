#!/usr/bin/env python3
"""Build the read-only, lossless SQLite atlas; original iOS source files remain unchanged."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import tempfile
import unicodedata

SOURCE_FILES = ['countries.geojson', 'cities.json', 'country-labels.json', 'country-flags.json']
FORMAT_VERSION = 3

def search_key(name, aliases):
    decomposed = unicodedata.normalize('NFD', ' '.join([name] + aliases))
    lowered = ''.join(c for c in decomposed if not unicodedata.category(c).startswith('M')).lower()
    def whitespace(c): return unicodedata.category(c) in ('Zs', 'Zl', 'Zp') or ord(c) in (*range(9, 14), *range(28, 32))
    start, end = 0, len(lowered)
    while start < end and whitespace(lowered[start]): start += 1
    while end > start and whitespace(lowered[end - 1]): end -= 1
    return lowered[start:end]

def encode(value): return json.dumps(value, ensure_ascii=False, separators=(',', ':'))

def build(path, directory, hashes):
    countries = json.loads((directory / 'countries.geojson').read_text())['features']
    cities = json.loads((directory / 'cities.json').read_text())
    labels = json.loads((directory / 'country-labels.json').read_text())
    centres = {r['countryID'] for r in cities if r['catalogKind'] == 'urbanCenter' and r['population'] > 50000}
    selectable = [r for r in cities if (r['catalogKind'] == 'urbanCenter' and r['population'] > 50000) or
                  (r['catalogKind'] == 'capitalFallback' and r['countryID'] not in centres)]
    selectable.sort(key=lambda r: (-r['population'], r['name'].encode('utf-16-be'), r['id'].encode('utf-16-be')))
    order = {r['id']: i for i, r in enumerate(selectable)}
    label_order = {r['id']: i for i, r in enumerate(sorted(selectable, key=lambda r: (-int(r['isCapital']), -r['population'])))}
    db = sqlite3.connect(path)
    db.executescript('''
      PRAGMA page_size=4096; PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF; PRAGMA user_version=3;
      CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
      CREATE TABLE countries (id TEXT PRIMARY KEY, name TEXT NOT NULL, continent TEXT NOT NULL, aliases TEXT NOT NULL, alias_count INTEGER NOT NULL, search_text TEXT NOT NULL);
      CREATE TABLE labels (ordinal INTEGER PRIMARY KEY, country_id TEXT UNIQUE NOT NULL, latitude REAL, longitude REAL, minimum_zoom REAL, maximum_zoom REAL, name TEXT, rank INTEGER);
      CREATE TABLE cities (ordinal INTEGER PRIMARY KEY, id TEXT UNIQUE NOT NULL, name TEXT NOT NULL, country_id TEXT NOT NULL,
        latitude REAL, longitude REAL, population REAL, capital INTEGER, kind INTEGER, aliases TEXT NOT NULL, alias_count INTEGER,
        successor TEXT, year INTEGER, search_text TEXT NOT NULL, name_key TEXT NOT NULL, selectable_order INTEGER, label_order INTEGER, minimum_zoom REAL);
    ''')
    for feature in countries:
        r = feature['properties']; aliases = r.get('aliases', [])
        db.execute('INSERT INTO countries VALUES (?,?,?,?,?,?)', (r['id'], r['name'], r['continent'], encode(aliases), len(aliases), search_key(r['name'], aliases)))
    for i, r in enumerate(cities):
        aliases = r.get('aliases', [])
        minimum = 5 if r['isCapital'] else 6 if r['population'] >= 1000000 else 7 if r['population'] >= 250000 else 8
        db.execute('INSERT INTO cities VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
          (i, r['id'], r['name'], r['countryID'], r['latitude'], r['longitude'], r['population'], int(r['isCapital']),
           {'urbanCenter': 0, 'capitalFallback': 1, 'legacy': 2}[r['catalogKind']], encode(aliases), len(aliases), r.get('urbanCenterID'),
           r.get('populationYear'), search_key(r['name'], aliases), search_key(r['name'], []), order.get(r['id']), label_order.get(r['id']), minimum))
    for i, r in enumerate(labels):
        db.execute('INSERT INTO labels VALUES (?,?,?,?,?,?,?,?)', (i, r['countryID'], r['latitude'], r['longitude'], r['minimumZoom'], r.get('maximumZoom', 7), r.get('name', ''), r.get('rank', 3)))
    counts = {'countries': len(countries), 'cities': len(cities), 'labels': len(labels), 'selectable': len(selectable)}
    for key, value in {**counts, 'format': FORMAT_VERSION, 'sourceSHA256': encode(hashes)}.items():
        db.execute('INSERT INTO metadata VALUES (?,?)', (key, str(value)))
    db.executescript('''CREATE INDEX cities_country ON cities(country_id, selectable_order);
      CREATE INDEX cities_selectable ON cities(selectable_order); CREATE INDEX cities_successor ON cities(successor);
      CREATE INDEX cities_latitude ON cities(latitude, longitude); ANALYZE;''')
    db.commit(); db.execute('VACUUM'); db.close()
    return counts

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=Path(__file__).resolve().parents[1] / 'app/src/main/assets/atlas')
    parser.add_argument('--check', action='store_true'); args = parser.parse_args()
    manifest_path = args.directory / 'manifest.json'; manifest = json.loads(manifest_path.read_text())
    hashes = {n: hashlib.sha256((args.directory / n).read_bytes()).hexdigest() for n in SOURCE_FILES}
    assert hashes == manifest['sourceSHA256'], 'Original source assets changed'
    target = args.directory / 'catalog.sqlite'
    if args.check:
        assert hashlib.sha256(target.read_bytes()).hexdigest() == manifest['generatedSHA256']['catalog.sqlite']
        with sqlite3.connect(f'file:{target}?mode=ro', uri=True) as db:
            assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
            metadata = dict(db.execute('SELECT key,value FROM metadata'))
            assert int(metadata['format']) == FORMAT_VERSION and json.loads(metadata['sourceSHA256']) == hashes
        print(encode({'verified': True, 'format': FORMAT_VERSION})); return
    with tempfile.TemporaryDirectory() as work:
        generated = Path(work) / 'catalog.sqlite'; counts = build(generated, args.directory, hashes)
        assert counts['countries'] == manifest['countries'] and counts['cities'] == manifest['storedCities'] and counts['selectable'] == manifest['selectableCities']
        shutil.copyfile(generated, target)
    manifest['generatedSHA256'].pop('catalog.bin', None)
    manifest['generatedSHA256']['catalog.sqlite'] = hashlib.sha256(target.read_bytes()).hexdigest()
    manifest['catalogIndexFormat'] = FORMAT_VERSION; manifest['catalogSQLiteBytes'] = target.stat().st_size
    manifest_path.write_text(encode(manifest) + '\n')
    names = SOURCE_FILES + ['catalog.sqlite', 'manifest.json']
    (args.directory / 'catalog.sha256').write_text(''.join(f'{hashlib.sha256((args.directory / n).read_bytes()).hexdigest()}  {n}\n' for n in names))
    print(encode({**counts, 'bytes': target.stat().st_size, 'compiled': True}))

if __name__ == '__main__': main()
