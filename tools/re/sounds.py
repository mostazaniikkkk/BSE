"""DS2 SotFS sound extraction / catalogue (docs/research/sounds.md). Research tool: writes only to the staging folder
D:\\Reversing\\DS2\\extracted\\sound (never into the mod).

Where the data lives (DarkSoulsII.exe strings, MagicOrchestra.ini):
  gamedata:/sound/frpg2.fevbnd.dcx   DCX(DFLT) + BND4 with every FMOD Designer project (.fev); frpg2.fev = the
                                     common project (banks frpg2_main / frpg2_main2), 1156 events.
  gamedata:/sound/frpg2_main.bnd     DCX + BND4 with frpg2_main.fsb (FSB5, Vorbis), bank 0 of frpg2.fev.
  gamedata:/sound/frpg2_main2.bnd    idem frpg2_main2.fsb, bank 1.
  gamedata:/TimeAct/chr/c000100_snd.tae   the player's sound TimeAct (events 201 / 202; the _pl.tae has none).
  Game\\sound\\*.fsb (loose)          music / voice / map streams only (m, v, y, a ...), no player SFX.

FMOD itself is the game's own FMOD Ex 4.44.50 (Game\\fmodex64.dll + fmod_event64.dll), called through its C API:
  - event properties, user properties and the sound definitions an event starts (FMOD_EVENT_CALLBACKTYPE
    SOUNDDEF_START: param1 = sound definition name, param2 = wave index) come from fmod_event64.dll itself;
  - the sound definition -> wave table (file, bank, index in the bank, length, random weight) is read from the FEV
    LGCY chunk (layout checked against all 359 definitions / 441 waves: every wave's bank+index names the FSB sample
    of the same file name);
  - the PCM comes from fmodex64.dll decoding the FSB (Sound::readData), then ffmpeg (libvorbis) makes the mono .ogg.

Usage:
  sounds.py extract            unpack the archives above into the staging folder
  sounds.py catalog            catalog.json: every event of frpg2.fev (properties, sound defs, waves)
  sounds.py tae                tae_snd.json: every 201/202 event of c000100_snd.tae, decoded
  sounds.py decode             wav/<bank>/<sample>.wav for every sample of frpg2_main / frpg2_main2
  sounds.py ogg                ogg/<bank>/<sample>.ogg (mono, libvorbis) from the wavs
  sounds.py all                everything above
"""
import ds2paths  # noqa: E402
import ctypes as C
import wave
import json
import os
import struct
import subprocess
import sys
import zlib

sys.path.insert(0, ds2paths.TOOLS)

GAME = ds2paths.GAME
STAGE = ds2paths.SOUND
BHD = os.path.join(ds2paths.BHD, 'GameDataEbl.bhd')  # decrypted with decrypt_bhd.py
FFMPEG = ds2paths.ffmpeg()
ARCHIVES = ['gamedata:/sound/frpg2.fevbnd.dcx', 'gamedata:/sound/frpg2_main.bnd', 'gamedata:/sound/frpg2_main2.bnd',
            'gamedata:/sound/MagicOrchestra.ini']
TAES = ['gamedata:/TimeAct/chr/c000100_snd.tae', 'gamedata:/TimeAct/chr/c000100_sfx.tae']
BANKS = ['frpg2_main', 'frpg2_main2']  # bank order of the frpg2.fev LGCY header


# ---------------------------------------------------------------- archives
def undcx(d):
    """DCX(DFLT): 'DCS\\0' u32be uncompressed, u32be compressed at +0x1c; zlib stream at +0x4c."""
    if d[:4] != b'DCX\0':
        return d
    _, csize = struct.unpack_from('>II', d, 0x1c)
    return zlib.decompress(d[0x4c:0x4c + csize])


def unbnd4(d):
    """BND4 with 0x24-byte entries (same layout as D:\\Reversing\\DS2\\tools\\unpack_bnd4_any.py)."""
    assert d[:4] == b'BND4'
    n, = struct.unpack_from('<I', d, 0xc)
    es, = struct.unpack_from('<q', d, 0x20)
    assert es == 0x24, es
    uni = d[0x30] == 1
    out = {}
    for i in range(n):
        size, usize, off, fid, noff = struct.unpack_from('<qqIiI', d, 0x40 + i * es + 8)
        if uni:
            end = noff
            while d[end:end + 2] != b'\0\0':
                end += 2
            name = d[noff:end].decode('utf-16-le')
        else:
            name = d[noff:d.index(b'\0', noff)].decode('shift_jis')
        out[name.replace('\\', '/').split('/')[-1]] = d[off:off + size]
    return out


