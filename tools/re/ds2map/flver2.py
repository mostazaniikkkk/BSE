"""DS2 SotFS FLVER2 (version 0x20010) reader -> meshes (positions, normals, uvs, triangles, material + textures).
Layout read structurally from the map/object files (every offset fits the file, every index < vertex count,
names decode, header triangle counts match the decoded face sets):
 header 0x80: 'FLVER\\0' 'L\\0', +0x08 u32 version, +0x0c u32 data offset, +0x10 u32 data size, +0x14 i32 dummies,
   +0x18 materials, +0x1c bones, +0x20 meshes, +0x24 vertex buffers, +0x28 f32[3] bbox min, +0x34 bbox max,
   +0x40 i32 true face count, +0x44 i32 total face count, +0x48 u8 vertex index bits (16/32), +0x49 u8 unicode,
   +0x50 i32 face sets, +0x54 i32 buffer layouts, +0x58 i32 textures.
 then tables: dummy 0x40, material 0x20, bone 0x80, mesh 0x30, face set 0x20, vertex buffer 0x20, layout 0x10, texture 0x20.
 material: i32 name off, i32 mtd off, i32 texture count, i32 first texture, i32 flags, i32 gx off, i32, i32.
 mesh: u8 dynamic, 3 pad, i32 material, i32 0, i32 0, i32 default bone, i32 bone count, i32 bbox off, i32 bone indices off,
   i32 face set count, i32 face set indices off, i32 vertex buffer count, i32 vertex buffer indices off.
 face set: u32 flags (0 = full detail; other values = LOD / extra sets, skipped), u8 triangle strip, u8 cull backfaces,
   i16, i32 index count, i32 indices off (from data offset), i32 indices bytes, i32 0, i32 index bits (0 = header), i32 0.
 vertex buffer: i32 buffer index, i32 layout, i32 vertex size, i32 vertex count, i32 0, i32 0, i32 bytes, i32 off (from data offset).
 layout: i32 member count, i32 0, i32 0, i32 members off; member 0x14: i32 0, i32 struct offset, i32 type, i32 semantic, i32 index.
   types seen: 2 = float3, 0x11 = 4 x u8 (normal/tangent: (b-127)/127; bone indices), 0x13 = 4 x u8 /255 (colour, weights),
   0x15 = 2 x i16 UV (scale: see UV_SCALE). semantics: 0 position, 1 bone weights, 2 bone indices, 3 normal, 5 uv,
   6 tangent, 7 bitangent, 10 vertex colour.
 texture: i32 path off, i32 type off (g_DiffuseTexture...), f32 scale u, f32 scale v, ...
Vertices are in model space (map pieces checked: a single root bone at the origin)."""
import struct
import numpy as np

UV_SCALE = 1.0 / 1024   # raw i16 / 1024; 1024 vs 2048: sin confirmar (no DS2 source found)


