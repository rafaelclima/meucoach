#!/usr/bin/env python3
"""Sanitize TCX files before committing them as test fixtures.

The sanitizer removes GPS positions, the device creator block and free-form
notes. Timestamps and training metrics stay because they are needed by the
parser regression tests and do not identify a place or a person. The output is
deterministic and keeps the original TCX structure.

Usage:
    python3 tools/sanitize_tcx.py ~/Documents/Rafa/*.tcx --out fixtures/tcx
"""

from __future__ import annotations

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path

TCX_NS = "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
ACTIVITY_EXTENSION_NS = "http://www.garmin.com/xmlschemas/ActivityExtension/v2"
XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"

ET.register_namespace("", TCX_NS)
ET.register_namespace("x", ACTIVITY_EXTENSION_NS)
ET.register_namespace("xsi", XSI_NS)


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def remove_children(parent: ET.Element, names: set[str]) -> int:
    removed = 0
    for child in list(parent):
        if local_name(child.tag) in names:
            parent.remove(child)
            removed += 1
        else:
            removed += remove_children(child, names)
    return removed


def sanitize(source: Path, destination: Path) -> None:
    tree = ET.parse(source)
    root = tree.getroot()
    removed = remove_children(root, {"Position", "Creator", "Notes", "Author"})
    if removed == 0:
        raise SystemExit(f"{source}: expected at least one sensitive element")

    destination.parent.mkdir(parents=True, exist_ok=True)
    tree.write(destination, encoding="utf-8", xml_declaration=True)
    ET.parse(destination)
    print(f"{source.name} -> {destination} ({removed} sensitive element(s) removed)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sources", nargs="+", type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    for source in args.sources:
        sanitize(source, args.out / source.name)


if __name__ == "__main__":
    main()
