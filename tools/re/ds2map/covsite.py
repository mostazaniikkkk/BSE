"""One covenant site: NPC generator position (GeneratorLocation, map-local; + map offset = MSB space) -> MSB map pieces / objects within RADIUS m (part AABB from the FLVER
header bbox, transformed) -> per-part OBJ in map space, combined OBJ cropped to a cube of +-RADIUS around the NPC,
diffuse textures (PNG + alpha-weighted mean colour), manifest JSON.
Usage: site.py <key> <map> <npc x> <npc y> <npc z> [radius]"""
import json, os, struct, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import bxf4, flver2, msb2, tex  # noqa: E402
import ds2fs  # noqa: E402
import ds2paths  # noqa: E402

ROOT = os.path.join(ds2paths.EXTRACTED, 'covenant_sites')
RAW = os.path.join(ROOT, 'raw')
SIGN = float(os.environ.get('ROT_SIGN', '1'))


def raw_map(m):
    for p, f in [('gamedata:/map/%s/%s.msb' % (m, m), m + '.msb'), ('gamedata:/model/map/%s.mapbhd' % m, m + '.mapbhd'),
                 ('gamedata:/model/map/%s.mapbdt' % m, m + '.mapbdt'),
                 ('gamedata:/model/map/t%s.tpfbhd' % m[1:], 't%s.tpfbhd' % m[1:]),
                 ('gamedata:/model/map/t%s.tpfbdt' % m[1:], 't%s.tpfbdt' % m[1:])]:
        ds2fs.fetch(p, os.path.join(RAW, f))
    return (msb2.MSB(os.path.join(RAW, m + '.msb')),
            bxf4.BXF4(os.path.join(RAW, m + '.mapbhd'), os.path.join(RAW, m + '.mapbdt')),
            bxf4.BXF4(os.path.join(RAW, 't%s.tpfbhd' % m[1:]), os.path.join(RAW, 't%s.tpfbdt' % m[1:])))


def bnd4(d):
    """In-memory BND4 with 0x24-byte entries (tools/ds2/unpack_bnd4_any.py layout): {base name: bytes}."""
    d = ds2fs.undcx(d)
    assert d[:4] == b'BND4'
    n, = struct.unpack_from('<I', d, 0xc)
    es, = struct.unpack_from('<q', d, 0x20)
    uni = d[0x30] == 1
    out = {}
    for i in range(n):
        size, usize, off, fid, no = struct.unpack_from('<qqIiI', d, 0x40 + i * es + 8)
        if uni:
            e = no
            while d[e:e + 2] != b'\0\0':
                e += 2
            name = d[no:e].decode('utf-16le')
        else:
            name = d[no:d.index(b'\0', no)].decode('shift_jis')
        out[name.replace('\\', '/').split('/')[-1]] = ds2fs.undcx(d[off:off + size])
    return out


_obj = {}


def obj_bnd(model):
    if model not in _obj:
        path = os.path.join(RAW, 'obj', model + '.bnd')
        try:
            ds2fs.fetch('gamedata:/model/obj/%s.bnd' % model, path)
            _obj[model] = bnd4(open(path, 'rb').read())
        except FileNotFoundError:
            _obj[model] = None
    return _obj[model]


def header_bbox(d):
    return np.array(struct.unpack_from('<3f', d, 0x28)), np.array(struct.unpack_from('<3f', d, 0x34))


