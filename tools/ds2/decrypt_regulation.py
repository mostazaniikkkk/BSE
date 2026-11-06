"""Decrypts DS2 SotFS enc_regulation.bnd.dcx.

Derived from DarkSoulsII.exe (Ghidra, project D:/Reversing/DS2):
  FUN_1402e0470  local regulation load (RegulationResourceObject vtable +0x80 -> FUN_1402df8f0):
                 AES key is the two qwords 0x54940adf30811740, 0x4c25bfec71e10933 (little-endian),
                 then the result goes to the "algorithm_deflate_level_6" (DCX) decompressor.
  FUN_140261150  data = IV[16] | TAG[16] | ciphertext.
  (FUN_1402da560 uses the same scheme with another key, for the network-downloaded regulation.)
  FUN_140260e70  counter block = 0x80 | IV[0:11] | u32be counter (starts at 0).
  FUN_140260ad0  counter is incremented before each block, so the first block uses counter 1.
  (CWC mode: the tag check over the ciphertext is not reproduced here.)
"""
import struct
import sys
from Crypto.Cipher import AES

KEY = struct.pack("<QQ", 0x54940ADF30811740, 0x4C25BFEC71E10933)


def decrypt(data: bytes) -> bytes:
    iv, ciphertext = data[:16], data[32:]
    prefix = b"\x80" + iv[:11]
    cipher = AES.new(KEY, AES.MODE_CTR, nonce=prefix, initial_value=1)
    return cipher.decrypt(ciphertext)


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    out = decrypt(open(src, "rb").read())
    open(dst, "wb").write(out)
    print(f"{len(out)} bytes, magic {out[:4]!r}")