class Flver:
    def __init__(self, d):
        assert d[:6] == b'FLVER\0', d[:8]
        self.d = d
        self.version, self.doff = struct.unpack_from('<II', d, 8)
        nd, nm, nb, nmesh, nvb = struct.unpack_from('<5i', d, 0x14)
        self.bbox = (struct.unpack_from('<3f', d, 0x28), struct.unpack_from('<3f', d, 0x34))
        self.faces_header = struct.unpack_from('<2i', d, 0x40)
        self.ibits = d[0x48]
        self.uni = d[0x49] == 1
        nfs, nbl, ntex = struct.unpack_from('<3i', d, 0x50)
        p = 0x80 + nd * 0x40
        mats = [struct.unpack_from('<8i', d, p + i * 0x20) for i in range(nm)]
        p += nm * 0x20
        self.bones = []
        for i in range(nb):
            t = struct.unpack_from('<3f', d, p + i * 0x80)
            no, = struct.unpack_from('<I', d, p + i * 0x80 + 0xc)
            r = struct.unpack_from('<3f', d, p + i * 0x80 + 0x10)
            par, = struct.unpack_from('<h', d, p + i * 0x80 + 0x1c)
            s = struct.unpack_from('<3f', d, p + i * 0x80 + 0x20)
            self.bones.append(dict(name=self.s(no), t=t, r=r, s=s, parent=par))
        p += nb * 0x80
        meshes = [struct.unpack_from('<B3x11i', d, p + i * 0x30) for i in range(nmesh)]
        p += nmesh * 0x30
        fsets = [struct.unpack_from('<IBBh6i', d, p + i * 0x20) for i in range(nfs)]
        p += nfs * 0x20
        vbs = [struct.unpack_from('<8i', d, p + i * 0x20) for i in range(nvb)]
        p += nvb * 0x20
        lays = []
        for i in range(nbl):
            mc, _, _, mo = struct.unpack_from('<4i', d, p + i * 0x10)
            lays.append([struct.unpack_from('<5i', d, mo + j * 0x14) for j in range(mc)])
        p += nbl * 0x10
        texs = []
        for i in range(ntex):
            po, to, su, sv = struct.unpack_from('<iiff', d, p + i * 0x20)
            texs.append(dict(path=self.s(po), type=self.s(to), scale=(su, sv)))
        self.materials = [dict(name=self.s(m[0]), mtd=self.s(m[1]), textures=texs[m[3]:m[3] + m[2]], flags=m[4])
                          for m in mats]
        self.fset_flags = sorted(set(f[0] for f in fsets))
        self.meshes = []
        for m in meshes:
            dyn, mat, _, _, dbone, bcount, bbo, bio, fsc, fso, vbc, vbo = m
            fsi = struct.unpack_from('<%di' % fsc, d, fso)
            vbi = struct.unpack_from('<%di' % vbc, d, vbo)
            verts = {}
            for bi in vbi:
                bidx, lay, vsize, vcount, _, _, blen, boff = vbs[bi]
                a = np.frombuffer(d, np.uint8, vsize * vcount, self.doff + boff).reshape(vcount, vsize)
                for (_, so, typ, sem, idx) in lays[lay]:
                    if sem == 0 and typ == 2:
                        verts['pos'] = a[:, so:so + 12].copy().view('<f4')
                    elif sem == 3 and typ == 0x11 and 'normal' not in verts:
                        verts['normal'] = (a[:, so:so + 3].astype(np.float32) - 127) / 127
                    elif sem == 5 and typ == 0x15:
                        verts.setdefault('uv', []).append(
                            a[:, so:so + 4].copy().view('<i2').astype(np.float32) * UV_SCALE)
                    elif sem == 10 and typ == 0x13 and 'color' not in verts:
                        verts['color'] = a[:, so:so + 4].astype(np.float32) / 255
            tris = None
            for fi in fsi:
                flags, strip, cull, _, icount, ioff, ilen, _, ib, _ = fsets[fi]
                if flags != 0:
                    continue
                bits = ib or self.ibits
                idx = np.frombuffer(d, '<u2' if bits == 16 else '<u4', icount, self.doff + ioff).astype(np.int64)
                tris = strip_to_tris(idx, bits) if strip else idx.reshape(-1, 3)
            self.meshes.append(dict(material=mat, dynamic=dyn, default_bone=dbone,
                                    tris=tris if tris is not None else np.zeros((0, 3), np.int64), **verts))

    def s(self, o):
        d = self.d
        if self.uni:
            e = o
            while d[e:e + 2] != b'\0\0':
                e += 2
            return d[o:e].decode('utf-16le')
        return d[o:d.index(b'\0', o)].decode('shift_jis')


def strip_to_tris(idx, bits=16):
    """Triangle strip, restart index 0xFFFF (16-bit) / 0xFFFFFFFF; alternate winding; degenerates dropped."""
    restart = 0xFFFF if bits == 16 else 0xFFFFFFFF
    out = []
    flip = False
    a = b = None
    for v in idx.tolist():
        if v == restart:
            a = b = None
            flip = False
            continue
        if a is None:
            a = v
            continue
        if b is None:
            b = v
            continue
        if a != b and b != v and a != v:
            out.append((b, a, v) if flip else (a, b, v))
        flip = not flip
        a, b = b, v
    return np.array(out, np.int64).reshape(-1, 3)


def part_matrix(pos, rot, scale, order='XZY'):
    """Model -> map space for an MSB part. Rotation in degrees. order 'XZY' = rotate about X, then Z, then Y
    (column vectors: M = T * Ry * Rz * Rx * S). The order is sin confirmar for DS2 (almost every part found
    here only has a Y rotation, where the order does not matter)."""
    rx, ry, rz = np.radians(rot)
    cx, sx, cy, sy, cz, sz = np.cos(rx), np.sin(rx), np.cos(ry), np.sin(ry), np.cos(rz), np.sin(rz)
    R = {'X': np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]),
         'Y': np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]),
         'Z': np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])}
    M3 = np.eye(3)
    for axis in order:
        M3 = R[axis] @ M3
    M = np.eye(4)
    M[:3, :3] = M3 @ np.diag(scale)
    M[:3, 3] = pos
    return M
