"""Adds to data/soulsmode/ds2/weapons.json what the spell bullet origin needs (docs/research/dummypoly.md):

- castAnimations[tae].bulletBones: for each distinct TAE 2300 time, the animated R_Weapon bone of c0001 at that time
  (model space of the character, axes left / up / forward in metres; 'rot' maps the bone's local rig axes to them);
- weapons[catalyst].dummyPolys: the catalyst's dummy polys 200 / 1 in the R_Weapon frame (local rig axes, metres),
  from dummypoly.md (the weapon model's dummy placed through its own dummy 1 on c0001's dummy 20).

The origin is bone position + rot x dummy (FUN_1403441f0 / chr vt+0x218).

Also what the melee hit volume needs (docs/research/wall_bounce.md, section 4):
- weapons[w].hitDummies: the weapon-model dummies its hits use (PDP +0x31 with +0x30 = 1), same frame and composition;
- bodyHitDummies: the c0001 dummies used with PDP +0x30 = 0 (the plunge: dummy 9), in their parent bone's local rig axes;
- animations[tae].weaponBones: R_Weapon sampled every 1/30 s from the first hit start to the last hit end (no exported
  attack animation is left-authored, so L_Weapon is never used), same convention as bulletBones.
A capsule is pos + rot x dummy.pos with axis rot x dummy.fwd x length (the forward axis is read off the models: the
blade and the tip dummies lie along it; the code's axis is not confirmed).

Usage: cast_origins.py <weapons.json>
"""
import ds2fs  # noqa: E402
import ds2paths  # noqa: E402
import glob
import json
import math
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(__file__))
import ds2_anims as A  # noqa: E402
import nsa  # noqa: E402

sys.path.insert(0, ds2paths.TOOLS)
import flver_dummy as F  # noqa: E402
import params  # noqa: E402

MODELS = ds2paths.MODELS
BODY = F.read(os.path.join(MODELS, 'model__chr__c0001', 'c0001.flv'))
STEP = 1.0 / 30

# dummypoly.md, table "En R/L_Weapon (rig)", right hand (-1): dummy 200 of each catalyst model, and dummy 1 (Lightning
# Spear's PDP +0x31) on the grip.
DUMMIES = {
    'sorcerers_staff': {'200': [-0.0247, -0.0037, 0.4453], '1': [-0.0141, -0.0036, -0.0001]},
    'clerics_chime': {'200': [-0.0088, -0.0036, -0.2276], '1': [-0.0141, -0.0036, -0.0001]},
    'pyromancy_flame': {'200': [-0.0114, 0.0064, 0.0], '1': [-0.0141, -0.0036, -0.0001]},
}
# Rig (model) axes -> left / up / forward: DS2 x = left, z = up, y = back (ds2_anims body_pos).
P = [[1, 0, 0], [0, 0, 1], [0, -1, 0]]


def _dummy(flv, ref):
    return next((d for d in flv['dummies'] if d['ref'] == ref), None)


def _frame(d):
    """FUN_140af5cd0: Z = forward, X = normalize(up x Z) (up = (0, 1, 0) without the use-up byte), Y = Z x X."""
    def norm(v):
        n = math.sqrt(sum(x * x for x in v))
        return [x / n for x in v]
    z = norm(d['fwd'])
    x = norm(A.cross(d['up'] if d['useup'] else (0.0, 1.0, 0.0), z))
    return list(d['pos']), (x, A.cross(z, x), z)


def _to(p, fr):
    o, ax = fr
    return [sum((p[i] - o[i]) * a[i] for i in range(3)) for a in ax]


def _from(c, fr):
    o, ax = fr
    return [o[i] + sum(c[k] * ax[k][i] for k in range(3)) for i in range(3)]


def _rig(v):
    """FLVER axes -> Morpheme rig axes (Y and Z swapped, dummypoly.md)."""
    return [round(v[0], 4), round(v[2], 4), round(v[1], 4)]


