"""DS2 SotFS Morpheme animation decoder (AnimSourceNSA) and c0001 rig reader.

Layout checked against the game: FUN_140617810 (TaskSampleTransformsFromAnimSourceNSA) -> FUN_140609670 reads
the header fields used here (+0x2c sample rate, +0x34/+0x38 section counts, +0x40..+0x58 channel maps, +0x60 pos
means quantisation, +0x80/+0x88 quantisation sets, +0x90 unchanging data, +0xa0 section start frames, +0xb0
section data); FUN_14065e690 / FUN_14065e890 decode the unchanging pos / tan-quarter-angle quat channels
(u16 * scale + offset, q = (2v, 1 - |v|^2) / (1 + |v|^2)); FUN_14065d370 / FUN_14065dbb0 the sampled ones.
Field names and the sampled-channel formulas follow the Morpheme 4 runtime (mrAnimSourceNSA / mrAnimSectionNSA,
github.com/LordRadai/Morpheme4), whose struct offsets match those reads. Pointers are offsets from the start of
the object that holds them.

Usage: nsa.py <file.nsa> [time]   prints the local transform of every rig bone.
"""
import ds2paths  # noqa: E402
import os
import math
import re
import struct
import sys

sys.path.insert(0, ds2paths.TOOLS)
import nmb  # noqa: E402

NMB = os.path.join(ds2paths.C0001, 'c0001.nmb')


# ---------------------------------------------------------------- quaternions (x, y, z, w)
def tqa(v):
    m2 = v[0] * v[0] + v[1] * v[1] + v[2] * v[2]
    f = 2.0 / (1.0 + m2)
    return (v[0] * f, v[1] * f, v[2] * f, (1.0 - m2) / (1.0 + m2))


def qmul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw,
            aw * bw - ax * bx - ay * by - az * bz)


def qdot(a, b):
    return sum(x * y for x, y in zip(a, b))


def qnorm(q):
    n = math.sqrt(qdot(q, q))
    return tuple(c / n for c in q)


def slerp(a, b, t):
    d = qdot(a, b)
    if d < 0:
        b = tuple(-c for c in b)
        d = -d
    if d > 0.9995:
        return qnorm(tuple(x + (y - x) * t for x, y in zip(a, b)))
    th = math.acos(d)
    s = math.sin(th)
    wa, wb = math.sin((1 - t) * th) / s, math.sin(t * th) / s
    return tuple(wa * x + wb * y for x, y in zip(a, b))


def qconj(q):
    return (-q[0], -q[1], -q[2], q[3])


def qrot(q, v):
    r = qmul(qmul(q, (v[0], v[1], v[2], 0.0)), qconj(q))
    return r[:3]


# ---------------------------------------------------------------- rig
class Rig:
    """AnimRigDef of c0001 (first rig asset): bone names, parents and bind pose."""

    def __init__(self, path=NMB):
        d = open(path, 'rb').read()
        b = [body for o, t, i, body in nmb.objects(d) if t == 1][0]
        n, = struct.unpack_from('<I', b, 0x44)
        self.parents = list(struct.unpack_from('<%di' % n, b, 0x54))
        self.names = [m.group().decode() for m in re.finditer(rb'[A-Za-z_][A-Za-z0-9_]{2,}', b[0x338:0x570])][:n]
        # Bind pose: positions then quaternions, 16-byte entries (found by the unit-length check on the quats).
        self.bind_pos = [struct.unpack_from('<3f', b, 0x604 + i * 16) for i in range(n)]
        self.bind_quat = [struct.unpack_from('<4f', b, 0x9c4 + i * 16) for i in range(n)]
        # RigToAnimMap assets (AnimToRig tables): anim channel -> rig bone.
        self.maps = []
        for o, t, i, body in nmb.objects(d):
            if t == 2:
                count, = struct.unpack_from('<I', body, 0x38)
                self.maps.append(list(struct.unpack_from('<%dH' % count, body, 0x4a)))

    def index(self, name):
        return self.names.index(name)


