"""Decompresses a DCX(DFLT) container and unpacks the BND4 binder inside into a folder.

DCX header: 'DCX\\0', 'DCS\\0' + u32be uncompressed + u32be compressed, 'DCP\\0DFLT', 'DCA\\0' + u32be
size, then a zlib stream (the exe hands it to its "algorithm_deflate_level_6" decompressor).
BND4 layout is checked structurally: every entry offset/size must fit in the file and names must decode.
"""
import os
import struct
import sys
import zlib

NUL = b"\x00"


def undcx(data: bytes) -> bytes:
    assert data[:4] == b"DCX" + NUL and data[0x28:0x2C] == b"DFLT", "not a DCX/DFLT file"
    usize, csize = struct.unpack_from(">II", data, 0x1C)
    dca = data.index(b"DCA" + NUL)
    start = dca + struct.unpack_from(">I", data, dca + 4)[0]
    out = zlib.decompress(data[start:start + csize])
    assert len(out) == usize, (len(out), usize)
    return out


def unbnd4(data: bytes, outdir: str):
    assert data[:4] == b"BND4", data[:4]
    count = struct.unpack_from("<I", data, 0x0C)[0]
    entry_size = struct.unpack_from("<q", data, 0x20)[0]
    unicode = data[0x30] == 1
    print(f"BND4: {count} files, entry size {entry_size:#x}, unicode {unicode}")
    # Entry (0x18 bytes here): u32 flags, i32 -1, u64 size, u32 data offset, u32 name offset.
    assert entry_size == 0x18, "only the 0x18-byte entry layout seen in regulation is handled"
    os.makedirs(outdir, exist_ok=True)
    for i in range(count):
        e = 0x40 + i * entry_size
        size, off, noff = struct.unpack_from("<qII", data, e + 8)
        if unicode:
            end = noff
            while data[end:end + 2] != NUL * 2:
                end += 2
            name = data[noff:end].decode("utf-16-le")
        else:
            name = data[noff:data.index(NUL, noff)].decode("shift_jis")
        assert off + size <= len(data)
        base = name.replace("\\", "/").split("/")[-1]
        open(os.path.join(outdir, base), "wb").write(data[off:off + size])
        print(f"  {size:9} {name}")


if __name__ == "__main__":
    raw = open(sys.argv[1], "rb").read()
    if raw[:4] == b"DCX" + NUL:
        raw = undcx(raw)
    unbnd4(raw, sys.argv[2])