def extract():
    from extract_bdt import Archive
    arc = Archive(BHD, os.path.join(GAME, 'GameDataEbl.bdt'))
    for path in ARCHIVES:
        data = arc.read(path)
        assert data is not None, path
        base = path.split('/')[-1]
        if base.endswith('.ini'):
            open(os.path.join(STAGE, base), 'wb').write(data)
            continue
        folder = os.path.join(STAGE, 'bnd', base.split('.')[0])
        os.makedirs(folder, exist_ok=True)
        for name, blob in unbnd4(undcx(data)).items():
            open(os.path.join(folder, name), 'wb').write(blob)
            print(f'{len(blob):9} {path} -> {name}')
    os.makedirs(os.path.join(STAGE, 'tae'), exist_ok=True)
    for path in TAES:
        data = arc.read(path)
        assert data is not None, path
        open(os.path.join(STAGE, 'tae', path.split('/')[-1]), 'wb').write(data)
        print(f'{len(data):9} {path}')


def fev_path():
    return os.path.join(STAGE, 'bnd', 'frpg2', 'frpg2.fev')


def fsb_path(bank):
    return os.path.join(STAGE, 'bnd', bank, bank + '.fsb')


# ---------------------------------------------------------------- FSB5 / FEV
def fsb5_names(path):
    """FSB5 v1: header 0x3c (magic, version, numSamples, sampleHeadersSize, nameTableSize, dataSize, mode, ...);
    sample headers are u64 bit fields + chunks; name table = u32 offsets then C strings. Returns
    [(name, frequency index, channels, numSamples)]; mode 15 = Vorbis."""
    d = open(path, 'rb').read()
    magic, ver, n, shs, nts, ds, mode = struct.unpack_from('<4s6I', d, 0)
    assert magic == b'FSB5' and ver == 1
    p, samples = 0x3c, []
    for _ in range(n):
        raw, = struct.unpack_from('<Q', d, p)
        p += 8
        nxt = raw & 1
        while nxt:
            c, = struct.unpack_from('<I', d, p)
            nxt = c & 1
            p += 4 + ((c >> 1) & 0xffffff)
        samples.append([(raw >> 1) & 15, ((raw >> 5) & 1) + 1, raw >> 34])
    nt = 0x3c + shs
    for i in range(n):
        o, = struct.unpack_from('<I', d, nt + 4 * i)
        samples[i].insert(0, d[nt + o:d.index(b'\0', nt + o)].decode())
    return mode, samples


def fev_strings(d):
    """RIFF 'FEV ' / LIST 'PROJ' / 'STRR': u32 count, u32 offsets, C strings. LGCY refers to strings by index."""
    p = d.index(b'STRR')
    size, = struct.unpack_from('<I', d, p + 4)
    b = d[p + 8:p + 8 + size]
    n, = struct.unpack_from('<I', b, 0)
    offs = struct.unpack_from('<%dI' % n, b, 4)
    base = 4 + 4 * n
    return [b[base + o:b.index(b'\0', base + o)].decode('latin1') for o in offs]


def fev_sounddefs(d, strings):
    """Sound definitions in the LGCY chunk: u32 count, then per definition u32 name (string index), u32 config index,
    u32 wave count, and per wave u32 type (0 = wave table: the only type present), u32 random weight, u32 length +
    file name, u32 bank, u32 index in the bank, u32 length in ms. The table starts right after the 117 configs of
    0x4a bytes (u32 count first); the config fields are NOT decoded here (layout unconfirmed)."""
    p = d.index(b'LGCY')
    size, = struct.unpack_from('<I', d, p + 4)
    lg = d[p + 8:p + 8 + size]
    first = next(i for i, s in enumerate(strings) if s.startswith('/'))
    q = lg.index(struct.pack('<I', first))
    count, = struct.unpack_from('<I', lg, q - 4)
    defs = {}
    for _ in range(count):
        name, cfg, nw = struct.unpack_from('<III', lg, q)
        q += 12
        waves = []
        for _ in range(nw):
            typ, weight = struct.unpack_from('<II', lg, q)
            q += 8
            assert typ == 0, typ
            sl, = struct.unpack_from('<I', lg, q)
            fn = lg[q + 4:q + 4 + sl - 1].decode()
            q += 4 + sl
            bank, index, ms = struct.unpack_from('<III', lg, q)
            q += 12
            waves.append({'file': fn, 'bank': BANKS[bank], 'index': index, 'length_ms': ms, 'weight': weight})
        defs[strings[name]] = {'config': cfg, 'waves': waves}
    return defs


# ---------------------------------------------------------------- the game's FMOD
class Fmod:
    def __init__(self, media):
        os.add_dll_directory(GAME)
        self.ex = C.WinDLL(os.path.join(GAME, 'fmodex64.dll'))
        self.ev = C.WinDLL(os.path.join(GAME, 'fmod_event64.dll'))
        self.es = C.c_void_p()
        self._ok(self.ev.FMOD_EventSystem_Create(C.byref(self.es)), 'EventSystem_Create')
        self.sys = C.c_void_p()
        self._ok(self.ev.FMOD_EventSystem_GetSystemObject(self.es, C.byref(self.sys)), 'GetSystemObject')
        self._ok(self.ex.FMOD_System_SetOutput(self.sys, 4), 'SetOutput NOSOUND_NRT')
        self._ok(self.ev.FMOD_EventSystem_Init(self.es, 256, 0, None, 0), 'EventSystem_Init')
        self._ok(self.ev.FMOD_EventSystem_SetMediaPath(self.es, (media.rstrip('\\') + '\\').encode()), 'MediaPath')

    @staticmethod
    def _ok(r, what):
        if r != 0:
            raise RuntimeError(f'{what}: FMOD_RESULT {r}')

    def version(self):
        v = C.c_uint()
        self.ev.FMOD_EventSystem_GetVersion(self.es, C.byref(v))
        return v.value


