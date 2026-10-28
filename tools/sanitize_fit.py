#!/usr/bin/env python3
"""Remove identifying data from FIT activity files before committing them.

The sanitizer works at the wire level so the FIT message structure, scales and
developer fields stay intact. It replaces every position with the FIT invalid
value, clears device serial/application identifiers, and replaces developer
string fields (for example Zepp's ``source`` URL) with a fixed placeholder.

Usage:
    python3 tools/sanitize_fit.py ~/Documents/Rafa/Zepp/*.fit --out fixtures/fit

The output is deterministic and keeps timestamps. Run it only on files that
have already been approved for de-identification. Never commit the originals.
"""

from __future__ import annotations

import argparse
import struct
from dataclasses import dataclass
from pathlib import Path

POSITION_FIELDS = {
    20: {0, 1},  # record.position_lat / position_long
    19: {3, 4, 5, 6},  # lap.start/end position
    18: {3, 4},  # session.start position
}
SERIAL_MESSAGES = {0, 23}  # file_id, device_info
BASE_SIZE = {
    0x00: 1,  # enum
    0x01: 1,  # sint8
    0x02: 1,  # uint8
    0x83: 2,  # sint16
    0x84: 2,  # uint16
    0x85: 4,  # sint32
    0x86: 4,  # uint32
    0x07: 1,  # string (size is explicit)
    0x88: 4,  # float32
    0x89: 8,  # float64
    0x0A: 1,  # uint8z
    0x8B: 2,  # uint16z
    0x8C: 4,  # uint32z
    0x0D: 1,  # byte
    0x8E: 8,  # sint64
    0x8F: 8,  # uint64
    0x90: 8,  # uint64z
}


def crc16(data: bytes) -> int:
    table = [
        0x0000, 0xCC01, 0xD801, 0x1400,
        0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401,
        0x5000, 0x9C01, 0x8801, 0x4400,
    ]
    crc = 0
    for byte in data:
        tmp = table[crc & 0xF]
        crc = (crc >> 4) & 0x0FFF
        crc ^= tmp ^ table[byte & 0xF]
        tmp = table[crc & 0xF]
        crc = (crc >> 4) & 0x0FFF
        crc ^= tmp ^ table[(byte >> 4) & 0xF]
    return crc


@dataclass
class FieldDef:
    number: int
    size: int
    base_type: int
    dev_data_index: int | None = None
    offset: int = 0


@dataclass
class Definition:
    global_message: int
    endian: str
    fields: list[FieldDef]
    developer_fields: list[FieldDef]


def decode_value(base_type: int, raw: bytes, endian: str):
    kind = base_type & 0x1F
    if kind == 0x07:  # string
        return raw.split(b"\0", 1)[0].decode("utf-8", errors="replace")
    if kind == 0x0D:  # byte array
        return raw
    if not raw:
        return None
    prefix = "<" if endian == "little" else ">"
    if kind in (0x00, 0x02, 0x0A):
        return raw[0]
    if kind == 0x01:
        return struct.unpack(prefix + "b", raw[:1])[0]
    if kind in (0x03, 0x04, 0x0B):
        fmt = "h" if kind == 0x03 else "H"
        return struct.unpack(prefix + fmt, raw[:2])[0]
    if kind in (0x05, 0x06, 0x0C):
        fmt = "i" if kind == 0x05 else "I"
        return struct.unpack(prefix + fmt, raw[:4])[0]
    if kind == 0x08:
        return struct.unpack(prefix + "f", raw[:4])[0]
    if kind == 0x09:
        return struct.unpack(prefix + "d", raw[:8])[0]
    if kind == 0x0E:
        return struct.unpack(prefix + "q", raw[:8])[0]
    if kind in (0x0F, 0x10):
        return struct.unpack(prefix + "Q", raw[:8])[0]
    return raw


def read_definition(data: bytes, pos: int, header: int, endian: str) -> tuple[Definition, int]:
    if pos + 5 > len(data):
        raise ValueError("truncated definition header")
    prefix = "<" if endian == "little" else ">"
    global_message = struct.unpack(prefix + "H", data[pos + 2:pos + 4])[0]
    count = data[pos + 4]
    pos += 5
    fields: list[FieldDef] = []
    for _ in range(count):
        if pos + 3 > len(data):
            raise ValueError("truncated field definition")
        number, size, base = data[pos], data[pos + 1], data[pos + 2]
        if base not in BASE_SIZE:
            raise ValueError(f"unsupported base type 0x{base:02x}")
        if size == 0:
            raise ValueError("zero-sized field")
        fields.append(FieldDef(number, size, base))
        pos += 3
    developer_fields: list[FieldDef] = []
    if header & 0x20:
        if pos >= len(data):
            raise ValueError("truncated developer field count")
        dev_count = data[pos]
        pos += 1
        for _ in range(dev_count):
            if pos + 3 > len(data):
                raise ValueError("truncated developer field definition")
            number, size, dev_index = data[pos], data[pos + 1], data[pos + 2]
            if size == 0:
                raise ValueError("zero-sized developer field")
            developer_fields.append(FieldDef(number, size, 0, dev_index))
            pos += 3
    return Definition(global_message, endian, fields, developer_fields), pos