def weapon_model(weapon_id):
    """WP_<WeaponParam +0x04>_M (SD_ for shields, WeaponParam +0x15 = 3: chosen because the shield's WP_ file is another
    model; the code that picks the prefix was not traced)."""
    w = params.load('WeaponParam')[weapon_id]
    prefix = 'SD' if w[0x15] == 3 else 'WP'
    name = '%s_%04d_M' % (prefix, struct.unpack_from('<I', w, 4)[0])
    folder = 'shield' if prefix == 'SD' else 'weapon'
    path = os.path.join(MODELS, 'model__parts__%s__%s' % (folder, name), name + '.flv')
    if not os.path.exists(path):
        # Extracted on first use (tools/ds2_build.py); a weapon without that model file keeps None.
        try:
            ds2fs.model('gamedata:/model/parts/%s/%s.bnd' % (folder, name), 'model__parts__%s__%s' % (folder, name))
        except FileNotFoundError:
            pass
    return name, (F.read(path) if os.path.exists(path) else None)


def hit_dummies(flv, refs):
    """Each dummy in the R_Weapon frame: through the weapon's own dummy 1 onto c0001's dummy 20 (FUN_1403441f0)."""
    body = _dummy(BODY, 20)
    assert BODY['bones'][body['parent']]['name'] == 'R_Weapon'
    bfr, ofr = _frame(body), _frame(_dummy(flv, 1))
    out = {}
    for ref in sorted(refs):
        d = _dummy(flv, ref)
        if d is None:
            continue
        p = _from(_to(list(d['pos']), ofr), bfr)
        q = _from(_to([d['pos'][i] + d['fwd'][i] for i in range(3)], ofr), bfr)
        out[str(ref)] = {'pos': _rig(p), 'fwd': _rig([q[i] - p[i] for i in range(3)])}
    return out


def hand_dummies(flv, refs, body_ref):
    """hit_dummies for either hand: the weapon's dummy 1 onto c0001's dummy 20 (R_Weapon) or 21 (L_Weapon); bow
    WeaponTypeParam 361 +0x25..+0x2e = 20 / 21 with weapon dummy 1 (dummypoly.md s.3, by data). A dummy whose parent is
    not the model's root frame is moved by its parent bone's translation (the bow's 201 on Bow_NockingPoint; the bone
    rotations are ~0 and are ignored)."""
    body = _dummy(BODY, body_ref)
    bfr, ofr = _frame(body), _frame(_dummy(flv, 1))
    grip = _dummy(flv, 1)['parent']
    out = {}
    for ref in sorted(refs):
        d = _dummy(flv, ref)
        if d is None:
            continue
        base = [0.0, 0.0, 0.0]
        b = d['parent']
        while b >= 0 and b != grip:
            base = [base[i] + flv['bones'][b]['t'][i] for i in range(3)]
            b = flv['bones'][b]['parent']
        pos = [d['pos'][i] + base[i] for i in range(3)]
        p = _from(_to(pos, ofr), bfr)
        q = _from(_to([pos[i] + d['fwd'][i] for i in range(3)], ofr), bfr)
        out[str(ref)] = {'pos': _rig(p), 'fwd': _rig([q[i] - p[i] for i in range(3)])}
    return out


def volumes(slot):
    v = slot.get('hitVolume') if slot else None
    return [] if v is None else [x for x in (v if isinstance(v, list) else [v]) if x]


def weapon_bones(rig, tae, data):
    hits = data.get('hits') or []
    if not hits:
        return None
    path = A.nsa_file(int(tae))
    anim = nsa.NSA(path)
    bone = rig.index('R_Weapon')
    t0, t1 = min(h[0] for h in hits), max(h[1] for h in hits)
    out = []
    k = 0
    while t0 + k * STEP <= t1 + 1e-3:  # hit times are rounded to 1e-4
        t = t0 + k * STEP
        lp, lq = nsa.pose(rig, anim, max(0.0, min(t, anim.duration)))
        wp, wq = nsa.world(rig, lp, lq)
        out.append({'time': round(t, 4), 'pos': [round(x, 4) for x in A.mv(P, wp[bone])],
                    'rot': [[round(x, 5) for x in row] for row in A.mm(P, A.mat(wq[bone]))]})
        k += 1
    return out