# FMOD_EVENTPROPERTY order of the FMOD Ex 4.44 SDK header (fmod_event.h). Checked against the values returned for
# frpg2.fev: cone angles 360, doppler 1.0, steal priority 10000, auto-distance centre 1500 Hz, user properties at 53.
# 50 / 51 are not in that list (left unnamed).
PROPS = ['name', 'volume', 'volume_randomization', 'pitch', 'pitch_octaves', 'pitch_semitones', 'pitch_tones',
         'pitch_randomization', 'pitch_randomization_octaves', 'pitch_randomization_semitones',
         'pitch_randomization_tones', 'priority', 'max_playbacks', 'max_playbacks_behavior', 'mode',
         '3d_ignore_geometry', '3d_rolloff', '3d_min_distance', '3d_max_distance', '3d_position', '3d_cone_inside',
         '3d_cone_outside', '3d_cone_outside_volume', '3d_doppler', '3d_speaker_spread', '3d_pan_level', 'speaker_l',
         'speaker_c', 'speaker_r', 'speaker_ls', 'speaker_rs', 'speaker_lr', 'speaker_rr', 'speaker_lfe',
         'reverb_wet', 'oneshot', 'fade_in', 'fade_out', 'reverb_dry', 'time_offset', 'spawn_intensity',
         'spawn_intensity_randomization', 'wii_speakers', '3d_pos_randomization_min', '3d_pos_randomization_max',
         'event_type', 'steal_priority', 'effects_affect_reverb', 'will_terminate', '3d_auto_distance_filtering',
         'prop50', 'prop51', '3d_auto_distance_center_freq']
FLOAT_PROPS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 17, 18, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 38,
               40, 41, 43, 44, 52}


def catalog():
    d = open(fev_path(), 'rb').read()
    strings = fev_strings(d)
    defs = fev_sounddefs(d, strings)
    fsb = {b: fsb5_names(fsb_path(b))[1] for b in BANKS}
    for name, sd in defs.items():  # every wave must name the FSB sample at its bank / index
        for w in sd['waves']:
            sample = fsb[w['bank']][w['index']][0]
            assert os.path.splitext(os.path.basename(w['file']))[0] == sample, (name, w, sample)
            w['sample'] = sample
    f = Fmod(os.path.dirname(fev_path()))
    for b in BANKS:  # media path must hold the banks too
        dst = os.path.join(os.path.dirname(fev_path()), b + '.fsb')
        if not os.path.exists(dst):
            open(dst, 'wb').write(open(fsb_path(b), 'rb').read())
    ev = f.ev
    proj = C.c_void_p()
    Fmod._ok(ev.FMOD_EventSystem_Load(f.es, b'frpg2.fev', None, C.byref(proj)), 'Load')
    n = C.c_int()
    ev.FMOD_EventProject_GetNumEvents(proj, C.byref(n))
    CB = C.WINFUNCTYPE(C.c_int, C.c_void_p, C.c_int, C.c_void_p, C.c_void_p, C.c_void_p)
    started = []

    def cb(_e, typ, p1, p2, _ud):
        if typ == 1 and p1:  # FMOD_EVENT_CALLBACKTYPE_SOUNDDEF_START: (char *name, int wave index)
            started.append((C.cast(p1, C.c_char_p).value.decode('latin1'), p2 or 0))
        elif typ == 4:  # EVENTFINISHED
            started.append(None)
        return 0
    cbf = CB(cb)
    events = {}
    for pid in range(n.value):
        e = C.c_void_p()
        Fmod._ok(ev.FMOD_EventProject_GetEventByProjectID(proj, pid, 4, C.byref(e)), 'GetEvent info')  # INFOONLY
        idx, name = C.c_int(), C.c_char_p()
        ev.FMOD_Event_GetInfo(e, C.byref(idx), C.byref(name), None)
        np_ = C.c_int()
        ev.FMOD_Event_GetNumProperties(e, C.byref(np_))
        props = {}
        for k in range(np_.value):
            buf = (C.c_ubyte * 8)()
            ev.FMOD_Event_GetPropertyByIndex(e, k, buf, 0)
            raw = bytes(buf)
            if k == 0:
                continue
            if k < len(PROPS):
                props[PROPS[k]] = struct.unpack_from('<f' if k in FLOAT_PROPS else '<i', raw)[0]
            else:  # user properties: name + type from GetPropertyInfo (0 int, 1 float, 2 string)
                pi, pn, pt = C.c_int(k), C.c_char_p(), C.c_int()
                ev.FMOD_Event_GetPropertyInfo(e, C.byref(pi), C.byref(pn), C.byref(pt))
                key = pn.value.decode('latin1')
                if pt.value == 1:
                    props[key] = struct.unpack_from('<f', raw)[0]
                elif pt.value == 0:
                    props[key] = struct.unpack_from('<i', raw)[0]
                else:
                    ptr = struct.unpack_from('<Q', raw)[0]
                    props[key] = C.cast(ptr, C.c_char_p).value.decode('latin1') if ptr else None
        npar = C.c_int()
        ev.FMOD_Event_GetNumParameters(e, C.byref(npar))
        params = []
        for k in range(npar.value):
            par = C.c_void_p()
            ev.FMOD_Event_GetParameterByIndex(e, k, C.byref(par))
            pi, pn = C.c_int(), C.c_char_p()
            ev.FMOD_EventParameter_GetInfo(par, C.byref(pi), C.byref(pn))
            lo, hi = C.c_float(), C.c_float()
            ev.FMOD_EventParameter_GetRange(par, C.byref(lo), C.byref(hi))
            params.append({'name': pn.value.decode('latin1'), 'min': lo.value, 'max': hi.value})
        # Which sound definitions the event starts: run it (no output) a few times until EVENTFINISHED.
        used = []
        e2 = C.c_void_p()
        if ev.FMOD_EventProject_GetEventByProjectID(proj, pid, 0, C.byref(e2)) == 0:
            for _ in range(4):
                started.clear()
                ev.FMOD_Event_SetCallback(e2, cbf, None)
                if ev.FMOD_Event_Start(e2) != 0:
                    break
                for _ in range(600):
                    ev.FMOD_EventSystem_Update(f.es)
                    if None in started:
                        break
                ev.FMOD_Event_Stop(e2, 1)
                ev.FMOD_EventSystem_Update(f.es)
                for s in started:
                    if s and s[0] not in used:
                        used.append(s[0])
        events[name.value.decode('latin1')] = {'project_id': pid, 'properties': props, 'parameters': params,
                                               'sounddefs': used}
    out = {'fmod_version': hex(f.version()), 'banks': BANKS, 'events': events, 'sounddefs': defs,
           'samples': {b: [s[0] for s in fsb[b]] for b in BANKS}}
    json.dump(out, open(os.path.join(STAGE, 'catalog.json'), 'w'), indent=1)
    print(len(events), 'events,', len(defs), 'sound definitions ->', os.path.join(STAGE, 'catalog.json'))