def world_aabb(M, lo, hi):
    c = np.array([[x, y, z, 1] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    w = (M @ c.T).T[:, :3]
    return w.min(0), w.max(0)


def dist_aabb(p, lo, hi):
    return float(np.linalg.norm(np.maximum(0, np.maximum(lo - p, p - hi))))


def collision_offset(msb):
    import collections
    c = collections.Counter(tuple(p['pos']) for p in msb.parts if p['type'] == 3)
    return c.most_common(1)[0][0]


def matrix(part):
    return flver2.part_matrix(part['pos'], tuple(SIGN * r for r in part['rot']), part['scale'])


def main(key, m, npc, radius=30.0, objects=True):
    out = os.path.join(ROOT, key)
    os.makedirs(os.path.join(out, 'parts'), exist_ok=True)
    os.makedirs(os.path.join(out, 'tex'), exist_ok=True)
    msb, mbnd, tbnd = raw_map(m)
    # GeneratorLocation is map-local: + the common position of the map's collision parts (check_transform.py)
    offset = np.array(collision_offset(msb))
    npc_local = np.array(npc)
    npc = npc_local + offset
    sel = []
    for part in msb.parts:
        if part['type'] == 0:
            entry = '%s\\%s.flv.dcx' % (m, part['model'])
            if entry not in mbnd.files:
                continue
            data = mbnd.read(entry)
            src = 'gamedata:/model/map/%s.mapbhd+.mapbdt : %s' % (m, entry)
        elif part['type'] == 1 and objects:
            if np.linalg.norm(np.array(part['pos']) - npc) > radius + 60:
                continue
            b = obj_bnd(part['model'])
            if not b or part['model'] + '.flv' not in b:
                continue
            data = b[part['model'] + '.flv']
            src = 'gamedata:/model/obj/%s.bnd : %s.flv' % (part['model'], part['model'])
        else:
            continue
        if struct.unpack_from('<i', data, 0x20)[0] == 0:
            continue  # FLVER without meshes (marker objects such as o04_9300, o23_1080): nothing to draw
        M = matrix(part)
        lo, hi = world_aabb(M, *header_bbox(data))
        dist = dist_aabb(npc, lo, hi)
        if dist <= radius:
            sel.append((part, M, data, src, dist, lo, hi))
    texinfo = {}
    obj_tex = {}

    def texture(base, part):
        if base in texinfo:
            return texinfo[base]
        info = None
        # names compared ignoring case: the archive lookup lowercases paths (tools/ds2/extract_bdt.py, FUN_140fef650)
        entry = '%s\\%s.tpf.dcx' % (m, base)
        entry = {k.lower(): k for k in tbnd.files}.get(entry.lower(), entry)
        cands = []
        if entry in tbnd.files:
            cands.append(('gamedata:/model/map/t%s.tpfbhd+.tpfbdt : %s' % (m[1:], entry), tbnd.read(entry)))
        if part['type'] == 1:
            b = obj_bnd(part['model'])
            lk = {k.lower(): k for k in b} if b else {}
            if (base + '.tpf').lower() in lk:
                k = lk[(base + '.tpf').lower()]
                cands.append(('gamedata:/model/obj/%s.bnd : %s' % (part['model'], k), b[k]))
        for src, d in cands:
            try:
                name, fmt, dds = tex.tpf_textures(d)[0]
                im = tex.dds_image(dds)
                im.save(os.path.join(out, 'tex', base + '.png'))
                rgb, alpha = tex.average(im)
                info = dict(source=src, format=fmt, size=im.size, mean_rgb=rgb, mean_alpha=round(alpha, 3))
                break
            except Exception as e:  # noqa: BLE001
                info = dict(source=src, error=repr(e))
        texinfo[base] = info
        return info

    manifest = dict(map=m, npc_local=npc_local.tolist(), map_offset=offset.tolist(), npc=npc.tolist(), radius=radius, rot_sign=SIGN, parts=[])
    combo_v, combo_f, combo_mat, combo_mtd = [], [], [], []
    mats_used = {}
    nv = 0
    for part, M, data, src, dist, lo, hi in sorted(sel, key=lambda s: s[4]):
        f = flver2.Flver(data)
        pname = part['name']
        lines = ['mtllib ../site.mtl']
        vo = 1
        pentry = dict(name=pname, type={0: 'MapPiece', 1: 'Object'}[part['type']], model=part['model'], source=src,
                      pos=part['pos'], rot=part['rot'], scale=part['scale'], dist=round(dist, 2),
                      aabb=[lo.round(2).tolist(), hi.round(2).tolist()], meshes=[])
        for mesh in f.meshes:
            if 'pos' not in mesh or len(mesh['tris']) == 0:
                continue
            mat = f.materials[mesh['material']]
            diff = [t for t in mat['textures'] if t['type'] == 'g_DiffuseTexture']
            base = diff[0]['path'].replace('\\', '/').split('/')[-1].rsplit('.', 1)[0] if diff and diff[0]['path'] else None
            ti = texture(base, part) if base else None
            mname = base or 'none'
            mats_used[mname] = ti
            P = np.c_[mesh['pos'], np.ones(len(mesh['pos']))] @ M.T
            P = P[:, :3]
            uv = mesh['uv'][0] if mesh.get('uv') else np.zeros((len(P), 2))
            lines.append('usemtl %s' % mname)
            lines += ['v %.4f %.4f %.4f' % tuple(v) for v in P]
            lines += ['vt %.5f %.5f' % (u, 1 - v) for u, v in uv]
            T = mesh['tris'][:, [0, 2, 1]]  # FLVER front faces are clockwise (check_winding.py): flipped for OBJ (CCW)
            lines += ['f %d/%d %d/%d %d/%d' % (a + vo, a + vo, b + vo, b + vo, c + vo, c + vo) for a, b, c in T]
            vo += len(P)
            cen = P[T].mean(1)
            keep = np.all(np.abs(cen - npc) <= radius, axis=1)
            if part['model'].startswith('m8') and base and base.lower().startswith('sky'):
                keep[:] = False  # sky dome (m8xxx, sky_* texture): kept in parts/, left out of the cropped site
            if keep.any():
                combo_v.append(P)
                combo_f.append(T[keep] + nv)
                combo_mat += [mname] * int(keep.sum())
                combo_mtd += [mat['mtd'].split('\\')[-1]] * int(keep.sum())
                nv += len(P)
            pentry['meshes'].append(dict(material=mat['name'], mtd=mat['mtd'].split('\\')[-1], verts=len(P), tris=len(T),
                                         textures=[dict(type=t['type'], path=t['path'], scale=t['scale']) for t in mat['textures']],
                                         diffuse=base))
        open(os.path.join(out, 'parts', pname + '.obj'), 'w').write('\n'.join(lines) + '\n')
        manifest['parts'].append(pentry)
    # combined, cropped
    V = np.concatenate(combo_v) if combo_v else np.zeros((0, 3))
    F = np.concatenate(combo_f) if combo_f else np.zeros((0, 3), np.int64)
    with open(os.path.join(out, 'site_cropped.obj'), 'w') as fh:
        fh.write('# %s, map space, triangles with centroid within +-%g m of NPC %s\nmtllib site.mtl\n' % (m, radius, npc.tolist()))
        for v in V:
            fh.write('v %.4f %.4f %.4f\n' % tuple(v))
        cur = None
        for (a, b, c), mn in zip(F, combo_mat):
            if mn != cur:
                fh.write('usemtl %s\n' % mn)
                cur = mn
            fh.write('f %d %d %d\n' % (a + 1, b + 1, c + 1))
    with open(os.path.join(out, 'site.mtl'), 'w') as fh:
        for mn, ti in mats_used.items():
            rgb = ti['mean_rgb'] if ti and 'mean_rgb' in ti else (128, 128, 128)
            fh.write('newmtl %s\nKd %.4f %.4f %.4f\n' % ((mn,) + tuple(c / 255 for c in rgb)))
            if ti and 'mean_rgb' in ti:
                fh.write('map_Kd tex/%s.png\n' % mn)
    manifest['textures'] = texinfo
    json.dump(manifest, open(os.path.join(out, 'manifest.json'), 'w'), indent=1, default=float)
    np.savez_compressed(os.path.join(out, 'site_cropped.npz'), V=V, F=F, mat=np.array(combo_mat), mtd=np.array(combo_mtd),
                        colors=np.array([[k] + [str(c) for c in (v['mean_rgb'] if v and 'mean_rgb' in v else (128, 128, 128))]
                                         for k, v in texinfo.items()]))
    with open(os.path.join(out, 'colors.tsv'), 'w') as fh:
        fh.write('texture\tR\tG\tB\tmean_alpha\tsource\n')
        for k, ti in sorted(texinfo.items()):
            if ti and 'mean_rgb' in ti:
                fh.write('%s\t%d\t%d\t%d\t%.3f\t%s\n' % ((k,) + tuple(ti['mean_rgb']) + (ti['mean_alpha'], ti['source'])))
            else:
                fh.write('%s\t\t\t\t\t%s\n' % (k, 'MISSING' if ti is None else ti))
    print(key, m, 'parts', len(sel), 'tris cropped', len(F), 'textures', len(texinfo),
          'missing', [k for k, v in texinfo.items() if not v or 'mean_rgb' not in v])
    return manifest


if __name__ == '__main__':
    a = sys.argv
    main(a[1], a[2], [float(x) for x in a[3:6]], float(a[6]) if len(a) > 6 else 30.0)
