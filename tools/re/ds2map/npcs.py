"""Covenant NPCs: talk group id (talk_<map>.esd top group = npcMenu.fmg id) -> GeneratorRegistParam rows id//10000 == talk id
(+0x00 EnemyParam, +0x04/+0x08 AI ESD; the Rat King is matched by +0x04 == talk*100+10 = 756010) -> GeneratorParam rows with +0x08 == regist id -> GeneratorLocation (same row id):
+0x00 f32[3] position, +0x0c f32[3] rotation (degrees), +0x18 i32, +0x1c i32 (undecoded)."""
import struct, glob, os, json
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import ds2paths  # noqa: E402
MP = os.path.join(ds2paths.EXTRACTED, 'mapparam')
def load(path):
    d = open(path, 'rb').read(); n, = struct.unpack_from('<H', d, 0xa)
    rows = [struct.unpack_from('<QQ', d, 0x40 + i * 0x18) for i in range(n)]
    offs = sorted(o for _, o in rows); size = offs[1] - offs[0] if n > 1 else len(d) - offs[0]
    return {i: d[o:o + size] for i, o in rows}
COV = {'Blue Sentinels (2)': (7850, ['m10_31_00_00']), 'Brotherhood of Blood (3)': (7830, ['m10_23_00_00']),
       'Way of Blue (4)': (7410, ['m10_04_00_00']), 'Rat King (5)': (7560, ['m10_33_00_00', 'm10_34_00_00']),
       'Bell Keeper (6)': (7530, ['m10_16_00_00', 'm10_19_00_00']), 'Dragon Remnants (7)': (7720, ['m10_19_00_00'])}
EP = None
if __name__ == '__main__':
    import sys; sys.path.insert(0, r'D:\Proyectos\Minecraft\Dark Souls Mod\tools\re'); import params
    EP = params.load('EnemyParam')
    out = {}
    for cov, (talk, maps) in COV.items():
        # every map that has a generator for this talk id
        for f in sorted(glob.glob(MP + r'\GeneratorRegistParam_*.param')):
            m = os.path.basename(f)[21:-6]
            reg = {k: struct.unpack_from('<4i', v) for k, v in load(f).items() if k // 10000 == talk or struct.unpack_from('<i', v, 4)[0] == talk * 100 + 10}
            if not reg: continue
            gp = load(MP + r'\GeneratorParam_%s.param' % m); gl = load(MP + r'\GeneratorLocation_%s.param' % m)
            for gid, row in gp.items():
                rid, = struct.unpack_from('<i', row, 8)
                if rid in reg:
                    pos = struct.unpack_from('<3f', gl[gid], 0); rot = struct.unpack_from('<3f', gl[gid], 12)
                    tail = struct.unpack_from('<2i', gl[gid], 24)
                    ep = reg[rid][0]; model = struct.unpack_from('<i', EP[ep], 0)[0] if ep in EP else None
                    print('%-26s talk %d %s gen %d regist %d %s EP model %s pos %s rot %s tail %s' % (cov, talk, m, gid, rid, reg[rid], model,
                          tuple(round(x, 3) for x in pos), tuple(round(x, 3) for x in rot), tail))
                    out.setdefault(cov, []).append(dict(talk=talk, map=m, generator=gid, regist=rid, regist_row=reg[rid], ep_model=model, pos=pos, rot=rot, tail=tail))
    json.dump(out, open(r'D:\Reversing\DS2\out\covenant_maps\npcs.json', 'w'), indent=1)