# ---------------------------------------------------------------- TimeAct
def tae():
    """c000100_snd.tae, handled by ChrMorphemeTimeActTrackSoundCtrl FUN_1403278d0 (types 0xc9 / 0xca / 0xcb):
    201: +0 u32 sound id, +4 i32 variant source (1..6, below), +8 u8 'female voice' (+1000 if equip+0x2c),
         +9 u8 1 = category 's' (s%09d id + variant), +0xa i16 dummy poly (-1 = character), +0xc u8 (<1 -> follow),
         +0xd u8 (>0 -> common c0000 bank), +0xe u8 (1/2: only if chr vt+0x1b0 is false / true), +0xf u8 ground lookup,
         +0x10 u16.
    202: +0 u32 swing id (0..5), +4 u8 hand (1 = weapon -2 / chr+0x7a, 0 = weapon -1 / +0x7b), +8 i16 dummy poly,
         +0xa u8 (<1 -> follow), +0xb u8 (>0 -> common bank).
    203: +0 u32 id, +4 i32 variant source -> z%09d (not in c000100_snd.tae)."""
    import tae as T
    path = os.path.join(STAGE, 'tae', 'c000100_snd.tae')
    d, anims = T.load(path)
    out = {}
    for aid, evs in sorted(anims.items()):
        rows = []
        for typ, t0, t1, po in evs:
            p = d[po:po + 0x18]
            if typ == 201:
                sid, src = struct.unpack_from('<Ii', p, 0)
                rows.append({'type': 201, 'start': round(t0, 4), 'end': round(t1, 4), 'id': sid, 'variant': src,
                             'voice_sex': p[8], 's_category': p[9], 'dummy': struct.unpack_from('<h', p, 10)[0],
                             'b0c': p[12], 'common_bank': p[13], 'cond': p[14], 'ground': p[15],
                             'u10': struct.unpack_from('<H', p, 16)[0]})
            elif typ == 202:
                rows.append({'type': 202, 'start': round(t0, 4), 'end': round(t1, 4),
                             'swing': struct.unpack_from('<I', p, 0)[0], 'hand': p[4],
                             'dummy': struct.unpack_from('<h', p, 8)[0], 'b0a': p[10], 'common_bank': p[11]})
            else:
                rows.append({'type': typ, 'start': round(t0, 4), 'end': round(t1, 4), 'raw': p.hex()})
        out[str(aid)] = rows
    json.dump(out, open(os.path.join(STAGE, 'tae_snd.json'), 'w'), indent=0)
    print(len(out), 'animations ->', os.path.join(STAGE, 'tae_snd.json'))


