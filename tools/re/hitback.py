"""Wall bounce (HitBack) animation blend of c0001.nmb (docs/research/wall_bounce.md section 3, "Mezcla"), shared by
weapons.py (the clip names) and ds2_anims.py (the baked clips).

Network (SM_HitBack 0x1a93): DmgHitBackHold 0 -> state 0x1ab3, 1 -> 0x1abc; each a 0x7d (DmgHitBackDuration speed) over a
FeatherBlend2 (0x1ab2 / 0x1abb, CP BlendWeight_1, attrib 0x09 weights (0, 1)) of
  source 0 = sub-SM 0x1a95 / 0x1abd: per DmgHitBackEventID (switch 0x1a9a / 0x1ac2, values 280 / 281) a Blend2
             (0x1a96 / 0x1a99, 0x1abe / 0x1ac1; CP DmgHitBackDistance, attrib 0x09 weights (0, 2.5)) whose source 0 is
             a29_01 (one hand) and source 1 a29_03 (two hands), in BOTH states;
  source 1 = switch 0x1aad (state 0: a29_01) / 0x1ab4 (state 1: a29_03) on DmgHitBackEventID (280 / 281 / 286 / 287).
286 / 287 are the same branches through mirror nodes 0x87 (the mod mirrors the left hand itself).

Weights (exe): Blend2SyncEvents / FeatherBlend2 update connections (FUN_14063d320 / FUN_14063bd30, node +0x58 = 11 / 10)
map the CP with FUN_140636cc0: (cp - w0) / (w1 - w0) clamped to [0, 1]. BlendWeight_1 = CP node 0x32 default 1.0 (its
name is not in the exe, so the game does not write it). Per bone the FeatherBlend2 weight is weight * alpha (attrib 0x57,
FUN_14066d450): 1.0 on Spine and everything under it (44 bones), 0 on CharacterWorldSpaceTM / Character / Master / Root
/ Pelvis and the legs (15 bones). So: upper body = the pure clip of the state, pelvis and legs = the 1h / 2h Blend2.

Blend math (exe): TaskBlend2TransformBuffsInterpAttInterpPos (0x1b, FUN_14061b5d0 -> FUN_14066c6e0) and
TaskFeatherBlend2TransformBuffsInterpAttInterpPos (0x3b, FUN_14061e3a0 -> FUN_14066d450): positions p0 + (p1 - p0) w,
rotations by NMP fastSlerp (shortest arc by the sign of the dot; polynomial coefficients at 0x14110d290..0x14110d2d0).

Time (exe): both nodes use the sync-event queues (table 5 / 37: nodeBlend2SyncEventsQueueTimePos). The FeatherBlend2's
sync track passes through from source 1 (table 37 semantic 0xc = queuePassThroughChild1), so the clock is the pure clip;
the other clips are sampled at the same sync position (event index + fraction). Sync track (FUN_140610c40 ->
FUN_1405fd130): the discrete track at attrib 0x15 +0x84 (0 = Footsteps in the four clips), event starts inside the clip
region [+0x78, +0x7c] normalised to it, durations = gaps to the next start, last = 1 - start (byte +0x88 = 0); the region
lasts (end - start) x duration (+0x80).
"""
import ds2paths  # noqa: E402
import glob
import math
import os
import struct
import sys

sys.path.insert(0, ds2paths.TOOLS)
import nmb  # noqa: E402
import nsa  # noqa: E402

ANIM_DIR = ds2paths.C0001
NMB = os.path.join(ANIM_DIR, 'c0001.nmb')

# (DmgHitBackHold, EventID side) -> TAE of the clip; 1h = a29_01, 2h = a29_03; Upper_R = 280, Upper_L = 281.
CLIPS = {('1h', 'r'): 29010100, ('1h', 'l'): 29010110, ('2h', 'r'): 29030100, ('2h', 'l'): 29030110}
BLEND2_WEIGHTS = (0.0, 2.5)        # attrib 0x09 of 0x1a96 / 0x1a99 / 0x1abe / 0x1ac1 (and the mirrored 0x1a9d...)
FEATHER_WEIGHTS = (0.0, 1.0)       # attrib 0x09 of 0x1ab2 / 0x1abb
BLEND_WEIGHT_1 = 1.0               # CP node 0x32 default (attrib 0x03 +0x10)
FEATHER_ZERO = ('CharacterWorldSpaceTM', 'Character', 'Master', 'Root', 'Pelvis', 'L_Thigh', 'L_Calf', 'L_Foot',
                'L_Toe0', 'L_Toe0Nub', 'R_Thigh', 'R_Calf', 'R_Foot', 'R_Toe0', 'R_Toe0Nub')  # attrib 0x57 = 0; rest 1

# NMP fastSlerp coefficients (exe 0x14110d2c0 / 0x14110d2b0 / 0x14110d2d0 / 0x14110d290): C1, C3, C5, C7 = a + (b + (c +
# d x) x) x with x = |dot|.
_FS = ((1.5709943771362305, 0.5642929673194885, -0.1783657670021057, 0.04319949448108673),
       (-0.6461396217346191, 0.5945658087730408, 0.08610323816537857, -0.03465123102068901),
       (0.07949823886156082, -0.17304369807243347, 0.10792796313762665, -0.01439397782087326),
       (-0.004354103002697229, 0.014189627021551132, -0.015671897679567337, 0.005848706234246492))


def clip_name(hold, side, distance):
    return 'ds2_hitback_%s_%s_%d' % (hold, side, round(distance * 100))


