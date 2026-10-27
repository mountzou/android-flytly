# Atlas data provenance

## Current selection: GHSL urban centres

The app implements the reviewed rule across all **258** Natural Earth map units:

- **190** units use **11,422** GHSL urban centres, all strictly above 50,000 people.
- **48** units use a GeoNames capital/administrative-seat fallback: **49** entries because Indian Ocean Territories combines Christmas and Cocos Islands.
- **20** units have country-only visit selection.

There are **11,471 selectable places** and **26,288 stored catalog records**, including all 14,866 previous IDs for existing visits. `scripts/atlas-coverage.json` records each unit's implemented coverage and count.

### GHSL source

- European Commission, Joint Research Centre: GHS-UCDB R2024A **V1.2**, retrieved 2026-09-08.
- Reference year for the selected population and urban footprints: **2025**.
- Official catalog: https://data.jrc.ec.europa.eu/dataset/1a338be6-7eaf-480c-9664-3a8ade88cbcd
- Exact download URLs and SHA-256 hashes: `scripts/ghsl-sources.json`.
- General characteristics ZIP SHA-256: `bc879d82320504f89df2041b7936221c8239cd808abe93980493aed062b4f3d6`.
- Population/thematic ZIP SHA-256: `df8844961b663104e8e51ee656eafebed5336e5a946d92f6d7f31d4be75a9032`.
- License: CC BY 4.0, https://creativecommons.org/licenses/by/4.0/ . Source credits, adaptation notes, citations and license links are available in Settings → Data & Credits.

The general table's `GC_POP_TOT_2025` is retained as a decimal number and supplies descending population order. It was cross-checked against `GH_POP_TOT_2025` in the second thematic table; the maximum difference was 0.0001 person, below the precision relevant to the threshold. Equal populations use name and ID. Saved status never changes ordering. Search filters this fixed catalog and cannot cause a capital fallback.

Urban centres use namespaced IDs `ghsl-r2024a-<ID_UC_G0>`. Future GHSL releases must explicitly review ID continuity before changing this namespace or replacing the crosswalk. The app carries only processed JSON; GIS data and libraries are build-time inputs.

## Country joins and boundary policy

The audited mapping is in `artifacts/ghsl-audit/centres.json` and `countries.json`. Both inputs are hash-pinned in `scripts/ghsl-sources.json`; a changed mapping cannot be imported silently.

- Exact normalized Natural Earth country names/aliases join source labels. Four French overseas labels contribute 8 centres to the map's existing France unit.
- Same-country spatial rules split 7 Somaliland centres from the Somalia source label, 6 Hong Kong centres and 1 Macao centre from China. Northern Cyprus, Kosovo and Taiwan have corresponding source labels.
- Every GHSL record is assigned exactly once. Population is the whole urban-centre estimate, not a population allocation to intersecting territories. In particular, the centre named `Zhuhai [Macau]` is assigned to China; its Macao portion is not added as a separate city or population.
- Remaining boundary disagreements retain the reviewed source-country assignment. Country polygons are unchanged. The 202 conservative geographic review flags, including 28 dominant-overlap disagreements, remain documented in `artifacts/ghsl-audit/centres-review.csv`. They are not all confirmed errors. This includes source disagreements around Crimea and Western Sahara.
- Dot coordinates are the GHSL centroid geometry transformed from World Mollweide (`ESRI:54009`) to WGS84. The misleading GPKG LON/LAT attribute names are not treated as degrees. Coastal/border centroids may lie outside the generalized country outline; the dot still opens its assigned country. No invented capital, displaced coordinate or new border policy is introduced.

The full original investigation is in `artifacts/ghsl-audit/REPORT.md`. Those artifacts describe the pre-migration audit and are retained unchanged; the current build results are in `scripts/atlas-coverage.json`.

GHSL uses an internationally consistent urban-density/continuity definition, not every administratively separate city. Greece has 8 centres and Spain 85. Acharnes, Kallithea, Peristeri and Piraeus fall within Athens. Kalamata, Kavala, Serres, Trikala and Chalkida are not separate centres in this release. These are source-catalog counts and definitions, not independent census conclusions.