# ---------------------------------------------------------------- animation
class NSA:
    def __init__(self, path):
        d = self.d = open(path, 'rb').read()
        u32 = lambda o: struct.unpack_from('<I', d, o)[0]
        u64 = lambda o: struct.unpack_from('<Q', d, o)[0]
        self.duration, self.rate = struct.unpack_from('<2f', d, 0x28)
        self.num_channels, self.num_frame_sections, self.num_channel_sections = u32(0x30), u32(0x34), u32(0x38)
        self.unch_pos_map = self._map(u64(0x40))
        self.unch_quat_map = self._map(u64(0x48))
        pos_maps, quat_maps = u64(0x50), u64(0x58)
        self.pos_maps = [self._map(u64(pos_maps + 8 * k)) for k in range(self.num_channel_sections)]
        self.quat_maps = [self._map(u64(quat_maps + 8 * k)) for k in range(self.num_channel_sections)]
        self.pos_means_q = struct.unpack_from('<6f', d, 0x60)  # offset xyz, scale xyz
        n_pos_q, n_quat_q = u32(0x78), u32(0x7c)
        self.pos_q = [struct.unpack_from('<6f', d, u64(0x80) + 24 * k) for k in range(n_pos_q)]
        self.quat_q = [struct.unpack_from('<6f', d, u64(0x88) + 24 * k) for k in range(n_quat_q)]
        # Unchanging channels.
        ub = u64(0x90)
        n_up, n_uq = u32(ub), u32(ub + 4)
        upq = struct.unpack_from('<6f', d, ub + 8)
        uqq = struct.unpack_from('<6f', d, ub + 0x20)
        up_data, uq_data = ub + u64(ub + 0x38), ub + u64(ub + 0x40)
        self.unch_pos = [self._deq(struct.unpack_from('<3H', d, up_data + 6 * k), upq) for k in range(n_up)]
        self.unch_quat = [tqa(self._deq(struct.unpack_from('<3H', d, uq_data + 6 * k), uqq)) for k in range(n_uq)]
        # Sections (frame-major, channel-minor).
        starts = u64(0xa0)
        self.section_starts = [u32(starts + 4 * k) for k in range(self.num_frame_sections + 1)]
        data = u64(0xb0)
        self.sections = [[self._section(u64(data + 8 * (f * self.num_channel_sections + c)))
                          for c in range(self.num_channel_sections)] for f in range(self.num_frame_sections)]

    def _map(self, off):
        n, = struct.unpack_from('<H', self.d, off)
        return list(struct.unpack_from('<%dH' % n, self.d, off + 2))

    @staticmethod
    def _deq(v, q):
        return tuple(v[k] * q[3 + k] + q[k] for k in range(3))

    def _section(self, sb):
        d = self.d
        frames, n_pos, n_quat = struct.unpack_from('<3I', d, sb)
        p_data, p_qd, q_data, q_qd = struct.unpack_from('<4Q', d, sb + 0x10)
        return dict(frames=frames, n_pos=n_pos, n_quat=n_quat, base=sb,
                    p_data=sb + p_data, p_qd=sb + p_qd, q_data=sb + q_data, q_qd=sb + q_qd,
                    p_stride=(4 * n_pos + 3) & ~3, q_stride=(6 * n_quat + 3) & ~3)

    def sample(self, t):
        """Local (pos, quat) per anim channel at time t (seconds)."""
        pos = [None] * self.num_channels
        quat = [None] * self.num_channels
        for k, ch in enumerate(self.unch_pos_map):
            pos[ch] = self.unch_pos[k]
        for k, ch in enumerate(self.unch_quat_map):
            quat[ch] = self.unch_quat[k]
        frame_f = max(0.0, min(t, self.duration)) * self.rate
        frame = int(frame_f)
        sec = 0
        while sec + 1 < self.num_frame_sections and frame >= self.section_starts[sec + 1]:
            sec += 1
        local = frame - self.section_starts[sec]
        u = frame_f - frame
        d = self.d
        for c in range(self.num_channel_sections):
            s = self.sections[sec][c]
            if local >= s['frames'] - 1:
                local, u = s['frames'] - 2, 1.0
            mo = self.pos_means_q
            for k, ch in enumerate(self.pos_maps[c]):
                if k >= s['n_pos']:
                    break
                qm = struct.unpack_from('<6B', d, s['p_qd'] + 6 * k)
                mean = tuple(qm[i] * mo[3 + i] + mo[i] for i in range(3))
                qs = [self.pos_q[qm[3 + i]] for i in range(3)]
                ka = struct.unpack_from('<I', d, s['p_data'] + local * s['p_stride'] + 4 * k)[0]
                kb = struct.unpack_from('<I', d, s['p_data'] + (local + 1) * s['p_stride'] + 4 * k)[0]
                va = ((ka >> 21) & 0x7ff, (ka >> 10) & 0x7ff, ka & 0x3ff)
                vb = ((kb >> 21) & 0x7ff, (kb >> 10) & 0x7ff, kb & 0x3ff)
                pos[ch] = tuple((va[i] + (vb[i] - va[i]) * u) * qs[i][3 + i] + qs[i][i] + mean[i] for i in range(3))
            for k, ch in enumerate(self.quat_maps[c]):
                if k >= s['n_quat']:
                    break
                qm = struct.unpack_from('<6B', d, s['q_qd'] + 6 * k)
                qbar = tqa(tuple(qm[i] * (2.0 / 255) - 1.0 for i in range(3)))
                qs = [self.quat_q[qm[3 + i]] for i in range(3)]
                va = struct.unpack_from('<3H', d, s['q_data'] + local * s['q_stride'] + 6 * k)
                vb = struct.unpack_from('<3H', d, s['q_data'] + (local + 1) * s['q_stride'] + 6 * k)
                qa = tqa(tuple(va[i] * qs[i][3 + i] + qs[i][i] for i in range(3)))
                qb = tqa(tuple(vb[i] * qs[i][3 + i] + qs[i][i] for i in range(3)))
                quat[ch] = qmul(qbar, slerp(qa, qb, u))
        return pos, quat


