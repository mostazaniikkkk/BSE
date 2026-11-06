# Regulation (params) — desencriptado y formato

Herramientas locales (fuera del repo): `D:\Reversing\DS2\tools\decrypt_regulation.py`, `unpack_bnd.py`.

## Carga — `RegulationResourceObject`
- `FUN_1402de2c0` pide `title:/enc_regulation.bnd.dcx` (`FUN_14048cb30`).
- vtable `RegulationResourceObject` (`0x1410d9a08`) `+0x80` → `FUN_1402df8f0` → `FUN_1402e0470`.

## Desencriptado — `FUN_1402e0470` → `FUN_140261150`
- Clase `Cypher::AesCypher` (ctor `FUN_140260240`).
- Clave AES-128: qwords `0x54940adf30811740`, `0x4c25bfec71e10933` (little-endian).
- Archivo: `IV[16] | TAG[16] | ciphertext`.
- Modo (`FUN_140260e70`, `FUN_140260ad0`): contador `0x80 | IV[0..10] | u32be`, incrementado **antes** de cada bloque
  (el primer bloque usa 1). El hash en bloques de 12 bytes y la H derivada de `E(0xC0…)` con el bit alto limpio
  corresponden a modo CWC. El tag no se verifica en nuestra herramienta.
- `FUN_1402da560` usa el mismo esquema con **otra** clave (`0x979fcb254cb3e29d`, `0xd0ce4ff059a03bd1`):
  es la regulation descargada por red, no la local.

## Descompresión
- `FUN_1402e0470`: `algorithm_deflate_level_6` → contenedor `DCX` / `DFLT` (zlib).
- Contenido: `BND4` con 228 `.param`.

## Formato `.param` (verificado contra el accessor `FUN_140358b90`)
- `+0x0A` u16: número de filas. `+0x0C`: nombre del tipo (32 bytes). `+0x2D` ≠ 0: tabla de filas de 64 bits.
- Filas desde `+0x40`, 0x18 bytes: `u64 id, u64 offset de datos, u64 offset de nombre`.

## Param manager
`*(DAT_1416148f0 + 0x18)`. `FUN_140359bb0` carga los 0x52 params de `FUN_14048b620` en `+0x310 + índice*0x10`.

| Índice | Param | Slot |
|---|---|---|
| 0x26 | PlayerChrGrowthParam | +0x570 |
| 0x27 | PlayerLevelUpSoulsParam | +0x580 |
| 0x28 | RelatePhysicalStatToLevelStatParam | +0x590 |
| 0x29 | PhysicalStatsPerLevelStatValuesParam | +0x5a0 |
| 0x2a | MenuStatsParam | +0x5b0 |
| 0x2b | HollowLevelStatsParam | +0x5c0 |
| 0x2c | PlayerLackOfStatsParam | +0x5d0 |
| 0x42 | PlayerStatusParam | +0x730 |
