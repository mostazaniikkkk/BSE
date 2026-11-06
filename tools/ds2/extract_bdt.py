"""Extracts files by path from a DS2 BHD5/BDT archive (BHD already decrypted with decrypt_bhd.py).

Derived from DarkSoulsII.exe:
  FUN_140fef880  BHD5 header: +0x10 u32 bucket count, +0x14 u32 bucket table offset.
  FUN_140fef650  lookup: skip up to the first ':', then for each UTF-16 char: 'A'-'Z' -> lowercase,
                 '\\' -> '/', h = h*37 + c (u32). Bucket = h % bucket count; bucket = {u32 count, u32 offset};
                 entries are 32 bytes and matched on entry+0 == h.
  FUN_140ff0eb0  entry+4 u32 = file size, entry+8 u64 = offset in the .bdt.
  FUN_140ff1860  entry+0x10 u64 = hash info offset, entry+0x18 u64 = AES key info offset (0 = none).
  FUN_140ff1730  key info: key[16], +0x10 u32 range count, +0x14 ranges of (u64 start, u64 end) relative to
                 the file; each overlapping range is decrypted with FUN_140fefee0.
  FUN_140fefee0  OpenSSL EVP cipher at 0x1411baa70: nid 418 (aes-128-ecb), padding off.
"""
import os
import struct
import sys
from Crypto.Cipher import AES


def path_hash(path: str) -> int:
    h = 0
    for ch in path[path.index(":") + 1:]:
        c = ord(ch)
        if 0x41 <= c <= 0x5A:
            c += 0x20
        elif c == 0x5C:
            c = 0x2F
        h = (h * 37 + c) & 0xFFFFFFFF
    return h


class Archive:
    def __init__(self, bhd_path, bdt_path):
        self.bhd = open(bhd_path, "rb").read()
        assert self.bhd[:4] == b"BHD5"
        self.bucket_count, self.bucket_offset = struct.unpack_from("<II", self.bhd, 0x10)
        self.bdt_path = bdt_path

    def find(self, path):
        h = path_hash(path)
        count, off = struct.unpack_from("<II", self.bhd, self.bucket_offset + (h % self.bucket_count) * 8)
        for i in range(count):
            entry = struct.unpack_from("<IIqqq", self.bhd, off + i * 32)
            if entry[0] == h:
                return entry
        return None

    def read(self, path):
        entry = self.find(path)
        if entry is None:
            return None
        _, size, offset, _, key_offset = entry
        with open(self.bdt_path, "rb") as f:
            f.seek(offset)
            data = bytearray(f.read(size))
        if key_offset:
            key = self.bhd[key_offset:key_offset + 16]
            ranges = struct.unpack_from("<I", self.bhd, key_offset + 0x10)[0]
            cipher = AES.new(key, AES.MODE_ECB)
            for r in range(ranges):
                start, end = struct.unpack_from("<qq", self.bhd, key_offset + 0x14 + r * 16)
                if start < 0 or end <= start:
                    continue
                end = min(end, len(data))
                data[start:end] = cipher.decrypt(bytes(data[start:end]))
        return bytes(data)


if __name__ == "__main__":
    bhd, bdt, outdir = sys.argv[1:4]
    archive = Archive(bhd, bdt)
    os.makedirs(outdir, exist_ok=True)
    for path in sys.argv[4:]:
        data = archive.read(path)
        if data is None:
            print(f"missing  {path}")
            continue
        name = path.split(":", 1)[1].lstrip("/").replace("/", "__")
        open(os.path.join(outdir, name), "wb").write(data)
        print(f"{len(data):9}  {data[:4]!r}  {path}")
