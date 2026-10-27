#!/usr/bin/env python3
"""Prepare the same Natural Earth vector artwork used by iOS place/continent cards."""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/atlas"


def projected(lon, lat):
    lat = math.radians(max(-85.051129, min(85.051129, lat)))
    return ((lon + 180) / 360, (1 - math.log(math.tan(math.pi / 4 + lat / 2)) / math.pi) / 2)


def bounds(points):
    xs, ys = zip(*points)
    return min(xs), min(ys), max(xs), max(ys)


def simplify(points, tolerance):
    keep = {0, len(points) - 1}
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        a, b = points[first], points[last]
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = dx * dx + dy * dy
        maximum, split = tolerance * tolerance, None
        for index in range(first + 1, last):
            p = points[index]
            t = max(0, min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length)) if length else 0
            distance = (p[0] - a[0] - t * dx) ** 2 + (p[1] - a[1] - t * dy) ** 2
            if distance > maximum:
                maximum, split = distance, index
        if split is not None:
            keep.add(split)
            stack.extend([(first, split), (split, last)])
    result = [points[index] for index in sorted(keep)]
    return result if len(result) >= 4 else points


def svg(paths, extent):
    x0, y0, x1, y1 = extent
    width, height = max(x1 - x0, 0.00001), max(y1 - y0, 0.00001)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x0:.7f} {y0:.7f} {width:.7f} {height:.7f}">'
            + "".join(paths) + '</svg>\n')


def main():
    output = ROOT / "art"
    output.mkdir(exist_ok=True)
    outlines = {}
    continents = {}
    for feature in json.loads((ROOT / "countries.geojson").read_text())["features"]:
        country_id = feature["properties"]["id"]
        continent = feature["properties"]["continent"]
        if continent in ("North America", "South America"): continent = "Americas"
        if continent == "Seven seas (open ocean)":
            continent = {"ATF": "Africa", "SYC": "Africa", "SHN": "Africa", "MUS": "Africa", "IOT": "Africa", "MDV": "Asia", "HMD": "Oceania", "SGS": "Americas", "CLP": "Americas"}.get(country_id)
        continents[country_id] = continent
        geometry = feature["geometry"]
        polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
        projected_polygons = [[[projected(*point[:2]) for point in ring] for ring in polygon] for polygon in polygons]
        largest = max((bounds(polygon[0]) for polygon in projected_polygons), key=lambda b: (b[2] - b[0]) * (b[3] - b[1]))
        tolerance = max(1 / 268435456, min(32000 / 268435456, max(largest[2] - largest[0], largest[3] - largest[1]) / 1000))
        parts = []
        for polygon in projected_polygons:
            for ring in polygon:
                points = simplify(ring, tolerance)
                parts.append("M" + "L".join(f"{x:.7f},{y:.7f}" for x, y in points) + "Z")
        path = f'<path id="{country_id}" fill="#DADEE3" fill-rule="evenodd" d="{"".join(parts)}"/>'
        outlines[country_id] = path
        padding = max(largest[2] - largest[0], largest[3] - largest[1]) * 0.06
        extent = (largest[0] - padding, largest[1] - padding, largest[2] + padding, largest[3] + padding)
        (output / f"{country_id}.svg").write_text(svg([path.replace("#DADEE3", "#2D2C2F")], extent))
    extents = {
        "Europe": (-25, 60, 34, 74), "Asia": (25, 180, -12, 77),
        "Americas": (-170, -32, -57, 77), "Africa": (-26, 64, -37, 38),
        "Oceania": (109, 180, -49, 8), "Antarctica": (-180, 180, -85, -60)
    }
    for name, (west, east, south, north) in extents.items():
        x0, y0 = projected(west, north)
        x1, y1 = projected(east, south)
        (output / f"{name}.svg").write_text(svg([path for key, path in outlines.items() if continents[key] == name], (x0, y0, x1, y1)))
    print(f"Prepared {len(outlines)} country and {len(extents)} continent vector artworks from the original geometry.")


if __name__ == "__main__":
    main()
