"""Morpheme NMB (c0001.nmb) helpers. Objects: 0x30-byte header (u32 0x18, u32 10, u32 type, guid[16],
u32 id, u64 size, u32 align, u32 ?) followed by the body; types 3/4 = event tracks, 10 = network def,
13 = file name lookup table (four string tables of 994 entries)."""
import re, struct

def objects(d):
    for m in re.finditer(rb'\x18\x00\x00\x00\x0a\x00\x00\x00', d):
        o = m.start()
        typ, = struct.unpack_from('<I', d, o + 8)
        tid, = struct.unpack_from('<I', d, o + 0x1c)
        size, = struct.unpack_from('<Q', d, o + 0x20)
        align, = struct.unpack_from('<I', d, o + 0x28)
        if align not in (4, 16) or size > len(d) or typ > 64:
            continue  # the pattern also occurs inside object bodies
        yield o, typ, tid, d[o + 0x30:o + 0x30 + size]

def string_table(b, off):
    count, size, ids, offs, strs = struct.unpack_from('<IIQQQ', b, off)
    out = []
    for i in range(count):
        so, = struct.unpack_from('<I', b, off + offs + i * 4)
        s = b[off + strs + so: b.index(b'\0', off + strs + so)].decode('latin1')
        out.append(s)
    return out

def lookup_tables(d):
    for o, typ, tid, b in objects(d):
        if typ == 13:
            return [string_table(b, struct.unpack_from('<Q', b, k * 8)[0]) for k in range(4)]

def event_track(b):
    n, kind, noff, channel, track_id, eoff = struct.unpack_from('<IIQIIQ', b, 0)
    if n > 4096 or noff >= len(b):
        return None
    name = b[noff:b.index(b'\0', noff)].decode()
    events = [struct.unpack_from('<ffI', b, eoff + i * 12) for i in range(n)]
    return name, kind, channel, track_id, events

ATTRIB_HEAD = rb'\xff\xff(.)\x00\xcd\xcd\xcd\xcd'

def track_set(nd, q):
    """Attrib type 0x17 (event track set) whose 'ffff' header is at q; returns (discrete, curve, duration)
    lists of track asset ids (first 4 bytes of each track object's GUID)."""
    base = q - 8
    groups = []
    for g in range(3):
        count, ids_off = struct.unpack_from('<I4xQ', nd, base + 0x10 + g * 0x18)
        if count > 256 or ids_off > 0x10000:
            return None
        groups.append([nd[base + ids_off + k * 8: base + ids_off + k * 8 + 4] for k in range(count)])
    return groups

def anim_nodes(d):
    """Yields (anim index, [(track name, channel, events)...]) for every source-anim attrib (0x15)
    paired with the event track set (0x17) that follows it."""
    objs = list(objects(d))
    nd = [b for o, t, i, b in objs if t == 10][0]
    tracks = {d[o + 0xc:o + 0x10]: event_track(b) for o, t, i, b in objs if t in (3, 4)}
    tracks = {k: v for k, v in tracks.items() if v is not None}
    heads = [(m.start(), m.group(1)[0]) for m in re.finditer(ATTRIB_HEAD, nd, re.S)]
    for idx, (p, t) in enumerate(heads):
        if t != 0x15:
            continue
        anim, = struct.unpack_from('<I', nd, p + 0x6c)
        nxt = next((q for q, u in heads[idx + 1:idx + 4] if u == 0x17), None)
        out = []
        groups = track_set(nd, nxt) if nxt is not None else None
        if groups:
            for group in groups:
                for tid in group:
                    if tid not in tracks:
                        out.append(('?' + tid.hex(), -1, []))
                        continue
                    name, kind, channel, track_id, events = tracks[tid]
                    out.append((name, channel, events))
        yield p, anim, out
