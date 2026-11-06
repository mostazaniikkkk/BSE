"""Decrypts a DS2 *.bhd archive header with its *KeyCode.pem.

Derived from DarkSoulsII.exe:
  FUN_1402ef410  mounts each archive with (name.bhd, nameKeyCode.pem) via FUN_140b0bd50.
  FUN_140ff00a0  PEM_read_RSAPublicKey (FUN_140aa92c0), then for every RSA_size() block calls
                 RSA_public_decrypt (FUN_140aa8430 -> meth+0x10, OpenSSL 0.9.8o) with padding 3
                 (RSA_NO_PADDING) and keeps output bytes [1:], i.e. 255 of every 256.
"""
import base64
import sys


def read_der_int(buf, i):
    assert buf[i] == 0x02
    i += 1
    length = buf[i]; i += 1
    if length & 0x80:
        n = length & 0x7F
        length = int.from_bytes(buf[i:i + n], "big"); i += n
    return int.from_bytes(buf[i:i + length], "big"), i + length


def load_pem(path):
    text = open(path).read()
    body = "".join(l for l in text.splitlines() if not l.startswith("-----"))
    der = base64.b64decode(body)
    assert der[0] == 0x30
    i = 2 + (der[1] & 0x7F if der[1] & 0x80 else 0)
    n, i = read_der_int(der, i)
    e, i = read_der_int(der, i)
    return n, e


def decrypt(data, n, e):
    size = (n.bit_length() + 7) // 8
    out = bytearray()
    for off in range(0, len(data), size):
        m = pow(int.from_bytes(data[off:off + size], "big"), e, n)
        out += m.to_bytes(size, "big")[1:]
    return bytes(out)


if __name__ == "__main__":
    n, e = load_pem(sys.argv[2])
    out = decrypt(open(sys.argv[1], "rb").read(), n, e)
    open(sys.argv[3], "wb").write(out)
    print(f"{len(out)} bytes, magic {out[:4]!r}")
