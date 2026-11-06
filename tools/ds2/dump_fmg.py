"""Dumps an FMG text file as 'id<TAB>text' lines.

Layout as observed in the files (little-endian, 32-bit offsets): +0x0C u32 group count,
+0x10 u32 string count, +0x14 u32 string offset table; groups of 12 bytes at +0x1C:
(u32 first string index, u32 first id, u32 last id); strings are UTF-16LE, offset 0 = no text.
"""
import struct
import sys


def read_fmg(data):
    groups, strings, table = struct.unpack_from("<III", data, 0x0C)
    out = {}
    for g in range(groups):
        index, first, last = struct.unpack_from("<III", data, 0x1C + g * 12)
        for k, tid in enumerate(range(first, last + 1)):
            off = struct.unpack_from("<I", data, table + (index + k) * 4)[0]
            if off == 0:
                continue
            end = off
            while data[end:end + 2] != b"\x00\x00":
                end += 2
            out[tid] = data[off:end].decode("utf-16-le")
    return out


if __name__ == "__main__":
    for path in sys.argv[1:]:
        for tid, text in sorted(read_fmg(open(path, "rb").read()).items()):
            print(f"{tid}\t{text!r}")
