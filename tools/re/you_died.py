"""DS2's death banner ("YOU DIED" / "HAS MUERTO"), docs/research/you_died.md. Writes:
  assets/soulsmode/textures/gui/you_died_<en|es>.png   the text row of YOU_DIED.tpf (menu/tex/language/<lang>.fetexbnd.dcx)
  assets/soulsmode/textures/gui/you_died_band.png      the band sprite of waku_02.tpf (menu/99.febnd.dcx)
  assets/soulsmode/sounds/ds2_menu/m000000010.ogg      the sting (Game/sound/frpg2_smain.fsb subsound 5, decoded by the
                                                       game's fmodex64.dll, WAVWRITER_NRT), plus its sounds.json entry
  data/soulsmode/ds2/you_died.json                     per FLO frame (30 fps), each sprite's affine in the 1280 x 720
                                                       FLO space and its RGBA tint
Layout: menu/01.febnd.dcx -> l01_14_message.flo, scene id 3 (clip 0x48): text track clip 0x47 (35 keys at 0xf080), band
track clip 0x45 (30 keys at 0xf710) with its inner placement of clip 0x44 (0xf020); keyframe records of 0x30 bytes
(x, y, m00, m11, m10, m01, RGBA, aux, flags, frame). A frame holds its last key. Usage: you_died.py (env DS2_WORK,
FFMPEG with libvorbis).
"""
import ctypes as C
import io
import json
import os
import struct
import subprocess
import tempfile

from PIL import Image

import ds2fs
import ds2paths

FPS = 30
CLOSE_FRAME = 102          # label 0x65 of clip 0x48: FUN_140065730 closes the message group
LENGTH = 106               # clip 0x48 length
ROOT = (631.25, 322.55)    # scene id 3 root (scene table entry 9, 0x1a2b8)
DELAY = 3.0                # FUN_14018ed90 WaitJob, DAT_1410b0688
TEXT_UV = (0, 126.15, 1024, 256)
TEXT_POS = (-262.15, -136.15)
BAND_UV = (915.35, 304.9, 997.6, 512)
BAND_POS = (-915.35, -304.9)
SOUND = 'm000000010'       # FUN_1401905c0(this, 2) -> FUN_1404a55f0(req, 1, 10), "m%09d"
LANGS = {'en': 'English', 'es': 'NeutralSpanish'}


def bnd4(d):
    """{name: bytes} of a BND4 (entry sizes 0x18 / 0x20 / 0x24)."""
    assert d[:4] == b'BND4'
    n = struct.unpack_from('<I', d, 0xC)[0]
    es = struct.unpack_from('<q', d, 0x20)[0]
    uni = d[0x30] == 1
    out = {}
    for i in range(n):
        e = 0x40 + i * es
        if es == 0x24:
            size, _, off, _, noff = struct.unpack_from('<qqIiI', d, e + 8)
        elif es == 0x20:
            size, off, _, noff = struct.unpack_from('<qIiI', d, e + 8)
        else:
            size, off, noff = struct.unpack_from('<qII', d, e + 8)
        if uni:
            end = noff
            while d[end:end + 2] != b'\0\0':
                end += 2
            name = d[noff:end].decode('utf-16-le')
        else:
            name = d[noff:d.index(b'\0', noff)].decode('shift_jis')
        out[name.replace('\\', '/').split('/')[-1]] = d[off:off + size]
    return out


def tpf_image(t):
    """The single texture of a TPF as RGBA (offset, size at +0x10; DDS)."""
    assert t[:4] == b'TPF\0'
    off, size = struct.unpack_from('<II', t, 0x10)
    return Image.open(io.BytesIO(t[off:off + size])).convert('RGBA')


def crop(img, uv):
    return img.crop(tuple(int(round(v)) for v in uv))


def keys(d, at, n):
    out = []
    for i in range(n):
        o = at + i * 0x30
        x, y, a, dd, b, c = struct.unpack_from('<6f', d, o)
        out.append({'x': x, 'y': y, 'a': a, 'd': dd, 'b': b, 'c': c, 'rgba': list(d[o + 0x18:o + 0x1c]),
                    'frame': struct.unpack_from('<I', d, o + 0x24)[0]})
    assert [k['frame'] for k in out] == sorted(k['frame'] for k in out)
    return out


def held(track, f):
    best = track[0]
    for k in track:
        if k['frame'] <= f:
            best = k
    return best


def mul(m, n):
    """2 x 3 affines [a, b, c, d, tx, ty]: x' = a x + c y + tx, y' = b x + d y + ty."""
    a, b, c, d, tx, ty = m
    a2, b2, c2, d2, tx2, ty2 = n
    return [a * a2 + c * b2, b * a2 + d * b2, a * c2 + c * d2, b * c2 + d * d2, a * tx2 + c * ty2 + tx, b * tx2 + d * ty2 + ty]


def affine(k):
    # +0x10 = m10 (b), +0x14 = m01 (c): the reading that makes band and text concentric (you_died.md, sin confirmar).
    return [k['a'], k['b'], k['c'], k['d'], k['x'], k['y']]


