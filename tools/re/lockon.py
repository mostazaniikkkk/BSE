"""Builds data/soulsmode/ds2/lockon.json: the player's lock-on (PlayerLockTargetCtrl, docs/research/lockon.md).

- LockOnParam row 0 (the player's default row, FUN_14031cbc0 / FUN_140382630): 8 floats.
- LockTargetParam row 0 (every CommonParam +0xbc is 0): list-radius and acquire-distance multipliers.
- Target CommonParam: +0x360 line-of-sight loss time, +0xc4 / +0xc8 exclusion radius / height. Players are
  ChrCommonParam row 100; Minecraft mobs use the most common EnemyCommonParam values (a choice: mobs have no row).
- CameraExFollowParam row 0: the locked camera (FUN_14049df40) and the recenter (FUN_14049cbf0).

Usage: lockon.py <lockon.json>
"""
import collections
import json
import struct
import sys

import params


def target(row):
    lost, = struct.unpack_from('<f', row, 0x360)
    radius, height = struct.unpack_from('<2f', row, 0xc4)
    return {'lostSightTime': round(lost, 4), 'exclusionRadius': round(radius, 4), 'exclusionHeight': round(height, 4)}


def main():
    lock = struct.unpack_from('<8f', params.load('LockOnParam')[0])
    multipliers = struct.unpack_from('<2f', params.load('LockTargetParam')[0])
    enemy = params.load('EnemyCommonParam')
    key = lambda r: bytes(r[0x360:0x364] + r[0xc4:0xcc])
    common = collections.Counter(key(r) for r in enemy.values()).most_common(1)[0][0]
    mob = next(r for r in enemy.values() if key(r) == common)
    cam = params.load('CameraExFollowParam')[0]
    f = lambda off: round(struct.unpack_from('<f', cam, off)[0], 4)
    out = {
        'source': __doc__.strip().splitlines()[0],
        'lockOn': {'listRadius': lock[0], 'acquireDistance': lock[2], 'zoneWide': lock[3] * 0.01, 'zoneNarrow': lock[4] * 0.01,
                   'coneYaw': lock[5], 'conePitch': lock[6], 'closeRange': lock[7],
                   # f1: bullet homing range of the player's spells (PlayerLockTargetCtrl+0x24, docs/research/casting.md s.7).
                   'bulletRange': lock[1]},
        'lockTarget': {'listRadius': multipliers[0], 'acquireDistance': multipliers[1]},
        'targets': {'player': target(params.load('ChrCommonParam')[100]), 'mob': target(mob)},
        'camera': {'fov': f(0x04), 'pitchMin': f(0x78), 'pitchMax': f(0x7c), 'nearPitchMin': f(0x80),
                   'nearPitchMax': f(0x84), 'pitchBlendLow': f(0x88), 'pitchBlendHigh': f(0x8c),
                   'trackRate': f(0xb8), 'deadZone': f(0xbc), 'aimOffset': f(0xc0), 'trackRamp': f(0x118),
                   'recenterTimeMin': f(0x100), 'recenterTimeMax': f(0x104), 'recenterPower': f(0x108)},
    }
    out = json.loads(json.dumps(out), parse_float=lambda x: round(float(x), 4))
    json.dump(out, open(sys.argv[1], 'w', encoding='utf8'), indent=1)
    print(json.dumps(out))


if __name__ == '__main__':
    main()