## GeoNames: fallback, search aliases and previous visits

- Source: https://download.geonames.org/export/dump/cities15000.zip
- Schema: https://download.geonames.org/export/dump/readme.txt
- Feature codes: https://www.geonames.org/export/codes.html
- Snapshot retrieved 2026-09-08, 34,135 source rows.
- Source SHA-256: `a969dcf03a46632c8787050674459d1eabf0132e6f7b1cd2520d0a37436c367e`.
- Attribution: GeoNames, https://www.geonames.org/ . License: CC BY 4.0.

The 48 fallback units use the audited source-designated capitals (`PPLC`) regardless of population, including zero/unknown values. Saint John's in Antigua and Barbuda remains a fallback even though its GeoNames population is above 50,000: fallback is determined by absence of a GHSL centre, not by that separate GeoNames population. This is the pinned source designation, not a fresh verification of each current administrative seat.

GeoNames values are not guaranteed to use one census year or one uniform city definition; `sourceModified` is a record modification date, not a census date. It no longer controls new city eligibility in the 190 GHSL-covered units.

All **14,866** previous GeoNames/Natural Earth records keep their IDs, coordinates and populations for compatibility. New selections expose only GHSL centres and the reviewed fallbacks. For **9,231** old IDs, their original point lies within exactly one GHSL urban polygon assigned to the same map unit; `urbanCenterID` links them to that centre. No ambiguous matches were accepted. No fuzzy-name, nearest-city or cross-country merge is used. Aliases from these contained settlements enrich the centre's search names, including Greek names; searching a suburb selects its containing centre.

`VisitStore` resolves these links in memory without rewriting the version-1 saved document. Multiple old suburb IDs and a current centre ID display as one visited centre. On an explicit edit, the centre and its predecessor IDs are changed atomically. Removing a centre removes all linked stored IDs, preventing a removed visit from reappearing after relaunch. Unknown IDs and unrelated visits are preserved.

Unmatched saved places remain individual visits and can be removed through the Visited filter or the country's Previously saved cities section. They are not offered for new visits. The 48 fallback units retain their existing capital IDs.

The earlier compatibility catalog is regenerated in temporary staging by `prepare_atlas.py`: it retains all 7,341 Natural Earth IDs, updates exact `GEONAMESID` references and includes the previously offered GeoNames records. Its hash must match the original audited catalog before GHSL conversion can run.

## Country outlines: Natural Earth

- Repository: https://github.com/nvkelso/natural-earth-vector
- Pinned commit: `ca96624a56bd078437bca8184e78163e5039ad19`, retrieved 2026-09-07.
- License: public domain, https://www.naturalearthdata.com/about/terms-of-use/
- Sources: `ne_10m_admin_0_countries.geojson` and `ne_10m_populated_places.geojson`.
- Exact URLs and hashes: `scripts/atlas-sources.json`.

Country geometries, islands and interior rings remain unchanged. Map IDs use ADM0_A3; metadata retains English/Greek names. There are 258 map units including territories, not 258 sovereign countries. The 1:10 million boundaries remain generalized at close zoom. The same polygons power rendering and country hit testing.

## Country flag artwork