def sound(out_dir):
    """The sting through the game's FMOD Ex (as sounds.py), WAV -> mono-less stereo .ogg (ffmpeg libvorbis)."""
    os.add_dll_directory(ds2paths.GAME)
    ex = C.WinDLL(os.path.join(ds2paths.GAME, 'fmodex64.dll'))
    wav = os.path.join(tempfile.gettempdir(), SOUND + '.wav')
    s = C.c_void_p()
    ex.FMOD_System_Create(C.byref(s))
    ex.FMOD_System_SetOutput(s, 5)  # WAVWRITER_NRT
    ex.FMOD_System_SetSoftwareFormat(s, 48000, 2, 2, 0, 0)
    assert ex.FMOD_System_Init(s, 32, 0, C.c_char_p(wav.encode())) == 0
    snd = C.c_void_p()
    fsb = os.path.join(ds2paths.GAME, 'sound', 'frpg2_smain.fsb')
    assert ex.FMOD_System_CreateSound(s, fsb.encode(), 0x40 | 0x1, None, C.byref(snd)) == 0
    sub = C.c_void_p()
    assert ex.FMOD_Sound_GetSubSound(snd, 5, C.byref(sub)) == 0
    ch = C.c_void_p()
    ex.FMOD_System_PlaySound(s, -1, sub, 1, C.byref(ch))
    ex.FMOD_Channel_SetMode(ch, 1)
    ex.FMOD_Channel_SetLoopCount(ch, 0)
    ex.FMOD_Channel_SetPaused(ch, 0)
    length = C.c_uint()
    ex.FMOD_Sound_GetLength(sub, C.byref(length), 2)
    pos, playing, last = C.c_uint(0), C.c_int(1), -1
    for _ in range(200000):
        ex.FMOD_System_Update(s)
        ex.FMOD_Channel_IsPlaying(ch, C.byref(playing))
        ex.FMOD_Channel_GetPosition(ch, C.byref(pos), 2)
        if not playing.value or pos.value + 1 >= length.value or pos.value < last:
            break
        last = pos.value
    ex.FMOD_System_Release(s)
    os.makedirs(out_dir, exist_ok=True)
    subprocess.run([ds2paths.ffmpeg(), '-hide_banner', '-loglevel', 'error', '-y', '-i', wav, '-c:a', 'libvorbis',
                    '-q:a', '6', os.path.join(out_dir, SOUND + '.ogg')], check=True)
    os.remove(wav)
    return length.value / 48000


def main():
    gui = os.path.join(ds2paths.ASSETS, 'textures', 'gui')
    os.makedirs(gui, exist_ok=True)
    for code, lang in LANGS.items():
        tex = bnd4(ds2fs.undcx(ds2fs.read('gamedata:/menu/tex/language/%s.fetexbnd.dcx' % lang)))
        crop(tpf_image(tex['YOU_DIED.tpf']), TEXT_UV).save(os.path.join(gui, 'you_died_%s.png' % code))
    menu99 = bnd4(ds2fs.undcx(ds2fs.read('gamedata:/menu/99.febnd.dcx')))
    crop(tpf_image(menu99['waku_02.tpf']), BAND_UV).save(os.path.join(gui, 'you_died_band.png'))
    flo = bnd4(ds2fs.undcx(ds2fs.read('gamedata:/menu/01.febnd.dcx')))['l01_14_message.flo']
    assert flo[0x5a] == FPS
    text = keys(flo, 0xf080, 35)
    band = keys(flo, 0xf710, 30)
    place = keys(flo, 0xf020, 1)[0]
    root = [1, 0, 0, 1, ROOT[0], ROOT[1]]
    frames = []
    for f in range(1, LENGTH + 1):
        tk, bk = held(text, f), held(band, f)
        # Sprite quad = position + its uv rectangle, in pixels of the uv size.
        t_m = mul(mul(root, affine(tk)), [1, 0, 0, 1, TEXT_POS[0] + TEXT_UV[0], TEXT_POS[1] + TEXT_UV[1]])
        b_m = mul(mul(mul(root, affine(bk)), affine(place)),
                  [1, 0, 0, 1, BAND_POS[0] + BAND_UV[0], BAND_POS[1] + BAND_UV[1]])
        b_rgba = [round(bk['rgba'][i] * place['rgba'][i] / 255) for i in range(4)]
        frames.append({'text': [round(v, 5) for v in t_m] + tk['rgba'], 'band': [round(v, 5) for v in b_m] + b_rgba})
    length = sound(os.path.join(ds2paths.ASSETS, 'sounds', 'ds2_menu'))
    data = {'source': 'tools/re/you_died.py (docs/research/you_died.md): l01_14_message.flo scene 3, 1280 x 720 FLO '
                      'space; each frame [a, b, c, d, tx, ty, r, g, b, a] (x\' = a x + c y + tx, y\' = b x + d y + ty)',
            'fps': FPS, 'closeFrame': CLOSE_FRAME, 'delay': DELAY, 'space': [1280, 720],
            'textSize': [TEXT_UV[2] - TEXT_UV[0], round(TEXT_UV[3] - TEXT_UV[1], 4)],
            'bandSize': [round(BAND_UV[2] - BAND_UV[0], 4), round(BAND_UV[3] - BAND_UV[1], 4)],
            'sound': SOUND, 'soundLength': round(length, 4), 'frames': frames}
    json.dump(data, open(os.path.join(ds2paths.DATA, 'you_died.json'), 'w'), indent=1)
    # sounds.json (sounds.py mod keeps the "menu." entries when it rewrites it).
    path = os.path.join(ds2paths.ASSETS, 'sounds.json')
    sounds = json.load(open(path)) if os.path.exists(path) else {}
    sounds['menu.' + SOUND] = {'sounds': [{'name': 'soulsmode:ds2_menu/' + SOUND, 'stream': True}]}
    json.dump(sounds, open(path, 'w'), indent=1)
    print('you_died: textures, %d frames, sound %.2f s' % (len(frames), length))


if __name__ == '__main__':
    main()