# ---------------------------------------------------------------- decoding with the game's fmodex
def decode():
    f = Fmod(os.path.dirname(fev_path()))
    ex = f.ex
    manifest = {}
    for bank in BANKS:
        folder = os.path.join(STAGE, 'wav', bank)
        os.makedirs(folder, exist_ok=True)
        snd = C.c_void_p()
        Fmod._ok(ex.FMOD_System_CreateSound(f.sys, fsb_path(bank).encode(), 0x2000 | 0x40, None, C.byref(snd)),
                 'CreateSound ' + bank)  # FMOD_OPENONLY | FMOD_SOFTWARE
        n = C.c_int()
        ex.FMOD_Sound_GetNumSubSounds(snd, C.byref(n))
        used, manifest[bank] = set(), []
        for i in range(n.value):
            sub = C.c_void_p()
            Fmod._ok(ex.FMOD_Sound_GetSubSound(snd, i, C.byref(sub)), 'GetSubSound')
            nm = C.create_string_buffer(256)
            ex.FMOD_Sound_GetName(sub, nm, 256)
            typ, fmt, ch, bits = C.c_int(), C.c_int(), C.c_int(), C.c_int()
            ex.FMOD_Sound_GetFormat(sub, C.byref(typ), C.byref(fmt), C.byref(ch), C.byref(bits))
            if ch.value != 1:
                # readData of a stereo subsound crashes fmodex64 in OPENONLY mode (and loops in stream mode). Only 4
                # samples are stereo (f100000031, s000000147, indoor, outdoor) and none is used by the mod: skipped.
                manifest[bank].append({'index': i, 'name': nm.value.decode(), 'file': None, 'channels': ch.value,
                                       'skipped': 'stereo'})
                print(f'{bank} {i:3} {nm.value.decode():40} stereo: skipped')
                continue
            freq, vol, pan, pri = C.c_float(), C.c_float(), C.c_float(), C.c_int()
            ex.FMOD_Sound_GetDefaults(sub, C.byref(freq), C.byref(vol), C.byref(pan), C.byref(pri))
            length = C.c_uint()
            ex.FMOD_Sound_GetLength(sub, C.byref(length), 4)  # FMOD_TIMEUNIT_PCMBYTES
            Fmod._ok(ex.FMOD_Sound_SeekData(sub, 0), 'SeekData')  # without it readData returns 0 bytes
            buf = C.create_string_buffer(length.value)
            got = C.c_uint()
            ex.FMOD_Sound_ReadData(sub, buf, length.value, C.byref(got))
            pcm = buf.raw[:got.value]
            assert bits.value == 16, (nm.value, bits.value)  # FMOD decodes FSB Vorbis to PCM16
            rate = int(round(freq.value))
            hdr = b'RIFF' + struct.pack('<I', 36 + len(pcm)) + b'WAVEfmt ' + struct.pack(
                '<IHHIIHH', 16, 1, ch.value, rate, rate * ch.value * 2, ch.value * 2, 16) + b'data' + struct.pack(
                '<I', len(pcm))
            name = nm.value.decode()
            fn = name if name not in used else f'{name}__{i}'  # frpg2_main2 repeats some names
            used.add(name)
            open(os.path.join(folder, fn + '.wav'), 'wb').write(hdr + pcm)
            manifest[bank].append({'index': i, 'name': name, 'file': fn, 'channels': ch.value, 'rate': rate,
                                   'frames': len(pcm) // (2 * ch.value)})
            print(f'{bank} {i:3} {fn:40} {ch.value}ch {rate} Hz {len(pcm) // (2 * ch.value)} frames')
            ex.FMOD_Sound_Release(sub)
        ex.FMOD_Sound_Release(snd)
    json.dump(manifest, open(os.path.join(STAGE, 'samples.json'), 'w'), indent=1)


def ogg():
    """Minecraft plays positional sounds only from mono Ogg Vorbis: downmix (-ac 1) and encode with libvorbis
    (quality 6). Sample rate unchanged. The transcode is the only change to the data."""
    for bank in BANKS:
        src, dst = os.path.join(STAGE, 'wav', bank), os.path.join(STAGE, 'ogg', bank)
        os.makedirs(dst, exist_ok=True)
        for fn in sorted(os.listdir(src)):
            out = os.path.join(dst, fn[:-4] + '.ogg')
            subprocess.run([FFMPEG, '-hide_banner', '-loglevel', 'error', '-y', '-i', os.path.join(src, fn), '-ac', '1',
                            '-c:a', 'libvorbis', '-q:a', '6', out], check=True)
        print(bank, len(os.listdir(dst)), 'ogg ->', dst)


# ---------------------------------------------------------------- the mod's sounds
MOD = ds2paths.RESOURCES
WEAPONS_JSON = os.path.join(MOD, 'data', 'soulsmode', 'ds2', 'weapons.json')
OUT_DATA = os.path.join(MOD, 'data', 'soulsmode', 'ds2', 'attack_sounds.json')
OUT_SOUNDS = os.path.join(MOD, 'assets', 'soulsmode', 'sounds')
OUT_SOUNDS_JSON = os.path.join(MOD, 'assets', 'soulsmode', 'sounds.json')
SWING_BASE = 6000
ITEM_USE_ARMOR = (180090, 180190)  # a00_18_0090 ItemUseDeamonsSoul (soul items), a00_18_0190 ItemUseAbsorb (effigy)
# The item's own sound (docs/research/sounds.md "Sonido del objeto"): ItemParam +0x08/+0x0c/+0x10 are FFX ids that TAE
# 220200 starts at a dummy (slot = param 1, FUN_14016b840 -> FXCGSfxCtrl); the FFX (Game\sfx\sfx9999.ffxbnd.dcx)
# action 75 plays the s-event of its first parameter (FXCGDrawEntityHostSound, FUN_140bef820). Soul items (ItemType 550)
# slot 1 = FFX 1051..1072 -> s000000274 at 1.6333 s; Human Effigy 60151000 slot 1 = FFX 871 -> s000000273 and slot 2 =
# FFX 872 -> s000000872, both at 1.5 s. {TAE id: [(start s, event)]}.
ITEM_FFX_SOUNDS = {180090: [(1.6333, 's000000274')], 180190: [(1.5, 's000000273'), (1.5, 's000000872')]}
# Bullet SFX with a sound (same FFX action 75): Alluring Skull BulletParam 60530000 +0xbc (impact) = FFX 845 ->
# s000000845, its child 60530010 +0x00 = FFX 846 -> s000000846 (sample 846_skull_loop). The flight FFX 844 has none.
BULLET_FFX_SOUNDS = {'alluring_skull_impact': 's000000845', 'alluring_skull_lure': 's000000846'}
ARMOR_SETS = (('naked', 11001100), ('leather', 11040100), ('chainmail', 17420100), ('iron', 11050100),
              ('gold', 15130100), ('diamond', 17240100), ('turtle', 11660100))  # FUN_1404a5c30: id + 6000 + 10 x WeaponTypeParam +0x37 (types 1..10; 11 = +110 and a 2nd sound)


def fixed_201(e):
    """A TAE 201 whose name needs no runtime lookup in FUN_1403278d0 -> FUN_1404a4a70: variant source 0 (no armour /
    ground value added), no voice +1000, no ground check (+0xf), no chr-state condition (+0xe). The player's chr
    term is 0 (FUN_1403278d0: (chr id / 10) x 10 x 100000 with c0001 -> 0), so the name is prefix + id."""
    return e['variant'] == 0 and e['voice_sex'] == 0 and e['ground'] == 0 and e['cond'] == 0


def event_name_201(e):
    return ('s%09d' if e['s_category'] == 1 else 'c%09d') % e['id']


def measure(names, starts=300):
    """Runs each event in the game's FMOD (no output) and reads what it applies on every start: the event's channel
    group pitch / volume (event properties: pitch, quantised pitch randomisation, volume, volume randomisation) and
    every channel (one per layer: sound definition pitch / volume and their randomisation, layer effects). The channel
    is matched to its sound definition by the length of the playing wave."""
    import math
    cat = json.load(open(os.path.join(STAGE, 'catalog.json')))
    E, D = cat['events'], cat['sounddefs']
    f = Fmod(os.path.dirname(fev_path()))
    ev, ex = f.ev, f.ex
    proj = C.c_void_p()
    Fmod._ok(ev.FMOD_EventSystem_Load(f.es, b'frpg2.fev', None, C.byref(proj)), 'Load')

    def chans(cg, out):
        n = C.c_int()
        ex.FMOD_ChannelGroup_GetNumChannels(cg, C.byref(n))
        for k in range(n.value):
            ch = C.c_void_p()
            ex.FMOD_ChannelGroup_GetChannel(cg, k, C.byref(ch))
            out.append(ch)
        g = C.c_int()
        ex.FMOD_ChannelGroup_GetNumGroups(cg, C.byref(g))
        for k in range(g.value):
            s = C.c_void_p()
            ex.FMOD_ChannelGroup_GetGroup(cg, k, C.byref(s))
            chans(s, out)
    res = {}
    for name in names:
        info = E[name]
        e = C.c_void_p()
        Fmod._ok(ev.FMOD_EventProject_GetEventByProjectID(proj, info['project_id'], 0, C.byref(e)), name)
        by_len = {}
        for sd in info['sounddefs']:
            for w in D[sd]['waves']:
                by_len.setdefault(w['length_ms'], set()).add(sd)
        group_p, group_v, layer = set(), [], {sd: [[], []] for sd in info['sounddefs']}
        for _ in range(starts):
            Fmod._ok(ev.FMOD_Event_Start(e), 'Start ' + name)
            for _ in range(2):
                ev.FMOD_EventSystem_Update(f.es)
            cg = C.c_void_p()
            ev.FMOD_Event_GetChannelGroup(e, C.byref(cg))
            gp, gv = C.c_float(), C.c_float()
            ex.FMOD_ChannelGroup_GetPitch(cg, C.byref(gp))
            ex.FMOD_ChannelGroup_GetVolume(cg, C.byref(gv))
            group_p.add(round(12 * math.log2(gp.value), 3))
            group_v.append(gv.value)
            out = []
            chans(cg, out)
            for ch in out:
                fr, cv = C.c_float(), C.c_float()
                ex.FMOD_Channel_GetFrequency(ch, C.byref(fr))
                ex.FMOD_Channel_GetVolume(ch, C.byref(cv))
                snd = C.c_void_p()
                ex.FMOD_Channel_GetCurrentSound(ch, C.byref(snd))
                df, v, pa, pr = C.c_float(), C.c_float(), C.c_float(), C.c_int()
                ex.FMOD_Sound_GetDefaults(snd, C.byref(df), C.byref(v), C.byref(pa), C.byref(pr))
                ms = C.c_uint()
                ex.FMOD_Sound_GetLength(snd, C.byref(ms), 1)  # FMOD_TIMEUNIT_MS
                cands = by_len.get(ms.value) or by_len.get(ms.value + 1) or by_len.get(ms.value - 1)
                assert cands and len(cands) == 1, (name, ms.value, cands)  # unambiguous layer
                sd = next(iter(cands))
                layer[sd][0].append(12 * math.log2(fr.value / df.value))
                layer[sd][1].append(cv.value)
            ev.FMOD_Event_Stop(e, 1)
            ev.FMOD_EventSystem_Update(f.es)
        p = info['properties']
        base, n = p['pitch_semitones'], p['pitch_randomization_semitones']
        # The event pitch is the base plus a whole number of semitones in [-n, n] (seen on every start).
        assert all(abs(x - round(x)) < 1e-3 and abs(x - base) <= n + 1e-3 for x in group_p), (name, group_p)
        lo_v = p['volume'] * (1 - p['volume_randomization'])
        assert all(lo_v - 1e-3 <= x <= p['volume'] + 1e-3 for x in group_v), (name, min(group_v), max(group_v))
        res[name] = {'base': base, 'n': n, 'volume': p['volume'], 'vr': p['volume_randomization'],
                     'max_distance': p['3d_max_distance'], 'play_distance': p.get('playDistance'),
                     'layers': {sd: {'pitch': [min(v[0]), max(v[0])], 'volume': [min(v[1]), max(v[1])]}
                                for sd, v in layer.items() if v[0]}}
        missing = [sd for sd, v in layer.items() if not v[0]]
        assert not missing, (name, missing)  # every started definition got a channel
    return res


def mod():
    """Builds the mod's sounds: data/soulsmode/ds2/attack_sounds.json (TAE sound events of every animation in
    weapons.json, the weapons' swing type, each event's layers and randomisation), assets/soulsmode/sounds.json and
    the .ogg files (one per wave and baked pitch)."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import math
    import params
    cat = json.load(open(os.path.join(STAGE, 'catalog.json')))
    E, D = cat['events'], cat['sounddefs']
    tae_snd = json.load(open(os.path.join(STAGE, 'tae_snd.json')))
    weapons = json.load(open(WEAPONS_JSON))
    wtp = params.load('WeaponTypeParam')
    swing_type = {name: wtp[w['weaponType']][0x37] for name, w in weapons['weapons'].items()}
    playable = lambda n: n in E and E[n]['sounddefs']  # noqa: E731
    # TAE 201 variant sources 2..5 = chr+0xb0 +0x10..+0x1c = ArmorParam +0x48 of the equipped pieces head / chest /
    # arms / legs (FUN_14037e0f0, FUN_140348690 = eq+0x298 + i x 0x30, equipload.md), 1 for an empty slot. The mod's
    # armour sets (weapons.py 'armor', the user's mapping) give the rows; used for the item-use animations only.
    armor_p = params.load('ArmorParam')
    armor_sets = {}
    for set_name, base in ARMOR_SETS:
        armor_sets[set_name] = [struct.unpack_from('<i', armor_p[base + i], 0x48)[0] if base + i in armor_p else None
                                for i in range(4)]
    anims, needed, skipped = {}, set(), {}
    keys = sorted(set(weapons['animations']) | {str(k) for k in ITEM_USE_ARMOR}, key=int)
    for key in keys:
        rows = []
        for e in tae_snd.get(key, []):
            if (e['type'] == 201 and int(key) in ITEM_USE_ARMOR and 2 <= e['variant'] <= 5 and e['s_category'] == 0
                    and e['voice_sex'] == 0 and e['ground'] == 0 and e['cond'] == 0):
                names = {v: 'c%09d' % (e['id'] + v) for mats in armor_sets.values() for v in mats if v is not None}
                names = {v: n for v, n in names.items() if playable(n)}
                rows.append({'t': e['start'], 'id': e['id'], 'piece': e['variant'] - 2})
                needed.update(names.values())
                continue
            if e['type'] == 202:
                rows.append({'t': e['start'], 'swing': e['swing'], 'hand': e['hand']})
            elif e['type'] == 201 and fixed_201(e) and playable(event_name_201(e)):
                rows.append({'t': e['start'], 'event': event_name_201(e)})
                needed.add(event_name_201(e))
            elif e['type'] == 201:
                why = 'variant' if e['variant'] else 'voice' if e['voice_sex'] else 'ground' if e['ground'] \
                    else 'cond' if e['cond'] else 'no event'
                skipped[why] = skipped.get(why, 0) + 1
        for t, name in ITEM_FFX_SOUNDS.get(int(key), []):
            if playable(name):
                rows.append({'t': t, 'event': name})
                needed.add(name)
        if rows:
            rows.sort(key=lambda r: r['t'])
            anims[key] = rows
    bullets = {k: n for k, n in BULLET_FFX_SOUNDS.items() if playable(n)}
    needed.update(bullets.values())
    swings = {r['swing'] for rows in anims.values() for r in rows if 'swing' in r}
    for t in set(swing_type.values()):
        for s in swings:
            n = 'c%09d' % (SWING_BASE + 10 * t + s + (100 if t == 11 else 0))
            if t <= 10 and playable(n):
                needed.add(n)
            elif t == 11 and playable('c%09d' % (SWING_BASE + 110 + s)):
                needed.add('c%09d' % (SWING_BASE + 110 + s))
    meas = measure(sorted(needed))
    # Files: one .ogg per (wave, baked pitch). Baked = event base + the layer's centre; the rest is randomised in game.
    os.makedirs(os.path.join(OUT_SOUNDS, 'ds2'), exist_ok=True)
    # The folder only holds what this run writes (an older run's baked pitches would stay otherwise).
    for old in os.listdir(os.path.join(OUT_SOUNDS, 'ds2')):
        if old.endswith('.ogg'):
            os.remove(os.path.join(OUT_SOUNDS, 'ds2', old))
    sounds_json, events, files = {}, {}, {}
    for name in sorted(needed):
        m = meas[name]
        layers = []
        for k, (sd, lm) in enumerate(m['layers'].items()):
            centre = (lm['pitch'][0] + lm['pitch'][1]) / 2
            bake = round(m['base'] + centre, 2)
            entries = []
            for w in D[sd]['waves']:
                sample = w['sample'].lower()
                fn = sample if abs(bake) < 0.005 else f"{sample}_p{'m' if bake < 0 else ''}{abs(round(bake * 100))}"
                if fn not in files:
                    src = os.path.join(STAGE, 'wav', w['bank'], w['sample'] + '.wav')
                    ratio = 2 ** (bake / 12)
                    rate = struct.unpack_from('<I', open(src, 'rb').read(), 24)[0]
                    filt = [] if fn == sample else ['-af', f'asetrate={rate * ratio:.4f},aresample={rate}']
                    subprocess.run([FFMPEG, '-hide_banner', '-loglevel', 'error', '-y', '-i', src, *filt, '-ac', '1',
                                    '-c:a', 'libvorbis', '-q:a', '6', os.path.join(OUT_SOUNDS, 'ds2', fn + '.ogg')],
                                   check=True)
                    files[fn] = {'sample': w['sample'], 'bank': w['bank'], 'pitch_semitones': bake}
                entries.append({'name': 'soulsmode:ds2/' + fn, 'weight': w['weight'],
                                'attenuation_distance': int(round(m['max_distance']))})
            sid = f'ds2.{name}.{k}'
            sounds_json[sid] = {'sounds': entries}
            layers.append({'sound': sid, 'sounddef': sd, 'pitchSpread': round((lm['pitch'][1] - lm['pitch'][0]) / 2, 4),
                           'volume': [round(lm['volume'][0], 4), round(lm['volume'][1], 4)]})
        first = D[next(iter(m['layers']))]['waves'][0]
        src = os.path.join(STAGE, 'wav', first['bank'], first['sample'] + '.wav')
        with wave.open(src) as wf:
            length = wf.getnframes() / wf.getframerate()
        events[name] = {'length': round(length, 4), 'volume': round(m['volume'], 4), 'volumeRandomization': round(m['vr'], 4),
                        'pitchSteps': int(round(m['n'])), 'range': int(round(m['max_distance'])), 'layers': layers}
    data = {'source': 'tools/re/sounds.py mod (docs/research/sounds.md): c000100_snd.tae events 201/202 of the '
                      'weapons.json animations, frpg2.fev events measured in the game\'s FMOD Ex 4.44.50',
            'swingBase': SWING_BASE, 'swingType': swing_type, 'armorSound': armor_sets, 'animations': anims,
            'bulletSounds': bullets,
            'events': events}
    json.dump(data, open(OUT_DATA, 'w'), indent=1)
    # Entries of other exporters (you_died.py "menu.*") stay.
    if os.path.exists(OUT_SOUNDS_JSON):
        sounds_json.update({k: v for k, v in json.load(open(OUT_SOUNDS_JSON)).items() if k.startswith('menu.')})
    json.dump(sounds_json, open(OUT_SOUNDS_JSON, 'w'), indent=1)
    print(len(anims), 'animations,', len(events), 'events,', len(files), 'files; 201 left out:', skipped)


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'all'
    steps = {'extract': [extract], 'catalog': [catalog], 'tae': [tae], 'decode': [decode], 'ogg': [ogg], 'mod': [mod],
             'all': [extract, catalog, tae, decode, ogg]}[cmd]
    os.makedirs(STAGE, exist_ok=True)
    for s in steps:
        s()
