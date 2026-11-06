"""Unpacks a BND4 with 0x24-byte entries and UTF-16 names (seen in c0001.anibnd):
u32 flags, i32 -1, i64 size, i64 uncompressed size, u32 data offset, i32 id, u32 name offset."""
import collections
import os
import struct
import sys

NUL2 = b"\x00\x00"


def unpack(path, out):
    d = open(path, "rb").read()
    n = struct.unpack_from("<I", d, 0xC)[0]
    assert struct.unpack_from("<q", d, 0x20)[0] == 0x24 and d[0x30] == 1
    os.makedirs(out, exist_ok=True)
    exts = collections.Counter()
    listing = []
    for i in range(n):
        e = 0x40 + i * 0x24
        size, usize = struct.unpack_from("<qq", d, e + 8)
        off, fid, noff = struct.unpack_from("<IiI", d, e + 0x18)
        end = noff
        while d[end:end + 2] != NUL2:
            end += 2
        name = d[noff:end].decode("utf-16-le")
        base = name.replace(chr(92), "/").split("/")[-1]
        exts[os.path.splitext(base)[1].lower()] += 1
        open(os.path.join(out, base), "wb").write(d[off:off + size])
        listing.append((fid, name, size))
    return exts, listing


if __name__ == "__main__":
    exts, listing = unpack(sys.argv[1], sys.argv[2])
    print(exts)
    for fid, name, size in listing:
        if not name.lower().endswith(".nsa"):
            print(fid, name, size)
