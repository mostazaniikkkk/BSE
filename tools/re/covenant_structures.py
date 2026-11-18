"""The covenant structures (docs/research/covenants.md "Diseño en BSE"): each covenant's joining place in DS2, from the
original map models, turned into a Minecraft structure (the user's request, 2026-11-10).

For each site: the joining NPC's GeneratorLocation (mapparam; map-local + the map's collision offset), the MSB map
pieces and objects within RADIUS m of it with their FLVER meshes and textures (ds2map/covsite.py), then:
  - surface voxelization at 1 m = 1 block (DS2 metres = blocks, as everywhere in the mod), sampling every triangle;
  - each block's material = the texture covering most of it, its colour = the texture's mean colour, mapped to the
    nearest Minecraft building block (palette = mean colour of the vanilla textures in the client jar);
  - MTD filter: no distant scenery (NoLit, enkei), decals, sky, alpha-cut cutouts; water -> water, magma -> lava;
  - Z mirrored (DS2's map space is left-handed: clockwise front faces, ds2map/flver2.py; Minecraft is right-handed);
  - air is written only where a block has geometry both above and below it (rooms), so the terrain is not carved
    around the building (design choice);
  - the covenant's altar block at the NPC's feet.
Output: data/soulsmode/structure/covenant/<key>.nbt (DS2-derived, gitignored) and a preview PNG in the staging folder.

Usage: covenant_structures.py [site key ...]      (env DS2_WORK; MINECRAFT_JAR as titanite_block.py)
"""
import gzip
import io
import json
import os
import struct
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ds2map'))
import covsite  # noqa: E402
import ds2paths  # noqa: E402
import npcs  # noqa: E402

RADIUS = 30.0
STEP = 0.35   # sampling step on each triangle, in metres (below half a block so no block is skipped)
DATA_VERSION = 3955  # Minecraft 1.21.1
# (key, covenant id, map, generator row of the joining NPC) — ds2map/npcs.py, covenants.md.
SITES = [
    ('blue_sentinels', 2, 'm10_31_00_00', 315),
    ('brotherhood_of_blood', 3, 'm10_23_00_00', 275),
    ('way_of_blue', 4, 'm10_04_00_00', 100),
    ('rat_king_m10_33', 5, 'm10_33_00_00', 285),
    ('rat_king_m10_34', 5, 'm10_34_00_00', 285),
    ('bell_keeper_m10_16', 6, 'm10_16_00_00', 95),
    ('bell_keeper_m10_19', 6, 'm10_19_00_00', 95),
    ('dragon_remnants', 7, 'm10_19_00_00', 175),
]
# BSE builds only these (the user, 2026-11-10: Way of Blue's site is the approved small totem; the other sites and
# covenants are DarkTwo's, which can run this tool with their keys and DS2_RESOURCES pointing at its pack).
BSE_SITES = ['way_of_blue']
ALTAR = {2: 'soulsmode:blue_sentinels_altar', 3: 'soulsmode:brotherhood_of_blood_altar', 4: 'soulsmode:way_of_blue_altar',
         5: 'soulsmode:rat_king_altar', 6: 'soulsmode:bell_keeper_altar', 7: 'soulsmode:dragon_remnants_altar'}
JAR = os.environ.get('MINECRAFT_JAR') or os.path.join(os.path.expanduser('~'), '.gradle', 'caches', 'fabric-loom', '1.21.1',
                                                      'minecraft-client.jar')