def sanitize_bytes(data: bytes) -> tuple[bytes, dict[str, int]]:
    if len(data) < 14:
        raise ValueError("file is too short to be FIT")
    header_size = data[0]
    if header_size not in (12, 14):
        raise ValueError(f"unsupported FIT header size {header_size}")
    if data[8:12] != b".FIT":
        raise ValueError("missing .FIT signature")
    data_size = struct.unpack("<I", data[4:8])[0]
    data_end = header_size + data_size
    if data_end + 2 > len(data):
        raise ValueError("truncated FIT data")
    if data_end + 2 != len(data):
        raise ValueError("unexpected bytes after FIT CRC")
    if header_size == 14:
        stored_header_crc = struct.unpack("<H", data[12:14])[0]
        if stored_header_crc != crc16(data[:12]):
            raise ValueError("bad header CRC")
    stored_file_crc = struct.unpack("<H", data[data_end:data_end + 2])[0]
    if stored_file_crc != crc16(data[:data_end]):
        raise ValueError("bad file CRC")

    output = bytearray(data)
    definitions: dict[int, Definition] = {}
    dev_names: dict[tuple[int, int], str] = {}
    replacements = {"positions": 0, "serials": 0, "application_ids": 0, "sources": 0}
    pos = header_size

    while pos < data_end:
        header = data[pos]
        pos += 1
        if header & 0x80:  # compressed timestamp data message
            local = (header >> 5) & 0x03
        elif header & 0x40:  # definition message
            local = header & 0x0F
            architecture = data[pos + 1] if pos + 1 < data_end else 1
            endian = "little" if architecture == 0 else "big"
            definition, pos = read_definition(data, pos, header, endian)
            offset = 0
            for field in definition.fields:
                field.offset = offset
                offset += field.size
            for field in definition.developer_fields:
                field.offset = offset
                offset += field.size
            definitions[local] = definition
            continue
        else:
            local = header & 0x0F

        if local not in definitions:
            raise ValueError(f"data message without definition: local {local}")
        definition = definitions[local]
        compressed = bool(header & 0x80)
        data_fields = [
            field
            for field in definition.fields
            if not (compressed and field.number == 253)
        ]
        offset = 0
        for field in data_fields:
            field.offset = offset
            offset += field.size
        for field in definition.developer_fields:
            field.offset = offset
            offset += field.size
        message_end = pos + offset
        if message_end > data_end:
            raise ValueError("truncated data message")
        message = definition.global_message

        for field in data_fields:
            start = pos + field.offset
            raw = data[start:start + field.size]
            if message in POSITION_FIELDS and field.number in POSITION_FIELDS[message]:
                if field.size == 4 and field.base_type == 0x85:
                    value = struct.pack(
                        ">i" if definition.endian == "big" else "<i", 0x7FFFFFFF
                    )
                    output[start:start + 4] = value
                    replacements["positions"] += 1
            elif message in SERIAL_MESSAGES and field.number == 3 and field.size == 4:
                output[start:start + 4] = b"\0\0\0\0"
                replacements["serials"] += 1
            elif message == 207 and field.number in (0, 1):
                output[start:start + field.size] = b"\0" * field.size
                replacements["application_ids"] += 1

        # Field descriptions carry the developer index in field 0, the field
        # number in field 1 and the name in field 3. They appear before the
        # messages that use the fields.
        if message == 206:
            values = {}
            for field in data_fields:
                values[field.number] = decode_value(
                    field.base_type,
                    data[pos + field.offset:pos + field.offset + field.size],
                    definition.endian,
                )
            try:
                dev_names[(int(values[0]), int(values[1]))] = str(values[3])
            except (KeyError, TypeError, ValueError):
                pass

        for field in definition.developer_fields:
            start = pos + field.offset
            name = dev_names.get((field.dev_data_index, field.number))
            if name and name.lower() == "source":
                replacement = b"example.invalid\0"
                if field.size < len(replacement):
                    replacement = b"fixture\0"
                if field.size < len(replacement):
                    replacement = b"\0"
                output[start:start + field.size] = replacement.ljust(field.size, b"\0")
                replacements["sources"] += 1
        pos = message_end

    if pos != data_end:
        raise ValueError("data parser did not end at data size boundary")
    output[data_end:data_end + 2] = struct.pack("<H", crc16(bytes(output[:data_end])))
    return bytes(output), replacements


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for source in args.inputs:
        raw = source.read_bytes()
        sanitized, replacements = sanitize_bytes(raw)
        target = args.out / source.name
        target.write_bytes(sanitized)
        print(
            f"{source.name}: {len(raw)} -> {len(sanitized)} bytes; "
            f"positions={replacements['positions']} serials={replacements['serials']} "
            f"app_ids={replacements['application_ids']} sources={replacements['sources']}"
        )


if __name__ == "__main__":
    main()
