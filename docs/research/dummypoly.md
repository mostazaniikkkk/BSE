# Dummy polys del origen del bullet (200 / 1)

Contexto: casting.md §7.2. El bullet nace en la dummy poly `PDP+0x31` (+ contadores de disparo / bullet), buscada con
`chr vt+0x218(id, mano)` cuando PDP `+0x30 == 1`. Referencias: **200** (Soul Arrow, Fireball, Dark Orb), **1** (Lightning Spear).

Herramientas locales (fuera del repo, `D:\Reversing\DS2\tools\`): `unpack_bnd4_any.py` (BND4 con entradas de 0x24 bytes),
`flver_dummy.py` (huesos y dummies de un FLVER2), `probe_names.py` (prueba rutas contra un BHD5), `dummy_origin.py`
(cuenta de abajo). Salidas de Ghidra: `D:\Reversing\DS2\out\dummy\`. Modelos extraídos: `D:\Reversing\DS2\extracted\models\`.

## 1. La dummy está en el modelo del arma de esa mano (confirmado por código)

Cadena de `vt+0x218` (CharacterCtrl / PlayerCtrl, vtables `0x1410df218` / `0x1410e4bb8`, `vt.txt`):

1. `FUN_140132430(out, chr, mano, id)` llama `chr vt+0x218(chr, id, mano, colector)` (`ModelDummyPolyAllCollector`).
2. `vt+0x218` = `FUN_140311b90`: `chr+0x378` (ChrAsmCtrl) `vt+0x80` (`FUN_140151350`, devuelve `+0x30` = ChrAsmModel)
   → `ChrAsmModel vt+0x58(mano, id, colector)`.
3. `ChrAsmModel vt+0x58` = `FUN_1403397d0` (desensamblado en `dis1.txt`; la llamada a `0x14034e6a0` está ofuscada,
   seguida con `tools/re/trace.py`): convierte la mano en **ranura de equipo** (`ChrEquipSlot`) con el objeto `this+0x28 vt+0x70`:
   - −2 → `2·k`, −1 → `2·k + 1`, otro valor → sin cambio; `k = FUN_140348810(obj, mano) = obj+0x14 + mano·4`
     (mano 0 = izquierda, 1 = derecha). `k` es, por la forma de las ranuras, el arma activa (0..2) de esa mano:
     **sin confirmar** el nombre del campo.
   - luego `vt+0x50` = `FUN_140339750(ranura, id, colector)`.
4. `FUN_140339750`: `parte = FUN_14034e210(ranura)` — ranuras 0/2/4 → **parte 5**, 1/3/5 → **parte 6**, 6..9 → 1..4,
   10–11 → 9–10, 12–15 → 11–14. Toma el modelo `this+0xd08 + parte·8` (`vt+0x58`) y busca ahí la dummy
   (`FUN_140af4b10` → `modelo+0x50 vt+0x60(id, colector, matriz del modelo +0x10..+0x4c, +0x98)`).
   `FUN_14034aa90` / `FUN_14034ac70` usan la misma pareja: parte 6 ↔ mano −1, parte 5 ↔ mano −2.

**Resultado**: −1 = modelo del arma derecha activa (parte 6), −2 = el de la izquierda (parte 5).
El cuerpo (c0001) no se consulta en este camino. Si el arma no tiene la dummy, `FUN_140132430` no encuentra nada y
el bullet sale de la matriz del dueño (casting.md §7.2).

Con PDP `+0x30 == 0` el camino es `chr vt+0x40` = `FUN_140311b10` → `FUN_140af4bd0(chr+0xf0 → +0x10, id, …, chr+0x60)`:
el modelo propio del personaje. Que ese modelo sea `c0001.flv`: **sin confirmar** (no lo usan los hechizos de referencia).

## 2. Archivos

- Ruta: `FUN_1402dd660` arma `%s/%s.bnd` con la carpeta de `FUN_1402de460`: prefijo `WP_` → `parts:/model/parts/weapon`
  (también `BD_` body, `SD_` shield, `HD_`, `AM_`, `LG_`…; `c` → `chr:/model/chr`). Nombre: `WP_<modelo>_M`
  (formato `%s_%04d_M%s`). Están en `GameDataEbl` (`gamedata:/model/parts/weapon/WP_3800_M.bnd`, etc.); `_M_L` es
  la versión de baja calidad y `_M_A` otra variante: las tres tienen las mismas dummies.
- Id de modelo = **WeaponParam `+0x04`**: Sorcerer's Staff (3800000) **3800**, Cleric's Sacred Chime (4010000) **4010**,
  Pyromancy Flame (5400000) **3400**. Comprobado con los archivos (`WP_3400_M` existe, `WP_5400_M` no);
  el lector de `+0x04` en código no se trazó.
- Cuerpo: `gamedata:/model/chr/c0001.bnd` → `c0001.flv` (73 huesos, 53 dummies, sin mallas).

### Formato FLVER2 (versión 0x20010, leído estructuralmente)
Cabecera 0x80 (`+0x10` dummies, `+0x14` materiales, `+0x18` huesos, `+0x49` nombres UTF-16), luego dummies de 0x40,
materiales de 0x20 y huesos de 0x80.
- Dummy: `+0x00` posición, `+0x0c` color, `+0x10` adelante, `+0x1c` id (i16), `+0x1e` hueso padre (i16),
  `+0x20` arriba, `+0x2c` hueso de enganche (i16), `+0x2f` usar arriba.
- Hueso: `+0x00` traslación, `+0x0c` offset del nombre, `+0x10` rotación (euler), `+0x1c` padre, `+0x20` escala.
- En c0001 y en las armas **todas** las dummies tienen enganche −1. La posición está en el espacio del **hueso padre**:
  las de `R_Weapon` / `R_Hand` miden centímetros, las de los huesos raíz (`Parry_Dmy`…) están a la altura del cuerpo.
  El código que elige el hueso no se trazó: esto sale de los datos.

### Matriz de una dummy (`FUN_140af5cd0`)
Entrada del colector: puntero a la dummy en memoria (`+0x00` posición, `+0x10` adelante, `+0x20` arriba, byte `+0x30`
usar arriba) + matriz del hueso. Salida (filas): **Z = adelante normalizado**, **X = normalize(arriba × Z)**
(arriba por defecto `(0,1,0)`, `DAT_141136f70`, si el byte es 0), **Y = Z × X**, origen = posición.
Constantes leídas del exe: máscaras `0x1410ac470` / `0x1411371b0` = `(~0, ~0, ~0, 0)` (solo anulan w).

## 3. Cómo se engancha el arma a la mano (confirmado por código)

`ChrAsmModelParts vt+0x98` = `FUN_140342590` → pose `parte+0x328 vt+0x28` = `FUN_1403439f0`: guarda `+0x20` = id de
dummy del cuerpo (`FUN_14034aa90`) y `+0x24` = id de dummy del propio arma (`FUN_14034ac70`)
(los llama `FUN_14033e5b0`, por cada mano).

`ChrAsmModelPoseDummyPolygon` (vtable `0x1410e1398`), actualización `FUN_1403441f0`:
1. Busca la dummy `+0x20` en la parte 2 del ChrAsmModel (`FUN_140339e50(asm, 7)`, ranura 7 → parte 2); si no está,
   en el modelo del personaje (`chr vt+0xe0` = `FUN_140312660`, `chr+0xf0 → +0x10`, matriz `chr+0x60`); si tampoco,
   la matriz del personaje (`vt+0x138`). Las piezas de pecho probadas (`BD_1001_M`, `BD_1010_M`) no tienen las dummies
   20/21: se usa el modelo del personaje, que en los datos es **c0001** (dummies 20/21 sobre `R_Weapon` / `L_Weapon`).
2. Si `+0x24 ≥ 0`: busca esa dummy en el propio arma con matriz identidad (`FUN_140af5200`, `DAT_1410ac420`) y la
   invierte (`FUN_14012ec30`: rotación traspuesta, traslación · `(-1,-1,-1)` de `DAT_1410bf010`).
3. Matriz del arma (`modelo+0x10..+0x4c`) = **inversa(dummy propia) · dummy del cuerpo**: la dummy `+0x24` del arma
   queda encima de la dummy `+0x20` del cuerpo.

Ids (`FUN_14034aa90` / `FUN_14034ac70`): bytes de una fila `obj+0x218 + h·0x48` u `obj+0x68 + …` según una postura
`obj+0x10`. Por los datos esa fila es **WeaponTypeParam** (todos sus valores son ids de dummies que existen en c0001;
el puntero no se trazó: **sin confirmar** por código). Postura → bytes:

| Postura `obj+0x10` | Derecha (cuerpo / arma) | Izquierda (cuerpo / arma) |
|---|---|---|
| 1, 4, 5, 6 | `+0x25` / `+0x26` | `+0x27` / `+0x28` |
| 3 | `+0x29` / `+0x2a` | `+0x33` / `+0x34` |
| 2 | `+0x31` / `+0x32` | `+0x2d` / `+0x2e` |
| 0 | `+0x31` / `+0x32` | `+0x33` / `+0x34` |

En la regulation `+0x25` = `+0x29` = **20** (excepto una fila 0), `+0x27` = `+0x2d` = 21 o 22, `+0x31` / `+0x33` =
30..59 (dummies de c0001 en `Pelvis` / `Spine1`: el arma guardada). Por eso se lee 1/4/5/6 = en la mano, 3 = dos manos
con la derecha, 2 = dos manos con la izquierda, 0 = las dos guardadas: **lectura de los datos, sin confirmar** qué valor
es cada postura. Fuera de las partes 5/6: `0x31` (49) y `0x35` (53) por defecto.

Catalizadores (`WP+0x10` → WTP): Sorcerer's Staff 336, Chime 356, Pyromancy Flame 11. En la mano: **cuerpo 20 / arma 1**
(derecha) y **cuerpo 21 / arma 1** (izquierda). A dos manos el bastón usa su dummy 3 (`+0x2a` = 3); la campana y la llama, la 1.

## 4. Los datos

c0001 (espacio del hueso padre, metros, ejes FLVER):

| Dummy | Hueso padre | Posición | Adelante | Arriba |
|---|---|---|---|---|
| 20 | `R_Weapon` (51) | (−0.0141, −0.0001, −0.0036) | (0.0091, 0.0004, 1.0000) | (0.0232, −0.9997, 0.0002) |
| 21 | `L_Weapon` (27) | (−0.0153, 0.0099, 0.0064) | (−0.0091, −0.0004, 1.0000) | (0.0232, −0.9997, −0.0002) |
| 1 | `R_Hand` (35) | (0.0489, 0.0150, 0.0022) | — | — |

(c0001 no tiene dummy 200. Su dummy 1 está en `R_Hand`, pero el camino de §1 no la consulta.)
`R_Weapon` es hijo de `R_Hand` con traslación (0.0700, 0, 0.0221) y rotación 0; `L_Weapon` hijo de `L_Hand`, misma
traslación y rotación π en X.

Armas (espacio del modelo; padre `Model_Dmy`, identidad):

| Modelo | Dummy 1 (pos / adelante / arriba) | Dummy 200 (pos / adelante / arriba) |
|---|---|---|
| WP_3800_M (bastón) | (0, 0.0348, 0) / (1, 0, 0) / (0, −1, 0) | (0, 0.4803, 0.0003) / (0, 0, −1) / (0, −1, 0) |
| WP_4010_M (campana) | (0, 0, 0) / (−1, 0, 0) / (0, −1, 0) | (0, −0.2275, 0) / (0, 0, −1) / (0, −1, 0) |
| WP_3400_M (llama) | (0, 0, 0) / (0, −1, 0) / (−1, 0, 0) | (0, −0.0100, −0.0026) / (0, 0, −1) / (0, 1, 0) |

## 5. Origen del bullet sobre el esqueleto

`origen = dummy(arma, id) · inversa(dummy 1 del arma) · dummy 20/21 de c0001 · hueso R_Weapon / L_Weapon (animado)`.
`dummy_origin.py` lo da en el espacio de `R_Weapon` / `L_Weapon`. Es **constante** respecto a ese hueso.

**Ejes del rig de Morpheme** (el que usan `nsa.py` y `ds2_anims.py`): son los del FLVER con **Y y Z intercambiados**.
Comprobado en los 56 huesos comunes (traslaciones locales iguales tras el cambio; `Root` (0, 0.97, 0) → (0, 0, 0.97);
`R_Weapon` (0.07, 0, 0.0221) → (0.07, 0.0221, 0)). En la tabla, `rig = (x, z, y)` del FLVER.

En la mano (cuerpo 20/21, arma 1), metros:

| Catalizador | Mano | Dummy | En `R/L_Weapon` (rig) | En `R/L_Hand` (rig, pose de reposo de `*_Weapon`) |
|---|---|---|---|---|
| Sorcerer's Staff | der. | 200 | (−0.0247, −0.0037, 0.4453) | (0.0453, 0.0184, 0.4453) |
| Sorcerer's Staff | izq. | 200 | (−0.0259, 0.0065, 0.4553) | (0.0441, 0.0156, −0.4553) |
| Cleric's Sacred Chime | der. | 200 | (−0.0088, −0.0036, −0.2276) | (0.0612, 0.0185, −0.2276) |
| Cleric's Sacred Chime | izq. | 200 | (−0.0101, 0.0064, −0.2176) | (0.0599, 0.0157, 0.2176) |
| Pyromancy Flame | der. | 200 | (−0.0114, 0.0064, 0.0000) | (0.0586, 0.0285, 0.0000) |
| Pyromancy Flame | izq. | 200 | (−0.0128, 0.0165, 0.0099) | (0.0572, 0.0056, −0.0099) |
| Cualquiera de los tres | der. | 1 | (−0.0141, −0.0036, −0.0001) | (0.0559, 0.0185, −0.0001) |
| Cualquiera de los tres | izq. | 1 | (−0.0153, 0.0064, 0.0099) | (0.0547, 0.0157, −0.0099) |

- La dummy 1 del arma cae siempre sobre la dummy 20/21 del cuerpo (es la que se usa para engancharla): Lightning Spear
  sale del **agarre**, a ~5.9 cm del origen de `R_Hand`. No es la dummy 1 de c0001.
- Bastón a dos manos con la derecha (cuerpo 20 / arma 3): 200 en `R_Weapon` (rig) = (−0.0303, −0.0037, 0.6871).
- La columna de `R_Hand` solo vale con `R_Weapon` en su pose de reposo. **Varias animaciones de lanzar giran
  `R_Weapon`**: p. ej. `a38_11_0020` ~30°, `a38_11_0220` ~85° y 2 cm (medido con `nsa.pose`). Hay que usar el hueso
  animado.

### En el rig de Minecraft (`ds2_anims.py`)
- El brazo de Minecraft se mapea de `R_UpperArm` a `R_Hand` (`LIMBS`), y el objeto en la mano sigue `R_Weapon`
  (`ITEMS`, `_items` = orientación de `R_Weapon` relativa al pecho).
- El origen se obtiene igual que el resto del retarget, sin datos nuevos: en cada fotograma, con `wp, wq = nsa.world(...)`,
  `origen_modelo = wp[R_Weapon] + rot(wq[R_Weapon]) · offset_rig` (tabla de arriba), luego `to_mc_vec` y relativo
  al pecho (`inv_body`), a la escala `PX_PER_M = 12 / 0.97` (la de las piernas; es una elección del retarget, no de DS2).
- El brazo de Minecraft es rígido y no tiene muñeca: colocar el punto sobre el brazo retargeteado (en vez de sobre el
  esqueleto de DS2) obliga a decidir cómo se proyecta; eso no sale de DS2 y **queda sin decidir**.

## Pendiente / sin confirmar
- Qué campo es `obj+0x14 + mano·4` (arma activa) y qué valor de `obj+0x10` es cada postura (§3).
- Que la fila de los ids de enganche sea WeaponTypeParam (coincide con todos los datos; el puntero no se trazó).
- El código de la dummy que elige el hueso padre / de enganche (se dedujo de los valores).
- Otros catalizadores: solo se extrajeron los tres modelos de referencia; los demás tienen su propia dummy 200.
- Con varios bullets / disparos el id es `+0x31` + contadores (casting.md §7.2); los hechizos de referencia usan uno.
