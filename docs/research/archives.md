# Archivos `.bhd` / `.bdt` — desencriptado y extracción

Herramientas locales (fuera del repo): `D:\Reversing\DS2\tools\decrypt_bhd.py`, `extract_bdt.py`, `dump_fmg.py`.

## Montaje — `FUN_1402ef410`
Cada archivo se monta con `(nombre.bhd, nombreKeyCode.pem)` vía `FUN_140b0bd50`:
`gamedata` → GameDataEbl, `chrlq` → LqChrEbl, `partslq`, `objlq`, `maplq`.
Alias de rutas relevantes: `text` → `gamedata:/menu`.

## Cabecera `.bhd` — `FUN_140ff00a0`
OpenSSL 0.9.8o. `PEM_read_RSAPublicKey` (`FUN_140aa92c0`) y luego, por cada bloque de `RSA_size()` (256 bytes),
`RSA_public_decrypt` (`FUN_140aa8430` → `meth+0x10`) con padding 3 (`RSA_NO_PADDING`), quedándose con los bytes `[1:]`.
Resultado: `BHD5` con salt `FRPG2_STEAM_GAME_DATA_EBL_SALT64`.

## BHD5
- `FUN_140fef880`: `+0x10` u32 número de buckets, `+0x14` u32 offset de la tabla de buckets.
- `FUN_140fef650` (lookup): toma la ruta **después del primer `:`**; por cada carácter UTF-16, `A-Z` → minúscula,
  `\` → `/`, `h = h*37 + c` (u32). Bucket `h % n` = `{u32 count, u32 offset}`; entradas de 32 bytes.
- Entrada: `+0` u32 hash, `+4` u32 tamaño (`FUN_140ff0eb0`), `+8` u64 offset en `.bdt`,
  `+0x10` u64 offset de info de hash (SHA, 0x20 bytes + rangos), `+0x18` u64 offset de info AES (0 = sin cifrar).

## Cifrado de datos — `FUN_140ff1730` → `FUN_140fefee0`
- Info AES: `key[16]`, `+0x10` u32 número de rangos, `+0x14` rangos `(u64 inicio, u64 fin)` relativos al archivo.
- `FUN_140fefee0`: OpenSSL EVP con el cipher de `0x1411baa70` → **nid 418 (aes-128-ecb)**, sin padding.

## FMG (textos)
- `FUN_14048c8c0`: `text:/Text/<idioma>/<nombre>.fmg`. Idiomas: tabla en `0x141588140` (`Japanese`, `English`, …).
  Nombres: tabla en `0x141585e70` (`common`, `IngameSystem`, …, `iconHelp`, …).
- Ruta real: `gamedata:/menu/Text/English/iconHelp.fmg`.
- Formato (observado en los archivos): `+0x0C` grupos, `+0x10` strings, `+0x14` tabla de offsets; grupos de 12 bytes
  en `+0x1C` `(índice, primer id, último id)`; texto UTF-16LE.