def map_weight(cp, weights):
    """FUN_140636cc0."""
    w0, w1 = weights
    s = 1.0 if w1 - w0 >= 0 else -1.0
    if s * w0 >= s * cp:
        return 0.0
    if s * cp >= s * w1:
        return 1.0
    return (cp - w0) / (w1 - w0)


def fast_slerp(q0, q1, t):
    """FUN_14066c6e0 / FUN_14066d450 rotation channel: NMP fastSlerp, not renormalised."""
    d = sum(a * b for a, b in zip(q0, q1))
    if d < 0:
        q1 = tuple(-c for c in q1)
        d = -d
    c1, c3, c5, c7 = (a + (b + (c + e * d) * d) * d for a, b, c, e in _FS)
    rcp = 1.0 / (1.0 + d)
    big_t = 1.0 - t
    t2, big_t2 = t * t, big_t * big_t
    wa = (c1 + (c3 + (c5 + c7 * big_t2) * big_t2) * big_t2) * big_t * rcp
    wb = (c1 + (c3 + (c5 + c7 * t2) * t2) * t2) * t * rcp
    return tuple(wa * a + wb * b for a, b in zip(q0, q1))


def _nsa_file(tae_id):
    gg, ss, nnnn = tae_id // 1000000, tae_id // 10000 % 100, tae_id % 10000
    return glob.glob(os.path.join(ANIM_DIR, 'a%02d_%02d_%04d_*.nsa' % (gg, ss, nnnn)))[0]


def _source_anims():
    """TimeAct id -> attrib 0x15 data the sync track uses, for the four clips."""
    d = open(NMB, 'rb').read()
    nd = [b for o, t, i, b in nmb.objects(d) if t == 10][0]
    out = {}
    for p, anim, tracks in nmb.anim_nodes(d):
        tae = next((ev[0][2] for name, ch, ev in tracks if name == 'TimeAct' and ev), None)
        if tae not in CLIPS.values() or tae in out:
            continue
        start, end, duration = struct.unpack_from('<3f', nd, p + 0x78)
        sync_index, = struct.unpack_from('<i', nd, p + 0x84)
        loop_flag = nd[p + 0x88]
        name, _, events = tracks[sync_index]
        assert name == 'Footsteps' and loop_flag == 0, (tae, name, loop_flag)
        rng = end - start
        starts = [(e[0] - start) / rng for e in events if start <= e[0] <= end]
        durs = [starts[i + 1] - starts[i] for i in range(len(starts) - 1)] + [1.0 - starts[-1]]
        out[tae] = {'start': start, 'end': end, 'duration': duration, 'starts': starts, 'durs': durs}
    return out


class Bounce:
    def __init__(self, rig):
        self.rig = rig
        self.src = _source_anims()
        self.anims = {tae: nsa.NSA(_nsa_file(tae)) for tae in CLIPS.values()}
        self.alpha = [0.0 if n in FEATHER_ZERO else 1.0 for n in rig.names]
        counts = {len(s['starts']) for s in self.src.values()}
        assert counts == {4}, counts  # equal event counts: event i of one clip <-> event i of the others

    def length(self, hold, side):
        s = self.src[CLIPS[(hold, side)]]
        return (s['end'] - s['start']) * s['duration']

    def rate(self, hold, side):
        return self.anims[CLIPS[(hold, side)]].rate

    def _sample(self, tae, i, f):
        s = self.src[tae]
        x = s['starts'][i] + f * s['durs'][i]
        t = (s['start'] + (s['end'] - s['start']) * x) * s['duration']
        return nsa.pose(self.rig, self.anims[tae], min(t, self.anims[tae].duration))

    def pose(self, hold, side, distance, tau):
        """Local pose of state <hold> at time tau of the pure clip's region."""
        master = self.src[CLIPS[(hold, side)]]
        x = min(max(tau / self.length(hold, side), 0.0), 1.0)
        i = max(k for k in range(len(master['starts'])) if master['starts'][k] <= x + 1e-9)
        f = min((x - master['starts'][i]) / master['durs'][i], 1.0)
        pp, pq = self._sample(CLIPS[(hold, side)], i, f)
        ap, aq = self._sample(CLIPS[('1h', side)], i, f)
        bp, bq = self._sample(CLIPS[('2h', side)], i, f)
        w = map_weight(distance, BLEND2_WEIGHTS)
        fw = map_weight(BLEND_WEIGHT_1, FEATHER_WEIGHTS)
        lp, lq = [], []
        for b in range(len(self.rig.names)):
            mq = fast_slerp(aq[b], bq[b], w)
            mp = tuple(x0 + (x1 - x0) * w for x0, x1 in zip(ap[b], bp[b]))
            a = fw * self.alpha[b]
            lq.append(fast_slerp(mq, pq[b], a))
            lp.append(tuple(x0 + (x1 - x0) * a for x0, x1 in zip(mp, pp[b])))
        return lp, lq


def max_norm_error(bounce, hold, side, distance, steps=40):
    """Largest |1 - |q|| of the blended rotations over the bake (fastSlerp does not renormalise; sanity check)."""
    worst = 0.0
    for k in range(steps + 1):
        tau = bounce.length(hold, side) * k / steps
        _, lq = bounce.pose(hold, side, distance, tau)
        for q in lq:
            n = math.sqrt(sum(c * c for c in q))
            worst = max(worst, abs(n - 1.0))
    return worst