def trajectory(path):
    """Root motion positions of an .nsa (docs/research/equipload.md s.6, confirmed in code: AnimSourceNSA locate
    FUN_140609b30 -> TrajectorySourceNSA::locate FUN_14065ed10, sampler FUN_14065ebc0 / FUN_14065edc0): +0xb8 ->
    block: +0x20 f32 sample rate, +0x24 u32 samples, +0x28 3f offset, +0x34 3f scale, +0x58 u64 key offset (from the
    block, 0 = constant at the offset); key u32 = 11 / 11 / 10 bit fields (X >> 21, Y >> 10, Z) x scale + offset.
    The rotation channel (+0x40 / +0x4c / +0x60, 3 x u16 tan-quarter-angle keys) is not read here: callers check it
    is constant. Model space: X left, Y back, Z up (metres). Returns (rate, [(x, y, z) per sample])."""
    d = open(path, 'rb').read()
    o, = struct.unpack_from('<Q', d, 0xb8)
    rate, = struct.unpack_from('<f', d, o + 0x20)
    n, = struct.unpack_from('<I', d, o + 0x24)
    off = struct.unpack_from('<3f', d, o + 0x28)
    sc = struct.unpack_from('<3f', d, o + 0x34)
    kp, = struct.unpack_from('<Q', d, o + 0x58)
    keys = []
    for i in range(n):
        if kp == 0:
            keys.append(tuple(round(x, 4) for x in off))
            continue
        k, = struct.unpack_from('<I', d, o + kp + 4 * i)
        v = ((k >> 21) & 0x7ff, (k >> 10) & 0x7ff, k & 0x3ff)
        keys.append(tuple(round(v[j] * sc[j] + off[j], 4) for j in range(3)))
    return rate, keys


def pose(rig, anim, t, rig_map=None):
    """Local (pos, quat) per rig bone: animated channels over the bind pose."""
    if rig_map is None:
        rig_map = next(m for m in rig.maps if len(m) == anim.num_channels)
    pos, quat = anim.sample(t)
    lp, lq = list(rig.bind_pos), list(rig.bind_quat)
    for ch, bone in enumerate(rig_map):
        if pos[ch] is not None:
            lp[bone] = pos[ch]
        if quat[ch] is not None:
            lq[bone] = quat[ch]
    return lp, lq


def world(rig, lp, lq):
    """Model-space (pos, quat) per bone."""
    wp, wq = [None] * len(lp), [None] * len(lq)
    for i, parent in enumerate(rig.parents):
        if parent < 0:
            wp[i], wq[i] = lp[i], lq[i]
        else:
            wq[i] = qmul(wq[parent], lq[i])
            r = qrot(wq[parent], lp[i])
            wp[i] = tuple(wp[parent][k] + r[k] for k in range(3))
    return wp, wq


def mirror(rig, lp, lq, B=0xb6c30):
    """Morpheme's mirror node (TaskMirrorTransforms, FUN_1406140a0) on a local pose, with c0001.nmb's mirror map
    (node 0 attribute 0x42 at 0xb6c30: axis X, 25 L/R pairs and 9 invariant bones, per-bone offsets); per bone b with
    partner m(b): p' = rot(conj(O_parent), S(p_m)), q' = conj(O_parent) refl(q_m) O_b (docs/research/animations.md,
    "Espejo de Morpheme")."""
    d = open(NMB, 'rb').read()
    ident = (0.0, 0.0, 0.0, 1.0)
    n, = struct.unpack_from('<I', d, B + 0x14)
    m = list(range(len(rig.names)))
    off = [ident] * len(rig.names)
    for k in range(n):
        e = B + 0x60 + k * 0x30
        left, right = struct.unpack_from('<II', d, e + 0x20)
        m[left], m[right] = right, left
        off[right] = struct.unpack_from('<4f', d, e)
        off[left] = struct.unpack_from('<4f', d, e + 16)
    ninv, = struct.unpack_from('<I', d, B + 0x40)
    for k in range(ninv):
        off[struct.unpack_from('<I', d, B + 0x5a0 + 4 * k)[0]] = struct.unpack_from('<4f', d, B + 0x510 + 16 * k)
    op, oq = [], []
    for b, p in enumerate(rig.parents):
        s = m[b]
        c = qconj(off[p]) if p > 0 else ident
        x, y, z = lp[s]
        qx, qy, qz, qw = lq[s]
        op.append(qrot(c, (-x, y, z)))
        oq.append(qmul(qmul(c, (qx, -qy, -qz, qw)), off[b]))
    return op, oq


if __name__ == '__main__':
    rig = Rig()
    anim = NSA(sys.argv[1])
    t = float(sys.argv[2]) if len(sys.argv) > 2 else 0.0
    print('duration', anim.duration, 'rate', anim.rate, 'channels', anim.num_channels,
          'sections', anim.section_starts, 'unchanging pos/quat', len(anim.unch_pos), len(anim.unch_quat))
    lp, lq = pose(rig, anim, t)
    wp, wq = world(rig, lp, lq)
    for i, n in enumerate(rig.names):
        if 'Finger' not in n:
            print('%2d %-14s local %s  world %s' % (i, n, [round(x, 3) for x in lp[i]], [round(x, 3) for x in wp[i]]))