# Building blocks offered to the colour match: block id -> texture (all full cubes, same texture on every side or
# a representative side).
PALETTE = {
    'stone': 'stone', 'cobblestone': 'cobblestone', 'mossy_cobblestone': 'mossy_cobblestone',
    'stone_bricks': 'stone_bricks', 'mossy_stone_bricks': 'mossy_stone_bricks', 'cracked_stone_bricks': 'cracked_stone_bricks',
    'smooth_stone': 'smooth_stone', 'andesite': 'andesite', 'polished_andesite': 'polished_andesite',
    'diorite': 'diorite', 'polished_diorite': 'polished_diorite', 'granite': 'granite', 'polished_granite': 'polished_granite',
    'calcite': 'calcite', 'tuff': 'tuff', 'tuff_bricks': 'tuff_bricks', 'polished_tuff': 'polished_tuff',
    'deepslate': 'deepslate', 'cobbled_deepslate': 'cobbled_deepslate', 'polished_deepslate': 'polished_deepslate',
    'deepslate_bricks': 'deepslate_bricks', 'deepslate_tiles': 'deepslate_tiles', 'blackstone': 'blackstone',
    'polished_blackstone_bricks': 'polished_blackstone_bricks', 'gravel': 'gravel', 'dirt': 'dirt',
    'coarse_dirt': 'coarse_dirt', 'packed_mud': 'packed_mud', 'mud_bricks': 'mud_bricks', 'bricks': 'bricks',
    'sandstone': 'sandstone', 'smooth_sandstone': 'sandstone_top', 'red_sandstone': 'red_sandstone',
    'dripstone_block': 'dripstone_block', 'moss_block': 'moss_block', 'terracotta': 'terracotta',
    'white_terracotta': 'white_terracotta', 'light_gray_terracotta': 'light_gray_terracotta',
    'gray_terracotta': 'gray_terracotta', 'black_terracotta': 'black_terracotta', 'brown_terracotta': 'brown_terracotta',
    'red_terracotta': 'red_terracotta', 'orange_terracotta': 'orange_terracotta', 'yellow_terracotta': 'yellow_terracotta',
    'green_terracotta': 'green_terracotta', 'cyan_terracotta': 'cyan_terracotta', 'blue_terracotta': 'blue_terracotta',
    'light_blue_terracotta': 'light_blue_terracotta', 'purple_terracotta': 'purple_terracotta',
    'oak_planks': 'oak_planks', 'spruce_planks': 'spruce_planks', 'dark_oak_planks': 'dark_oak_planks',
    'birch_planks': 'birch_planks', 'oak_log': 'oak_log', 'spruce_log': 'spruce_log', 'dark_oak_log': 'dark_oak_log',
    'iron_block': 'iron_block', 'quartz_block': 'quartz_block_side', 'basalt': 'basalt_side',
    'smooth_basalt': 'smooth_basalt', 'netherrack': 'netherrack', 'nether_bricks': 'nether_bricks',
}


def skip(mtd, tex):
    t = tex.lower()
    return ('nolit' in mtd.lower() or '[decal]' in mtd.lower() or '_alp_1bit' in mtd.lower() or 'grass' in mtd.lower()
            or t.startswith('enkei') or t.startswith('sky') or t == 'none')


def special(mtd, tex):
    if 'water' in mtd.lower() or tex.lower().startswith('water'):
        return 'minecraft:water'
    if 'magma' in mtd.lower():
        return 'minecraft:lava'
    return None


def palette():
    out = {}
    with zipfile.ZipFile(JAR) as z:
        for block, texture in PALETTE.items():
            im = Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/%s.png' % texture))).convert('RGB')
            out['minecraft:' + block] = np.asarray(im, float).reshape(-1, 3).mean(0)
    return out


def nearest(rgb, pal):
    names = list(pal)
    cols = np.array([pal[n] for n in names])
    # Weighted RGB distance (red 2, green 4, blue 3), a cheap perceptual approximation.
    d = ((cols - np.array(rgb, float)) ** 2 * np.array([2, 4, 3])).sum(1)
    return names[int(d.argmin())]


def samples(V, F):
    """Points on every triangle, at most STEP apart; returns (points, triangle index)."""
    a, b, c = V[F[:, 0]], V[F[:, 1]], V[F[:, 2]]
    edge = np.maximum(np.maximum(np.linalg.norm(b - a, axis=1), np.linalg.norm(c - b, axis=1)), np.linalg.norm(a - c, axis=1))
    n = np.maximum(1, np.ceil(edge / STEP)).astype(int)
    pts, idx = [], []
    for k in np.unique(n):
        sel = np.nonzero(n == k)[0]
        i, j = np.meshgrid(np.arange(k + 1), np.arange(k + 1), indexing='ij')
        m = (i + j) <= k
        u, v = (i[m] / k), (j[m] / k)
        w = 1 - u - v
        p = (a[sel, None, :] * w[None, :, None] + b[sel, None, :] * u[None, :, None] + c[sel, None, :] * v[None, :, None])
        pts.append(p.reshape(-1, 3))
        idx.append(np.repeat(sel, len(u)))
    return np.concatenate(pts), np.concatenate(idx)


# ---- NBT (big-endian, gzip): just what a structure template needs.
def _s(x):
    b = x.encode('utf8')
    return struct.pack('>H', len(b)) + b


def nbt(tag, value):
    if tag == 'int':
        return struct.pack('>i', value)
    if tag == 'string':
        return _s(value)
    if tag == 'list_int':
        return b'\x03' + struct.pack('>i', len(value)) + b''.join(struct.pack('>i', v) for v in value)
    if tag == 'list_compound':
        return b'\x0a' + struct.pack('>i', len(value)) + b''.join(compound(v) for v in value)
    raise ValueError(tag)