The app bundles grayscale flag artwork from [flag-icons v7.6.0](https://github.com/lipis/flag-icons/tree/v7.6.0), copyright © 2013 Panayiotis Lipiridis, under the MIT License. SVG sources live in `Branding/CountryFlags/SVG/`; the app uses vector PDF assets generated from them. The complete notice is bundled at `ios-where-travel/Resources/Licenses/flag-icons.txt`. Country codes are joined using the same pinned Unicode CLDR snapshot as the currency import; its notice is bundled at `Resources/Licenses/Unicode-CLDR.txt`.

`scripts/country-flags-sources.json` pins download URLs and SHA-256 checksums. `scripts/prepare_country_flags.py` joins the actual bundled atlas IDs, including its five reviewed ID aliases, to the source's 4:3 flags. The generated `Resources/country-flags.json` covers **239 of the 258 map units**, using 239 flags. The other **19 units** are explicitly listed in the source manifest and use a neutral globe in `CountryFlagView`. They include disputed/shared areas, bases, and other units without a direct CLDR join. This is a source-coverage fallback, not a claim that these places have no flag. No parent-country assignment is inferred.

Flags share three base tones: **ink `#262626`, gray `#8c8c8c`, and white `#ffffff`**, configured in the source manifest. Source colors are grouped in RGB space with weights measured from their visible areas, ordered by Rec. 709 luminance, then mapped to this palette. This gives primary fields priority over small emblem details. Simple two/three-color flags keep their distinct colors as separate tones. Gradients become flat fills and fractional opacities become opaque; smooth antialiased edges can still contain intermediate pixels. Original SVG geometry is retained. CairoSVG 2.8.2 with Cairo 1.18.4 converts it to native vector PDFs with a 64×48 pt size and preserved vectors in the asset catalog. This also supplies a sharp native image fallback for flags shown up to 64 pt wide. PDF metadata uses a fixed date so regeneration is byte-for-byte reproducible. This format preserves stars, emblems and clipping that the native SVG importer loses in several source flags. No SVG renderer is installed in the app. The images use original rendering and keep their tones in light and dark appearance. Country names must remain visible beside this decorative artwork: flags distinguished mainly by color can become similar with a limited palette.

Local storage is the selected approach: the SVG sources total **1,649,822 bytes (1.65 MB)** and the generated vector PDF files total **1,529,658 bytes (1.53 MB)**. Only the PDFs are compiled into the app's image assets. An Xcode 27.1 simulator asset-catalog compilation for iPhone and iPad increased `Assets.car` from **466,264** to **4,283,944 bytes**, an additional **3,817,680 bytes (3.82 MB)**. This measures the compiled catalog; final App Store download size depends on distribution compression and device slicing. Offline availability and immediate rendering justify this bundled cost.

Regenerate or validate from the repository root, with Cairo 1.18.4 installed:

```sh
python3 -m pip install --target build/country-flags/python -r scripts/country-flags-requirements.txt
python3 scripts/prepare_country_flags.py --fetch
python3 scripts/prepare_country_flags.py --check
```

Downloads, local Python tools and the coverage/size report are cached under ignored `build/country-flags/`. The preparation tools are not app dependencies. Only selected grayscale image assets, the atlas mapping and license notices ship in the app. The approved design for v1.1.13 uses `CountryFlagView` to the right of the country name and continent on every country screen, at 56×42 pt, with the shared three-tone palette in both light and dark appearances.

## Firestore import conventions

The following one-off enrichments target existing `countries/{countryID}` documents and their `cities` subcollections in `flytly-ddc3c`, database `(default)`, Firestore Native STANDARD edition. Country IDs are the app's map IDs; ISO/Wikidata join keys stay local. Missing source values are omitted, without inheritance from another country. The app still uses the bundled atlas rather than this Firestore catalog.

Importers use the existing Firebase CLI login, validate pinned seeds, reject conflicting values and apply field masks with document update-time preconditions. Country imports use atomic commits; city imports use resumable batches. Verification checks imported values, unrelated fields, skipped documents and document identities against each import's baseline. Later enrichments have separate receipts, so an earlier verifier may no longer match the current schema.

Import artifacts retain sources, hashes, coverage, plans, before/after snapshots and receipts. The dated import folders are local and explicitly Git-ignored; retain or restore them to reproduce seeds and run verification.

## Country currencies in Firestore

Imported **2026-09-29**: `currencies`, a sorted array of currency codes, in **238** country documents. Only tender currencies valid on that date are included; seven countries have multiple currencies. **20** units are omitted: Antarctica has no current CLDR currency and 19 special units have no code match.

Source: Unicode CLDR `common/supplemental/supplementalData.xml`, commit `86be3214f0ba76a23765845ccc7b3c2e67247665`, SHA-256 `745150ec1408d50e1a272b65654a5dd645f3e60f5cc2898b576f725b28538d1a`, Unicode-3.0 license. Source and notices: `artifacts/country-currencies-import-2026-09-29/`.

CLDR maps alpha-3 map IDs to alpha-2 codes. Local aliases are `ALD → ALA`, `PSX → PSE`, `SAH → ESH`, `SDS → SSD`, and `KOS → XKK/XK` (CLDR user-assigned codes, not official ISO assignments). Currency names and symbols can be localized from the stored codes.

Prepare: `python3 scripts/prepare_country_currencies.py --fetch`. Inspect or verify: `node scripts/import_country_currencies.cjs --dry-run` / `--verify`.

## Country calling codes in Firestore

Imported **2026-09-29**: `callingCodes`, an array of E.164 country codes including `+`, in **234** documents; Greece stores `["+30"]`. Codes can be shared across countries and exclude national area codes and international access prefixes.

Source: Google libphonenumber `resources/PhoneNumberMetadata.xml`, commit `d347e5d04397c99fe018636eac2f363a27af71c2`, SHA-256 `2fa0728c949aa59ee4d95bd68ff6398988b3ef4069d70965cc1ae080b7236e5c`, Apache-2.0 license. Source and notices: `artifacts/country-calling-codes-import-2026-09-29/`.

The join reuses CLDR and the currency aliases, reads geographic `countryCode` values and excludes global services (`001`). The source's known-omissions note explicitly supplies `+262` for French Southern Territories. **24** units are skipped: 19 lack a code match and five lack phone metadata.

Prepare: `python3 -B scripts/prepare_country_calling_codes.py --fetch` (requires the retained CLDR source). Inspect or verify: `node scripts/import_country_calling_codes.cjs --dry-run` / `--verify`.

## Country travel metadata from Wikidata in Firestore

Imported **2026-09-30**: **239** country documents, including **210** with all five fields; **19** units have no validated values.

| Field | Country documents | Stored format |
| --- | ---: | --- |
| `drivingSide` | 239 | `"left"` or `"right"` |
| `plugTypes` | 217 | Sorted array of English Wikidata plug/standard names |
| `voltages` | 217 | Sorted numeric array of nominal domestic voltages in V |
| `frequencies` | 213 | Sorted numeric array of discrete mains frequencies in Hz |
| `capitals` | 233 | Sorted array of English capital names |

Source: [Wikidata Action API](https://www.wikidata.org/wiki/Wikidata:Data_access), CC0-1.0. Properties: [P1622](https://www.wikidata.org/wiki/Property:P1622) (driving), [P2853](https://www.wikidata.org/wiki/Property:P2853) (plugs), [P2884](https://www.wikidata.org/wiki/Property:P2884) (voltage), [P36](https://www.wikidata.org/wiki/Property:P36) (capitals), and [P2144](https://www.wikidata.org/wiki/Property:P2144) qualifiers on voltage statements (frequency). The pinned Natural Earth `ADM0_A3 → WIKIDATAID` crosswalk matches all 258 map units. Snapshots and exceptions: `artifacts/country-wikidata-import-2026-09-30/REPORT.md`.

- Deprecated, expired, future and uncertain dated claims are excluded. Current preferred ranks take precedence; otherwise current normal values are retained. Date precision and Gregorian/Julian calendars are respected. Japan retains both frequencies and South Africa all three capitals. Pakistan's ambiguous undated Rawalpindi claim is excluded; Islamabad is retained.
- Plug names remain source labels, such as `Europlug` and `Schuko`, without guessing A–O letters for generic standards such as `BS 546`. Names use English or the API's explicit multilingual fallback.
- Voltages use nominal domestic values in **90–250 V**, excluding tolerances and explicitly three-phase supplies. France's 400 V and Belize's 550 V are excluded.
- Frequencies require explicit Hz units and discrete **50/60 Hz** values. Curaçao, Liberia and North Korea's `55 ± 5 Hz` ranges and Iceland's unitless value are omitted; these are source-quality gaps.

Prepare from the retained snapshot: `python3 -B scripts/prepare_country_wikidata.py`. Inspect or verify: `node scripts/import_country_wikidata.cjs --dry-run` / `--verify`. A source refresh requires a new reviewed snapshot.

## Country official languages and time zones in Firestore

Imported **2026-09-30**, from Wikidata (CC0-1.0): `officialLanguages` in **233** countries and `timeZones` in **242**, as sorted unique arrays of English names with explicit API multilingual fallback. Snapshot and decisions: `artifacts/country-language-timezones-import-2026-09-30/REPORT.md`.

Languages use [P37](https://www.wikidata.org/wiki/Property:P37), retaining simultaneous current normal and preferred values. Regional, de facto-only, unofficial, disputed, working, vehicular, auxiliary and minority-only claims are excluded, as are deprecated, ended, future, uncertain dated and unknown values. If every preferred current language explicitly lacks country-wide official status, less-specific normal entries are withheld. Unqualified claims remain source assertions rather than an independent legal audit. **25** units have no accepted value, including Belgium (regional claims), Japan (de facto-only) and the United States (deprecated/regional claims). Canada stores `["English", "French"]`; Kyrgyzstan retains both Kyrgyz and Russian.

Time zones use [P421](https://www.wikidata.org/wiki/Property:P421), retaining all current non-deprecated values, including regional/dependency and standard/daylight-saving scope. Exact names are deduplicated; offsets, conventional names and IANA-shaped identifiers may describe the same zone. This descriptive catalog cannot compute current offsets or DST and is not an IANA-only list. **16** units lack current direct values; zones are not inferred from capitals or cities.

Prepare: `python3 -B scripts/prepare_country_language_timezones.py`. The importer accepts `node scripts/import_country_language_timezones.cjs <languages|time-zones> <--dry-run|--apply|--verify>` and defaults to a read-only plan. Languages must be applied and verified before time zones; the second stage checks the language receipt and current values. After both stages, use `node scripts/import_country_language_timezones.cjs time-zones --verify`. The retained `combined-verification.json` also compares both fields and earlier metadata against the original baseline.

## Country land neighbors in Firestore

Imported **2026-09-30**: `neighboringCountries` in all **258** countries, as sorted unique arrays of existing app IDs with symmetric relationships. **176** units have neighbors and **82** store `[]`, reflecting the reviewed graph rather than missing data. Greece stores `["ALB", "BGR", "MKD", "TUR"]`.

Pinned Natural Earth polygons provide **342** positive-length shared boundaries, checked with Shapely 2.1.2. [Wikidata P47](https://www.wikidata.org/wiki/Property:P47) corroborates **321** pairs; **21** use more specific map territories. Two reviewed supplements add Botswana–Zambia at Kazungula and Canada–Greenland on Tartupaluk/Hans Island: **344** pairs and **688** references in total. River boundaries count; maritime boundaries, tunnels, bridges alone and point-only contacts do not. P47 is therefore not copied directly.

The graph follows the app's territorial units: France includes French Guiana, Saint Martin is separate, and Greenland is `GRL`. The Canada–Greenland supplement records the agreed partition, without asserting a treaty ratification date or operational crossing. Evidence and scope: `artifacts/country-neighbors-import-2026-09-30/REPORT.md`.

Prepare: `python3 -B scripts/prepare_country_neighbors.py`. Inspect or verify: `node scripts/import_country_neighbors.cjs --dry-run` / `--verify`.

## City population and country rank in Firestore

Imported **2026-09-30**: `population` and `countryRank` in **11,422** `countries/{countryID}/cities/{cityID}` documents across **190** countries, joined by existing IDs. The source is the bundled GHS-UCDB R2024A V1.2 catalog's **2025** estimates; its hash is listed under Reproduction. The **49** fallback capitals in **48** units receive neither field because the app does not display GHSL population/rank for them.

- `population`: Firestore integer, rounded to the nearest person; positive halves round up. These are urban-centre estimates, not necessarily municipality populations.
- `countryRank`: integer, **1 + the count of selectable cities in the same country with strictly greater unrounded source population**. Source ties share rank. Search, saved state and Settings filters do not affect it. Rounding creates 22 additional tie groups, without changing ranks.

Athens stores `population: 3166757`, `countryRank: 1`. Only these two fields are imported; release/year remain in local metadata.

The raw import and integer conversion each used **29** resumable batches of at most **400** writes and verified all **11,471** cities plus parent countries. The conversion updates only `population`, accepting exact predecessor doubles or already-correct integers and preserving original ranks. Separate backups and receipts are in `artifacts/city-population-ranks-import-2026-09-30/` and `artifacts/city-integer-population-2026-09-30/`.

Reproduce the raw seed: `node scripts/import_city_population_ranks.cjs --prepare`. Prepare, inspect or verify the current integer catalog: `node scripts/round_city_populations.cjs --prepare` / `--dry-run` / `--verify`. The original raw-double verifier is superseded.

## Reproduction

Install `shapely==2.1.2` and `pyproj==3.8.0` in a build environment. Download the pinned files from both source manifests, then run:

```sh
python3 scripts/prepare_atlas.py /path/to/downloaded/source-directory
```

The script stages the compatibility catalog and calls `prepare_ghsl.py` to verify hashes, perform the legacy spatial joins, validate every country count and emit resources. All 258 coverage entries and unique IDs must pass before replacing app resources. Source updates require explicit manifest/crosswalk review; the GeoNames download URL itself is mutable.

- Bundled `countries.geojson` SHA-256: `f4733fe835f4037e2e34c122013c73d04ec817b35ed50da2f05ae1f23f8ab335`.
- Bundled `cities.json` SHA-256: `692c178506a3f85addf84289c40001319732f827eabb39b20475f6c734ef09dd`.

Selection and local saving work offline, with no account, backend, location permission or runtime geocoding. Build scripts never open personal visit storage. GHSL-derived tables, aliases and country joins are transformations by this project, not an official JRC application.

## Cartographic label points

`Resources/country-labels.json` contains 258 label entries derived from the same pinned Natural Earth 10m source. The country and city resources above remain unchanged. `scripts/prepare_country_labels.py` verifies the source hash and generates this companion file; the full `prepare_atlas.py` pipeline invokes it too.

Displayed country names use Natural Earth's concise `NAME`. Preferred coordinates use `LABEL_X` / `LABEL_Y`. Eight source points fall outside their corresponding outline; for GNQ, NZL, TTO, VCT, ATG, STP, SGS and BJN, the generator uses an interior representative point on the largest polygon. All 258 anchors are checked against the actual bundled map outlines in the Swift tests.

Minimum display zoom is `max(3, MIN_LABEL + 0.8)` and maximum is `max(7, MAX_LABEL)`, using a 256-point Web Mercator zoom convention. `LABELRANK` supplies a stable country priority when names overlap. Visited city names appear from zoom 5.0 by default; Settings offers Minimal (6.0), Balanced (5.0), or Detailed (4.0). Country and visited-city names can each be hidden. Runtime collision handling may hide a name while retaining the visited dot. These display preferences do not alter geography or saved visits.

Settings also applies a strict population threshold of 50k (default), 100k, 250k,
500k or 1M to the audited urban-centre selection catalog. The existing capital
fallback entries remain selectable at every threshold; raising the limit does
not create new capital fallbacks. The source catalog and coverage classifications
remain unchanged. Filtered-out saved cities remain accessible in Visited and
Previously saved cities and continue to contribute to the map and totals.

Label resource SHA-256: `3c90a839e3dd725e29a4dfa3dc54ee2e4816de2af6b2113813d329e6dce47fbe`.
