"""DS2 SotFS MSB ('MSB ', 64-bit) reader. Layout read from the files themselves (entry offsets, names decode,
model indices resolve, positions land inside the map):
  header 0x10, then param lists chained: i32 version, i32 count (offsets + 1 'next'), i64 name offset (UTF-16),
  i64 offsets[count-1], i64 next list. Order: MODEL_PARAM_ST, EVENT_PARAM_ST, POINT_PARAM_ST, ROUTE_PARAM_ST,
  LAYER_PARAM_ST, PARTS_PARAM_ST, MAPSTUDIO_PARTS_POSE_ST, MAPSTUDIO_BONE_NAME_STRING.
  MODEL entry: +0x00 i64 name offset (rel. entry), +0x08 u16 type, +0x0a u16 index.
  PARTS entry: +0x00 i64 name offset (rel. entry), +0x08 u8 type (0 map piece, 1 object, 3 collision, 4 navmesh?,
    5 connect collision?), +0x0a i16 index in type, +0x0c i16 model index, +0x10 f32[3] position, +0x1c f32[3]
    rotation (degrees), +0x28 f32[3] scale.
"""
import struct, sys

class MSB:
    def __init__(self, path):
        d = self.d = open(path, 'rb').read(); assert d[:4] == b'MSB '
        self.lists = {}
        p = 0x10
        while p:
            ver, cnt, no = struct.unpack_from('<iiq', d, p)
            offs = struct.unpack_from('<%dq' % cnt, d, p + 16)
            self.lists[self.ws(no)] = offs[:-1]
            p = offs[-1]
        self.models = []
        for o in self.lists['MODEL_PARAM_ST']:
            no, typ, idx = struct.unpack_from('<qHH', d, o)
            self.models.append(dict(name=self.ws(o + no), type=typ, index=idx))
        self.parts = []
        for o in self.lists['PARTS_PARAM_ST']:
            no, typ, _, idx, mi = struct.unpack_from('<qBBhh', d, o)
            pos = struct.unpack_from('<3f', d, o + 0x10); rot = struct.unpack_from('<3f', d, o + 0x1c)
            scl = struct.unpack_from('<3f', d, o + 0x28)
            self.parts.append(dict(name=self.ws(o + no), type=typ, index=idx, model=self.models[mi]['name'] if mi >= 0 else None,
                                   pos=pos, rot=rot, scale=scl, offset=o))
    def ws(self, o):
        d = self.d; e = o
        while d[e:e + 2] != b'\0\0': e += 2
        return d[o:e].decode('utf-16le')

if __name__ == '__main__':
    m = MSB(sys.argv[1])
    import collections
    print({k: len(v) for k, v in m.lists.items()})
    print(collections.Counter((p['type'], (p['model'] or '?')[0]) for p in m.parts))
    for p in m.parts[:int(sys.argv[2]) if len(sys.argv) > 2 else 5]:
        print(p)
