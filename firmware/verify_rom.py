"""Independent structural and unchanged-payload checks for the rebuilt ROM."""

import hashlib
import re
import struct
import sys
import zlib
from pathlib import Path


def inspect(path):
    with path.open("rb") as f:
        header = f.read(16384)
        f.seek(0, 2)
        total = f.tell()
        f.seek(total - 32)
        footer = f.read(32)
        f.seek(16384)
        remaining = total - 32 - 16384
        crc = 0
        while remaining:
            block = f.read(min(remaining, 1024 * 1024))
            if not block:
                raise ValueError("Truncated ROM")
            crc = zlib.crc32(block, crc)
            remaining -= len(block)
        checks = {
            "magic": footer[:8] == b"12345678",
            "header_crc": zlib.crc32(header) == struct.unpack("<I", footer[8:12])[0],
            "bin_crc": crc == struct.unpack("<I", footer[12:16])[0],
            "header_signature": footer[16:] == header[:16],
        }
        locations = [
            (int(offset, 16), int(length, 16))
            for offset, length in re.findall(
                rb"filepartload 20400000 LetvUpgrade\.bin ([0-9A-F]+) ([0-9A-F]+)",
                header,
            )
        ]
        if len(locations) != 6:
            raise ValueError("Unexpected payload count")
        decompression_sizes = [
            int(length, 16)
            for length in re.findall(
                rb"(?m)^mmc unlzo(?:\.cont)? 20400000 ([0-9A-F]+) system 1$",
                header,
            )
        ]
        if decompression_sizes != [length for _, length in locations[:3]]:
            raise ValueError("System decompression lengths do not match loaded payloads")
        if any(offset % 4096 for offset, _ in locations):
            raise ValueError("Payload offset is not 4 KiB aligned")
        if any(
            locations[index][0] + locations[index][1] > locations[index + 1][0]
            for index in range(5)
        ):
            raise ValueError("Payloads overlap")
        hashes = []
        for offset, length in locations:
            f.seek(offset)
            hashes.append(hashlib.sha256(f.read(length)).hexdigest())
    print(path, total, checks)
    if not all(checks.values()):
        raise ValueError("ROM checksum failed")
    return locations, hashes


if __name__ == "__main__":
    old, old_hashes = inspect(Path(sys.argv[1]))
    new, new_hashes = inspect(Path(sys.argv[2]))
    for index in range(3, 6):
        ok = old_hashes[index] == new_hashes[index]
        print(f"non-system payload {index} unchanged: {ok}")
        if not ok:
            raise ValueError("Non-system payload changed")
