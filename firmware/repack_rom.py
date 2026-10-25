"""Repack this MStar ROM while retaining all non-system payloads verbatim."""

import argparse
import hashlib
import re
import struct
import zlib
from pathlib import Path

HEADER_SIZE = 0x4000
ALIGNMENT = 0x1000
FOOTER_SIZE = 32
PART_RE = re.compile(rb"(?m)^filepartload 20400000 LetvUpgrade\.bin ([0-9A-F]+) ([0-9A-F]+)$")
UNLZO_RE = re.compile(rb"(?m)^(mmc unlzo(?:\.cont)? 20400000 )([0-9A-F]+)( system 1)$")


def copy_bytes(source, dest, length):
    while length:
        data = source.read(min(length, 1024 * 1024))
        if not data:
            raise ValueError("Unexpected end of payload")
        dest.write(data)
        length -= len(data)


def crc_region(file, start, length):
    file.seek(start)
    crc = 0
    while length:
        block = file.read(min(length, 1024 * 1024))
        if not block:
            raise ValueError("Unexpected end while calculating CRC")
        crc = zlib.crc32(block, crc)
        length -= len(block)
    return crc


def repack(original_path, output_path, lzo_parts):
    with original_path.open("rb") as original:
        original.seek(0, 2)
        original_size = original.tell()
        original.seek(0)
        header = original.read(HEADER_SIZE)
        original.seek(original_size - FOOTER_SIZE)
        footer = original.read(FOOTER_SIZE)
        if footer[:8] != b"12345678" or footer[16:] != header[:16]:
            raise ValueError("Unexpected ROM footer")
        if zlib.crc32(header) != struct.unpack("<I", footer[8:12])[0]:
            raise ValueError("Original header CRC mismatch")
        matches = list(PART_RE.finditer(header))
        if len(matches) != 6:
            raise ValueError(f"Expected six payloads, found {len(matches)}")
        locations = [(int(m[1], 16), int(m[2], 16)) for m in matches]
        if locations[0][0] != HEADER_SIZE or any(
            a < HEADER_SIZE or a + n > original_size - FOOTER_SIZE
            for a, n in locations
        ):
            raise ValueError("Invalid payload offsets")
        with output_path.open("w+b") as output:
            output.write(b"\xff" * HEADER_SIZE)
            new_locations = []
            for index, (old_offset, old_length) in enumerate(locations):
                new_offset = output.tell()
                if index < 3 and lzo_parts:
                    path = lzo_parts[index]
                    length = path.stat().st_size
                    with path.open("rb") as part:
                        copy_bytes(part, output, length)
                else:
                    original.seek(old_offset)
                    length = old_length
                    copy_bytes(original, output, length)
                new_locations.append((new_offset, length))
                pad = -output.tell() % ALIGNMENT
                output.write(b"\xff" * pad)
            bin_end = output.tell()
            replacements = iter(new_locations)

            def replace(_match):
                offset, length = next(replacements)
                return f"filepartload 20400000 LetvUpgrade.bin {offset:X} {length:X}".encode()

            script_end = header.index(b"% <- this is end of file symbol\n") + len(
                b"% <- this is end of file symbol\n"
            )
            new_script = PART_RE.sub(replace, header[:script_end])
            if len(UNLZO_RE.findall(new_script)) != 3:
                raise ValueError("Expected three system decompression commands")
            decompression_sizes = iter(length for _, length in new_locations[:3])
            new_script = UNLZO_RE.sub(
                lambda match: match[1] + f"{next(decompression_sizes):X}".encode() + match[3],
                new_script,
            )
            if len(new_script) > HEADER_SIZE:
                raise ValueError("Header does not fit 16 KiB")
            new_header = new_script + b"\xff" * (HEADER_SIZE - len(new_script))
            output.seek(0)
            output.write(new_header)
            output.flush()
            bin_crc = crc_region(output, HEADER_SIZE, bin_end - HEADER_SIZE)
            output.seek(bin_end)
            output.write(
                b"12345678"
                + struct.pack("<II", zlib.crc32(new_header), bin_crc)
                + new_header[:16]
            )
    return new_locations


def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("original", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("lzo", nargs="*", type=Path)
    args = parser.parse_args()
    if args.lzo and len(args.lzo) != 3:
        parser.error("Provide exactly three LZO chunks or none")
    for offset, length in repack(args.original, args.output, args.lzo):
        print(f"payload offset={offset:X} size={length:X}")
    print(f"SHA256 {sha256(args.output)}")