IDS = {'int': 3, 'string': 8, 'list_int': 9, 'list_compound': 9, 'compound': 10}


def compound(fields):
    out = b''
    for name, (tag, value) in fields.items():
        out += bytes([IDS[tag]]) + _s(name) + (compound(value) if tag == 'compound' else nbt(tag, value))
    return out + b'\x00'


def write_structure(path, size, blocks):
    states = sorted({s for _, s in blocks})
    index = {s: i for i, s in enumerate(states)}
    palette_nbt = []
    for s in states:
        name, props = (s.split('[', 1) + [''])[:2]
        entry = {'Name': ('string', name)}
        if props:
            entry['Properties'] = ('compound', {k: ('string', v) for k, v in
                                                (kv.split('=') for kv in props.rstrip(']').split(','))})
        palette_nbt.append(entry)
    root = {'DataVersion': ('int', DATA_VERSION), 'size': ('list_int', list(size)), 'palette': ('list_compound', palette_nbt),
            'blocks': ('list_compound', [{'pos': ('list_int', list(p)), 'state': ('int', index[s])} for p, s in blocks]),
            'entities': ('list_compound', [])}
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as f:
        f.write(b'\x0a' + _s('') + compound(root))


def preview(path, solid, colors):
    """An isometric view of the blocks (painter's order), for checking by eye."""
    xs, ys, zs = np.nonzero(solid)
    s = 6
    w = (solid.shape[0] + solid.shape[2]) * s + 20
    h = (solid.shape[0] + solid.shape[2]) * s // 2 + solid.shape[1] * s + 20
    im = Image.new('RGB', (w, h), (30, 32, 40))
    d = ImageDraw.Draw(im)
    order = np.lexsort((-zs, ys, xs))  # far to near
    ox = solid.shape[2] * s + 10
    for k in order[::-1]:
        x, y, z = xs[k], ys[k], zs[k]
        cx = ox + (x - z) * s
        cy = 10 + (x + z) * s // 2 + (solid.shape[1] - y) * s
        col = tuple(int(v) for v in colors[x, y, z])
        top = [(cx, cy - s // 2), (cx + s, cy), (cx, cy + s // 2), (cx - s, cy)]
        d.polygon(top, fill=col)
        d.polygon([(cx - s, cy), (cx, cy + s // 2), (cx, cy + s // 2 + s), (cx - s, cy + s)], fill=tuple(int(v * 0.7) for v in col))
        d.polygon([(cx + s, cy), (cx, cy + s // 2), (cx, cy + s // 2 + s), (cx + s, cy + s)], fill=tuple(int(v * 0.5) for v in col))
    im.save(path)


def site(key, cov, m, gen, pal, block_colors):
    gl = npcs.load(os.path.join(npcs.MP, 'GeneratorLocation_%s.param' % m))
    local = struct.unpack_from('<3f', gl[gen], 0)
    out = os.path.join(covsite.ROOT, key)
    npz = os.path.join(out, 'site_cropped.npz')
    if not os.path.exists(npz):
        covsite.main(key, m, list(local), RADIUS)
    d = np.load(npz)
    manifest = json.load(open(os.path.join(out, 'manifest.json')))
    npc = np.array(manifest['npc'])
    colors = {row[0]: [float(v) for v in row[1:]] for row in d['colors']}
    V = d['V'] - npc
    V[:, 2] = -V[:, 2]  # left-handed DS2 -> right-handed Minecraft
    F, tex, mtd = d['F'], d['mat'], d['mtd']
    keep = np.array([not skip(a, b) for a, b in zip(mtd, tex)])
    F, tex, mtd = F[keep], tex[keep], mtd[keep]
    P, tri = samples(V, F)
    vox = np.floor(P).astype(int)
    lo = np.array([-int(RADIUS), int(np.floor(V[:, 1].min())) if len(V) else 0, -int(RADIUS)])
    lo[1] = max(lo[1], -int(RADIUS))
    vox -= lo
    size = np.array([2 * int(RADIUS) + 1, 2 * int(RADIUS) + 1, 2 * int(RADIUS) + 1])
    ok = np.all((vox >= 0) & (vox < size), axis=1)
    vox, tri = vox[ok], tri[ok]
    # Majority texture per block.
    names = np.unique(tex)
    tid = np.searchsorted(names, tex[tri])
    flat = (vox[:, 0] * size[1] + vox[:, 1]) * size[2] + vox[:, 2]
    key2 = flat * len(names) + tid
    uk, cnt = np.unique(key2, return_counts=True)
    order = np.lexsort((-cnt, uk // len(names)))
    first = np.unique(uk[order] // len(names), return_index=True)[1]
    best = uk[order][first]
    cells, tbest = best // len(names), best % len(names)
    solid = np.zeros(size, bool)
    col = np.zeros(tuple(size) + (3,))
    blocks = {}
    for c, t in zip(cells, tbest):
        x, rem = divmod(int(c), size[1] * size[2])
        y, z = divmod(rem, size[2])
        tname = names[t]
        idx = np.nonzero(tex == tname)[0][0]
        state = special(mtd[idx], tname) or nearest(colors.get(tname, (128, 128, 128)), pal)
        blocks[(x, y, z)] = state
        solid[x, y, z] = True
        col[x, y, z] = block_colors.get(state, (60, 90, 200) if state.endswith('water') else (220, 90, 20))
    # Air only inside: a block with geometry somewhere above and below it in its column.
    above = np.flip(np.maximum.accumulate(np.flip(solid, 1), 1), 1)
    below = np.maximum.accumulate(solid, 1)
    inside = ~solid & above & below
    altar = tuple(int(v) for v in (-lo))
    altar = (altar[0], altar[1], altar[2])
    for p in zip(*np.nonzero(inside)):
        blocks.setdefault(tuple(int(v) for v in p), 'minecraft:air')
    blocks[altar] = ALTAR[cov]
    solid[altar] = True
    col[altar] = (230, 200, 60)
    path = os.path.join(ds2paths.RESOURCES, 'data', 'soulsmode', 'structure', 'covenant', key + '.nbt')
    write_structure(path, size, sorted(blocks.items()))
    preview(os.path.join(out, 'voxels.png'), solid, col)
    n = sum(1 for s in blocks.values() if s != 'minecraft:air')
    print(key, 'size', size.tolist(), 'blocks', n, 'air', len(blocks) - n, 'altar at', altar)
    return {'key': key, 'covenant': cov, 'size': size.tolist(), 'altar': list(altar)}


def main():
    pal = palette()
    block_colors = {k: tuple(v) for k, v in pal.items()}
    wanted = set(sys.argv[1:]) or set(BSE_SITES)
    info = [site(k, c, m, g, pal, block_colors) for k, c, m, g in SITES if not wanted or k in wanted]
    json.dump(info, open(os.path.join(ds2paths.DATA, 'covenant_structures.json'), 'w'), indent=1)
    worldgen(info)


# Structure placement (design values, not DS2): one structure per covenant, the altar's floor on the surface.
SPACING = 64
SEPARATION = 24
COVENANT_KEYS = {2: 'blue_sentinels', 3: 'brotherhood_of_blood', 4: 'way_of_blue', 5: 'rat_king', 6: 'bell_keeper',
                 7: 'dragon_remnants'}


def worldgen(info):
    """worldgen/{template_pool,structure,structure_set}/covenant/<covenant>.json: a single-piece jigsaw structure per
    covenant whose pool holds that covenant's site(s); the start is lowered by the altar's height so the altar stands
    on the surface (WORLD_SURFACE_WG)."""
    base = os.path.join(ds2paths.RESOURCES, 'data', 'soulsmode', 'worldgen')

    def write(folder, name, data):
        path = os.path.join(base, folder, 'covenant', name + '.json')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        json.dump(data, open(path, 'w'), indent=2)

    for cov, name in COVENANT_KEYS.items():
        sites = [i for i in info if i['covenant'] == cov]
        if not sites:
            continue
        # One structure per site (each with its own altar height); the covenant's set picks one of its sites.
        for s in sites:
            write('template_pool', s['key'], {'fallback': 'minecraft:empty', 'elements': [
                {'weight': 1, 'element': {'element_type': 'minecraft:single_pool_element',
                                          'location': 'soulsmode:covenant/' + s['key'], 'processors': 'minecraft:empty',
                                          'projection': 'rigid'}}]})
            write('structure', s['key'], {
                'type': 'minecraft:jigsaw', 'biomes': '#minecraft:is_overworld', 'step': 'surface_structures',
                'terrain_adaptation': 'none', 'spawn_overrides': {}, 'start_pool': 'soulsmode:covenant/' + s['key'],
                'size': 1, 'start_height': {'absolute': -s['altar'][1]},
                'project_start_to_heightmap': 'WORLD_SURFACE_WG', 'max_distance_from_center': 80,
                'use_expansion_hack': False})
        write('structure_set', name, {
            'structures': [{'structure': 'soulsmode:covenant/' + s['key'], 'weight': 1} for s in sites],
            'placement': {'type': 'minecraft:random_spread', 'spacing': SPACING, 'separation': SEPARATION,
                          'salt': 30440000 + cov}})


if __name__ == '__main__':
    main()
