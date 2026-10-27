#!/usr/bin/env python3
"""Import the reviewed iOS atlas without changing its geometry or identifiers."""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


def encode(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n")


def point(coordinates, properties):
    return {"type": "Feature", "geometry": {"type": "Point", "coordinates": coordinates},
            "properties": properties}


def collection(features):
    return {"type": "FeatureCollection", "features": features}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path("/Users/mountzou/ios-flytly"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    source = args.source / "ios-where-travel/Resources"
    target = root / "app/src/main/assets/atlas"
    target.mkdir(parents=True, exist_ok=True)
    names = ["countries.geojson", "cities.json", "country-labels.json", "country-flags.json"]
    data = {name: json.loads((source / name).read_text()) for name in names}
    countries, cities = data["countries.geojson"]["features"], data["cities.json"]
    country_ids = {feature["properties"]["id"] for feature in countries}
    city_ids = {city["id"] for city in cities}
    assert len(country_ids) == len(countries) == 258
    assert len(city_ids) == len(cities) == 26288
    assert all(city["countryID"] in country_ids for city in cities)
    assert all(city.get("urbanCenterID", city["id"]) in city_ids for city in cities)
    centres = {city["countryID"] for city in cities
               if city["catalogKind"] == "urbanCenter" and city["population"] > 50000}
    selectable = [city for city in cities if
                  (city["catalogKind"] == "urbanCenter" and city["population"] > 50000) or
                  (city["catalogKind"] == "capitalFallback" and city["countryID"] not in centres)]
    coverage = json.loads((args.source / "scripts/atlas-coverage.json").read_text())
    assert len(selectable) == coverage["selectableRecords"] == 11471
    assert all(sum(c["countryID"] == key for c in selectable) == value["cityCount"]
               for key, value in coverage["countries"].items())
    for name in names:
        shutil.copyfile(source / name, target / name)
    shutil.copyfile(args.source / "scripts/atlas-coverage.json", target / "atlas-coverage.json")
    shutil.copyfile(args.source / "DATA_SOURCES.md", target / "DATA_SOURCES.md")
    shutil.copytree(source / "Licenses", target / "licenses", dirs_exist_ok=True)
    shutil.copyfile(source / "Fonts/BricolageGrotesque-OFL.txt", target / "licenses/BricolageGrotesque-OFL.txt")
    fonts = root / "app/src/main/res/font"
    fonts.mkdir(parents=True, exist_ok=True)
    # The source variable font defaults to weight 800. Pin real text weights,
    # rather than letting every Android label inherit its ExtraBold default.
    for weight, filename in [(400, "bricolage_grotesque.ttf"), (500, "bricolage_grotesque_medium.ttf"),
                             (600, "bricolage_grotesque_semibold.ttf"), (700, "bricolage_grotesque_bold.ttf")]:
        font = TTFont(source / "Fonts/BricolageGrotesque.ttf", recalcTimestamp=False)
        instance = instantiateVariableFont(font, {"wght": weight, "wdth": 100, "opsz": 24})
        instance.save(fonts / filename)
    # Keep original, licensed SVG artwork ready for native country screens.
    shutil.copytree(args.source / "Branding/CountryFlags/SVG", target / "flags", dirs_exist_ok=True)
    preference_art = target / "preferences"
    preference_art.mkdir(exist_ok=True)
    for name in ["TravelPaceWalking", "TravelNatureCloud", "TravelCultureStatue", "TravelFoodCarrot",
                 "TravelCrowdsMegaphone", "TravelMobilityTrain", "TravelWeatherUmbrella", "TravelBudgetPig"]:
        shutil.copyfile(args.source / "ios-where-travel/Assets.xcassets" / f"{name}.imageset" / f"{name}.svg",
                        preference_art / f"{name}.svg")
    # These two rendering indexes are derived; the four source files stay byte-identical.
    # iOS uses 256-point tiles; MapLibre uses 512-point tiles, so equivalent zoom is one lower.
    encode(target / "map-country-labels.geojson", collection([
        point([label["longitude"], label["latitude"]], {
            **label, "minimumZoom": max(0, label["minimumZoom"] - 1),
            "maximumZoom": label["maximumZoom"] - 1})
        for label in data["country-labels.json"]]))
    encode(target / "map-cities.geojson", collection([
        point([city["longitude"], city["latitude"]], {
            "id": city["id"], "countryID": city["countryID"], "name": city["name"],
            "population": city["population"], "minimumZoom": 4 if city["isCapital"] else
            5 if city["population"] >= 1000000 else 6 if city["population"] >= 250000 else 7})
        for city in selectable]))
    hashes = {name: hashlib.sha256((target / name).read_bytes()).hexdigest() for name in names}
    assert hashes["cities.json"] == coverage["citiesSHA256"]
    encode(target / "manifest.json", {
        "version": 1, "countries": len(countries), "storedCities": len(cities),
        "selectableCities": len(selectable), "sourceSHA256": hashes,
        "generatedSHA256": {name: hashlib.sha256((target / name).read_bytes()).hexdigest()
                            for name in ["map-cities.geojson", "map-country-labels.geojson"]}})
    subprocess.run([sys.executable, str(root / "scripts/compile_atlas_catalog.py"), "--directory", str(target)], check=True)
    print(f"Imported {len(countries)} map units, {len(cities)} stored cities, "
          f"{len(selectable)} selectable cities. Source hashes verified.")


if __name__ == "__main__":
    main()