def main():
    path = sys.argv[1]
    data = json.load(open(path, encoding='utf8'))
    rig = nsa.Rig()
    bone = rig.index('R_Weapon')
    for tae, cast in data['castAnimations'].items():
        times = sorted({b[0] for b in cast['bullets']})
        if not times:
            cast['bulletBones'] = []
            continue
        anim = nsa.NSA(A.nsa_file(int(tae)))
        out = []
        for t in times:
            lp, lq = nsa.pose(rig, anim, min(t, anim.duration))
            wp, wq = nsa.world(rig, lp, lq)
            out.append({'time': t, 'pos': [round(x, 4) for x in A.mv(P, wp[bone])],
                        'rot': [[round(x, 5) for x in row] for row in A.mm(P, A.mat(wq[bone]))]})
        cast['bulletBones'] = out
    for name, dummies in DUMMIES.items():
        data['weapons'][name]['dummyPolys'] = dummies
    # Bow (docs/research/bow.md s.5): dummy 200 (arrow origin, PDP +0x31) and 201 (nocking point) of WP_4200_M in the
    # R_Weapon / L_Weapon frame. bowAnimations: the clips are authored with the bow in the left hand, so the arrow time
    # is sampled on L_Weapon as authored and on R_Weapon through the mirror node (bulletBonesMirror).
    # Crossbow (docs/research/crossbow.md): the same for WP_4600_M and crossbowAnimations; the authored side follows
    # each clip's leftAuthored (a42_21 / a42_23 / a42_25 are _R, a42_22 _L).
    for block, clips_key in (('bow', 'bowAnimations'), ('crossbow', 'crossbowAnimations')):
        if clips_key not in data:
            continue
        bow = data['weapons'][data[block]['weapon']]
        _, flv = weapon_model(bow['weaponId'])
        bow['dummyPolys'] = hand_dummies(flv, (200, 201), 20)
        bow['dummyPolysLeft'] = hand_dummies(flv, (200, 201), 21)
        left = rig.index('L_Weapon')
        for tae, clip in data[clips_key].items():
            times = sorted({b[0] for b in clip['bullets']})
            anim = nsa.NSA(A.nsa_file(int(tae))) if times else None
            authored, other = (left, bone) if clip['leftAuthored'] else (bone, left)
            for key, mirror, b in (('bulletBones', False, authored), ('bulletBonesMirror', True, other)):
                out = []
                for t in times:
                    lp, lq = nsa.pose(rig, anim, min(t, anim.duration))
                    if mirror:
                        lp, lq = nsa.mirror(rig, lp, lq)
                    wp, wq = nsa.world(rig, lp, lq)
                    out.append({'time': t, 'bone': rig.names[b], 'pos': [round(x, 4) for x in A.mv(P, wp[b])],
                                'rot': [[round(x, 5) for x in row] for row in A.mm(P, A.mat(wq[b]))]})
                clip[key] = out
    body_refs = set()
    for name, w in data['weapons'].items():
        refs = set()
        for hand in w.values():
            if isinstance(hand, dict) and all(isinstance(s, dict) or s is None for s in hand.values()):
                for slot in hand.values():
                    for v in volumes(slot):
                        (refs if v['source'] == 'weapon' else body_refs).add(v['dummy'])
        if refs:
            model, flv = weapon_model(w['weaponId'])
            w['hitDummies'] = hit_dummies(flv, refs) if flv else None
            if flv is None or set(w['hitDummies']) != {str(r) for r in refs}:
                print('%s: %s lacks dummies %s' % (name, model, sorted(refs - {int(r) for r in (w['hitDummies'] or {})})),
                      file=sys.stderr)
    data['bodyHitDummies'] = {}
    for ref in sorted(body_refs):
        d = _dummy(BODY, ref)
        data['bodyHitDummies'][str(ref)] = {'bone': BODY['bones'][d['parent']]['name'], 'pos': _rig(d['pos']),
                                            'fwd': _rig(d['fwd'])}
    for tae, anim in data['animations'].items():
        if anim is not None and not anim.get('fall') and anim.get('hits'):
            anim['weaponBones'] = weapon_bones(rig, tae, anim)
    json.dump(data, open(path, 'w', encoding='utf8'), indent=1)
    print({k: v['bulletBones'][0]['pos'] for k, v in data['castAnimations'].items() if v['bulletBones']})


if __name__ == '__main__':
    main()
