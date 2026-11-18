# Catalizadores y daño de hechizos

Fuente: `DarkSoulsII.exe` (SotFS), desofuscado con `D:\Reversing\DS2\out\py\deob.py`. Salidas en
`D:\Reversing\DS2\out\spell\` (`sp6.c` usuarios de SpellParam, `f_1403936a0.txt` constructor del golpe de hechizo,
`f0050.txt` elección de bala/PDP, `d19.c` / `d13a.c` usos de `PDP+0x19..+0x1b`, `f4d0.txt` descriptor de la otra mano)
y las de [damage.md](damage.md) (`out\damage\f1fe0.txt`, `crit_391bf0.txt`, `crit_14038fee0.txt`).
Datos: regulation (`WeaponParam` WP, `WeaponTypeParam` WTP, `WeaponReinforceParam` WRP, `WeaponStatsAffectParam` WSA,
`WeaponActionCategoryParam`, `WeaponAttackMotionParam` WAMP, `SpellParam`, `SpellSoulConsumeParam`, `BulletParam`,
`PlayerDamageParam` PDP, `DamageCtrlParam` DCP, `PhysicalStatsPerLevelStatValuesParam` PSPL). Nombres: `ItemName.fmg`.

Getters del gestor de params (`*(DAT_1416148f0 + 0x18)`): SpellParam `FUN_140359030` (slot `+0x540`),
SpellSoulConsumeParam `0x140359000` (`+0x550`), WeaponParam `FUN_140359150`, WeaponTypeParam `FUN_140359210`.
BulletParam se carga aparte (`FUN_14048b510`).

## 1. Qué es un catalizador

### Lo confirmado: el slot de acción de tipo 12 lanza el hechizo
- El "tipo de movimiento" del descriptor de ataque (`desc+0x10`) es `WAMP +0x04` (lo llena `FUN_140393c00`, ver
  [stamina.md](stamina.md)). El valor **12 (`0xc`) es "hechizo"**:
  - `FUN_140391bf0` (constructor del slot de golpe con id de PDP explícito) solo con `desc+0x10 == 0xc` lee
    `desc+0x70` como fila de `SpellParam` (`+0xb4`, `+0xba`).
  - `FUN_140391fe0` (valor del golpe, damage.md §1) tiene ramas propias para `desc+0x10 == 0xc` y para
    `desc+0x70 ≠ 0` (abajo, §3).
  - `FUN_1401fdc50` / `FUN_1401feab0` (peticiones de acción) guardan el tipo `0xc` junto con la fila de SpellParam.
- En la regulation, los slots con `WAMP +0x04 = 12` son:
  - bastones, campanas, llamas y el Black Witch's Staff (categorías 3800, 4010, 5400, 4150…): slots
    **0, 1, 5, 10, 11, 15, 20, 21, 25, 30, 31, 35, 40, 41, 45** (ligeros y tras rodar, en todos los agarres);
  - Blue Flame (1280) y Mace of the Insolent (2470): slots 2, 3, 12, 13, 22, 23, 32, 33, 42, 43 (los fuertes).
  - La Longsword (1220) no tiene ninguno.
- En los catalizadores, el resto del moveset (fuertes 2/3, 12/13, 42/43) son golpes normales (`WAMP +0x04 = 2`,
  ver §4).

### Bytes `WTP +0x00..+0x03` y `+0x53`: qué escuela admite el arma (confirmado)
Lector: **`FUN_140397a30(spell, wtp)`** (desensamblado limpio): `SpellParam+0x00` 0 → `WTP+0x00 ≠ 0`, 1 → `+0x01`,
2 → `+0x02`, 3 → `+0x03`, 4 → `+0x53` (según [spells.md](spells.md); los casos 0..3 los comprobé yo). La llamada sale de
`FUN_140396510` dentro de las comprobaciones de lanzar (`FUN_140395930` → `FUN_140396ba0` → `FUN_1403963b0`, ver
spells.md). Fila WTP = `WP +0x10`:

| Arma | WTP | `+0x00..+0x03`, `+0x53` |
|---|---|---|
| Bastones (Sorcerer's 3800000, Amana, Lizard, Olenford's, Archdrake, Bat, Bone, Sunset, Azal's, Retainer, Transgressor's…) | 330..343, 730 | 1 0 0 1, 0 |
| Staff of Wisdom 3890000, Blue Flame 1280000, Sorcerer's Twinblade 5370000 | 341, 145, 500 | 1 0 0 0, 0 |
| Campanas (Cleric's Sacred Chime 4010000, Priest's, Dragon, Chime of Want, Archdrake, Idol's, Protective, Disc, Screams…), Mace of the Insolent | 350..357, 650, 201 | 0 1 0 0, 1 |
| Caitha's Chime 4090000 | — | 0 0 0 0, 1 |
| Pyromancy Flame 5400000 / Dark Pyromancy Flame 5410000 | 11 | 0 0 1 0, 0 |
| Black Witch's Staff 4150000, Sanctum Shield 11150000 | 340, 580 | 1 1 0 1, 1 |

- **Si no puede** (o faltan usos o stats): la comprobación da el código 1 y `FUN_140395930` pide la animación
  `0x24596d6` = 38115030 `a38_11_5030_StickSpellNoBullet` sin fila de hechizo: el gesto sin bala y sin gastar nada
  (spells.md; no lo volví a trazar).

### Escuela del hechizo
`SpellParam +0x00` (u32) 0..4. Orden **confirmado por los datos de los SpEffect** que multiplican los factores de escuela
(§6): Sun Seal (milagros) toca solo el índice de la escuela 1, Penal Handcuffs (piromancia) solo la 2, Abyss Seal (hexes)
las 3 y 4. Ids: 31xxxxxx = 0, 32xxxxxx = 1, 33xxxxxx = 2, 34xxxxxx = 3, 35xxxxxx = 4 (spells.md).

## 2. "Spell buff" del catalizador: es el mismo poder de ataque de damage.md
No hay un campo aparte. El golpe del hechizo se construye con **el arma de la mano que lanza** y la misma función que
los ataques normales:
- `FUN_1403936a0(ctx, desc, …)` (hechizo): `FUN_140390050` → ids; luego `FUN_140391bf0(ctx, desc, slot = desc+0x1c, …,
  pdp, 1)` → `FUN_140391fe0` con el descriptor del catalizador (`desc+0x48` WP, `+0x50` WTP, `+0x30` nivel/infusión,
  `+0x38` WRP, `+0x40` WSA, los mismos campos que usa un golpe normal).
- Por tipo i (0 físico, 1 magia, 2 rayo, 3 fuego, 4 oscuro):
  `valor[i] = max(0, (S[i] + B[i])·M[i] + F[i]) · k` (bucle `0x141b8ba3d`), con:
  - **B** = `lerp(WRP[i], WRP[i+9], nivel / WRP+0x48) × WSA+0x00` (`FUN_14034e820`). Se omite si `desc+0x29 ≠ 0`
    (`FUN_140390ea0`; significado sin confirmar, en uso normal 0).
  - **M** = `WRP +0xa0 + 4i` × 0.01 (`FUN_14034c580` → `FUN_14034c760`) + resinas, ≥ 0.
  - **S** = bonos de stats × coeficientes del nivel en WSA (`FUN_1403903b0`):
    magia `PSPL[Int]+0x14 × c[2]`, rayo `PSPL[Fe]+0x1c × c[3]`, fuego `PSPL[⌊(Int+Fe)/2⌋]+0x18 × c[4]`,
    oscuro `PSPL[min(Int, Fe)]+0x20 × c[5]` (tabla de damage.md).
  - **F** = daño plano del **PDP del hechizo** (`FUN_14038fee0` → `FUN_14038f2d0`): `F[código] += valor` para
    `(+0x0c, +0x19)`, `(+0x10, +0x1a)`, `(+0x14, +0x1b)`. Tabla de `FUN_14038f2d0` (`0x14038f370`): código 0..4 →
    índice 0..4; 6, 7, 8 → 5, 6, 7; 12 → 8; 9 → 9; 5, 10, 11 → nada.
  - **k** = `k1 × WTP[mano] × ChrParam+0xd8` (damage.md). `k1` usa la falta de requisitos **del catalizador**
    (`WP +0x18..+0x1e`: Sorcerer's Staff Int 10, Cleric's Chime Fe 10, Pyromancy Flame 0). `WTP +0x54 / +0x60` = 1.0 /
    1.0 en las filas 336, 356 y 11.
- El reparto `M` decide qué elementos tiene el catalizador:

| Catalizador (WRP) | Base +0 → máx. (nivel máx.) | `M` (fís/mag/rayo/fuego/osc) | c[2] Int / c[3] Fe / c[4] fuego / c[5] osc (+0 → +máx.) |
|---|---|---|---|
| Sorcerer's Staff (3800) | 112 → 225 (+10) | 0 / 1 / 0 / 0 / 1 | 0.50 / 0.45 / 0.45 / 0.50 → 0.55 / 0.50 / 0.50 / 0.55 |
| Cleric's Sacred Chime (4010) | 90 → 180 (rayo 180, osc. 160) (+10) | 0 / 0 / 1 / 0 / 1 | 0.45 / 0.70 / 0.45 / 0.50 → 0.50 / 0.75 / 0.50 / 0.55 |
| Pyromancy Flame (5400) | 125 → 250 (+10) | 0 / 0 / 0 / 1 / 0 | 0.45 / 0.45 / 0.80 / 0.45 → 0.50 / 0.50 / 1.30 / 0.50 |
| Dark Pyromancy Flame (5410) | 150 → 300 (+10) | 0 / 0 / 0 / 1 / 0 | c[4] 1.00 → 1.10 |
| Black Witch's Staff (4150) | 110 → 220 (+10) | 0 / 1 / 1 / 1 / 1 | 0.90 / 0.50 / 0.45 / 0.90 |
| Staff of Wisdom (3890) | 180 (+5) | 0 / 1 / 0 / 0 / 0 | 1.10 / … |

  WSA = `WRP+0x4c` (3800 → 1010010, 4010 → 1014010, 5400 → 1019000); `WSA+0x00` = 1.0. La "llama de piromancia" sube
  de nivel como cualquier arma (WRP 5400, `+0x48` = 10); el coste de mejora no está en esta nota.
- PSPL (bono por valor del stat):

| Stat | 5 | 10 | 13 | 20 | 22 | 30 | 40 |
|---|---|---|---|---|---|---|---|
| `+0x14` (magia, Int) | 51 | 53 | 68 | 106 | 106 | 110 | 140 |
| `+0x1c` (rayo, Fe) | 53 | 57 | 63 | 80 | 84 | 102 | 140 |
| `+0x18` (fuego, ⌊(Int+Fe)/2⌋) | 61 | 72 | 85 | 117 | 123 | 147 | 155 |
| `+0x20` (oscuro, min(Int, Fe)) | 61 | 72 | 85 | 117 | 126 | 162 | 170 |

### Elementos del catalizador que no son del hechizo: no hay filtro en todo el camino (ver §6)
Por el código leído, el golpe de un Soul Arrow lanzado con el Sorcerer's Staff lleva **magia y oscuro** (el staff tiene
`M[1] = M[4] = 1`), y un Lightning Spear con la campana, **rayo y oscuro**. **No hay ningún filtro** que deje solo
el elemento del hechizo (recorrido de la bala de punta a punta en §6):
- no está en `FUN_140391fe0`, `FUN_140391bf0`, `FUN_14034c580` / `FUN_14038f440` (M), `FUN_14016d070` (golpe desde el
  slot), `FUN_140374fa0` / `FUN_1403750b0` (copias del slot) ni en `FUN_1401373b0` (estructura `A` de la víctima);
- el único filtro por `+0x19..+0x1b` que existe es el de las balas de SpEffect (`FUN_1402162a0`: copia solo los tipos
  `+0x19/+0x1a/+0x1b` de su fila), que **no** es el camino del jugador;
- `FUN_14013a3f0` sí usa `PDP+0x19..+0x1b` = 1/2/3/4 como **elemento de la bala** (para efectos de la víctima con los
  SpEffect 0xc/0xd/0xe/0x1e), pero no toca el daño.
Queda abierto solo lo que no se puede leer en el exe: que el juego real muestre ese daño oscuro (no hay forma de
comprobarlo desde los params).

## 3. Daño del hechizo al impactar
### Qué fila de PDP y qué bala (`FUN_140390050`)
- `SpellParam +0x0c` = id de **BulletParam**, `+0x10` = id de **PlayerDamageParam** (en las filas probadas ambos = id
  del hechizo).
- A cada uno se le suma un desplazamiento de `FUN_140391900` según `desc+0x08` y el agarre `desc+0x04`
  (`desc+8 == 1`: [0, 0, 0, 200, 400, 600, 0]; `== 2`: [0, 0, 100, 300, 500, 700, 100]); solo se usa si la fila
  desplazada existe (`FUN_140132120` bala, `FUN_140170cf0` PDP). **Ningún hechizo de la tabla tiene esas filas**, así
  que queda el id base. Qué es `desc+0x08`: sin confirmar.
- Si `SpellParam +0xfc ≠ 0`: fila de `SpellSoulConsumeParam`; el PDP se elige entre `+0x08..+0x2c` según
  `(fila+0x04 − chr+0x490→+0xec) / fila+0x04` (hechizos que gastan almas). Ninguno de los ejemplos lo usa.
- Con `SpellParam +0x04 ≠ 0`, `FUN_1403936a0` también arma el golpe con la otra mano (`FUN_140392890` →
  `FUN_14038f4d0`). Los ejemplos tienen 0.

### Campos del PDP de un hechizo
| Hechizo (SpellParam = PDP = Bullet) | Escuela | `+0x08/+0x0a` u16 | Plano `+0x0c` (código `+0x19`) | Piso `+0x18` | Tasa `+0x1c` | Estamina `+0x24` | DCP `+0x00` |
|---|---|---|---|---|---|---|---|
| Soul Arrow 31010000 | 0 | 10 / 0 | 60 (1 magia) | 61 | 0.85 | 620 | 81060000 |
| Great Soul Arrow 31020000 | 0 | 14 / 0 | 150 (1) | 64 | 0.90 | 775 | 81060000 |
| Heavy Soul Arrow 31030000 | 0 | 12 / 0 | 70 (1) | 67 | 0.95 | 775 | 81060010 |
| Lightning Spear 32140000 | 1 | 0 / 22 | 0 (2 rayo) | 100 | 1.50 | 620 | 100000060 |
| Great Lightning Spear 32150000 | 1 | 0 / 42 | 0 (2) | 115 | 1.75 | 620 | 100000070 |
| Fireball 33010000 | 2 | 0 / 0 | 0 (3 fuego) | 55 | 0.75 | 930 | 81062020 |
| Great Fireball 33030000 | 2 | 0 / 0 | 0 (3) | 115 | 1.75 | 1240 | 81062020 |
| Dark Orb 34010000 | 3 | 12 / 10 | 0 (4 oscuro) | 94 | 1.40 | 930 | 81063010 |

- `+0x1a/+0x1b` = 5 en todos (código 5 → no suma a F; en `FUN_140391fe0` va a `golpe+0x14`).
- `+0x08/+0x0a`: el menú los copia (`FUN_1401b8e50`, junto a la escuela y `+0xf0`). **Etiqueta sin confirmar.**
- DCP de los hechizos: subtipo `+0x00` = 3, `+0x08` = 0 (sin penetración de guardia), `+0x11` = 4 / 5 / 6 / 7 (magia /
  rayo / fuego / oscuro en estas filas; uso sin trazar). Caída por distancia `+0xa4 / +0xa8 / +0xac`:
  Soul Arrow 20 / 40 / −50, Lightning Spear 12 / 30 / −70, Fireball 20 / 40 / −50, Dark Orb 20 / 40 / −50.
  Con `FUN_140136ab0` (stamina.md) sería −50 % entre `20·hit+0xdc` y `40·hit+0xe0` m; **qué valen `hit+0xdc/+0xe0`
  en una bala no está confirmado** (en cuerpo a cuerpo son 1.0).

### Lo propio de los hechizos en `FUN_140391fe0`
- `golpe+0x6c` (= `golpe+0xb4` de damage.md) = `b8+0x420`; el factor extra `b8+0x428` **no** se aplica con
  `desc+0x10 == 0xc` (`0x14072f53c`).
- `golpe+0x70` (= `A+0x7c`) = `WP+0x80` (catalizadores: 1.0) × **factor de escuela del atacante**: con
  `desc+0x70 ≠ 0`, `SpellParam+0x00` 0/1/2/3/4 → `b8 +0x48c / +0x490 / +0x494 / +0x498 / +0x49c`; sin hechizo, 1.0
  (`DAT_1410ac698`). Valen 1.0 por defecto y los cambian SpEffects (§6).
- `golpe+0x87` = 1 con hechizo (si no, `WTP+0xa1`). Significado sin confirmar.
- `FUN_140391bf0` pasa al slot `WP+0x40 × SpellParam+0xb4` y, si `WTP+0x100 > 0` y `SpellParam+0xba > 0`,
  `WAMP+0x18`. Significado sin confirmar (los ejemplos: `WP+0x40` = 1.0, `+0xb4` = 1.0, `WAMP+0x18` = 0).

### Lanzamiento y bala
- TAE de lanzamiento: `SpellParam +0x14 + n·0x14` (8 bloques `{f32, f32, u32 TAE, u32 TAE, f32}`; Soul Arrow 38110010 /
  38110020). El bloque lo elige `thunk_FUN_141c37a31`; **el índice n no está decodificado**.
- El TAE 38110020 tiene dos eventos **2300** a 0.567–0.667 s con `param[0]` = 0 y 5. `ChrMorphemeTimeActTrackBulletCtrl`
  (`FUN_140325880`): el 2300 busca el slot `param[0]` en `chr+0x230` (`FUN_1403740e0`) y crea la bala
  (`FUN_140373c00`); el 2301 crea una bala con id explícito (`FUN_140373880`). La bala usa el **mismo slot de golpe** que
  arma `FUN_140391bf0` (`FUN_1403747b0`).
- Velocidad de lanzamiento (`b8+0x1b8`, `FUN_1403936a0`): `… × WTP+0x10c` (staff 0.65, campana 0.60, llama 1.0) × un
  float de las derivadas (`+0x38`) × `FUN_14038f9d0` × `b8+0x488`. **Sin decodificar.**
- Usos: `FUN_1401ab750` = `SpellParam[+0xf0 + derivada(+0x2f)] × b8+0x484`. Sin decodificar.
- Estamina del lanzamiento: el coste de ataque usa `WP +0x38` para los tipos de movimiento 8..12 (stamina.md):
  Sorcerer's Staff 233, Cleric's Chime 219, Pyromancy Flame 315, × `WeaponStaminaCostParam[WP+0x3c = 10]` (slot 0:
  0.95 → 221.35 / 208.05 / 299.25). **Cuándo se cobra en un lanzamiento** (no hay evento 2200): sin confirmar.

### Víctima
- El golpe tiene el mismo formato que el de un arma (mismo slot, mismos campos), así que lo leen `FUN_1401373b0` /
  `FUN_140138d50` como en damage.md §4: `μ`, `D` (`DamageAdjustParam`, de la **víctima**, no dependen del ataque),
  defensa, absorción, piso `PDP+0x18`, tasa `PDP+0x1c`, `A+0x7c`. Enemigo y jugador: elementos ×6 / ÷6.
  - La bala registra su golpe con la misma función que el cuerpo a cuerpo (`FUN_1403b09d0`, §6), así que llega a la
    víctima igual.
- Resumen (enemigo, sin guardia, sin efectos, NG, antes de la caída por distancia), por elemento e del hechizo:
  `d_e = max(piso, 6·valor[e]) / 6 · (1 − abs_e) · tasa · A+0x7c`; total `trunc(Σ d_e)`.
- Guardia: `pen = 0.01·DCP+0x08 × WP+0x90 × M` = 0 (DCP+0x08 = 0). Daño de estamina en guardia: base `PDP+0x24`
  (tabla), fórmula de stamina.md. Poise: no trazado.

## 4. Moveset cuerpo a cuerpo
- Los catalizadores tienen categoría propia en `WP +0x0c` (Sorcerer's Staff 3800, Cleric's Chime 4010, Pyromancy Flame
  5400, Black Witch's Staff 4150) y el exportador puede leerla igual que las demás (`WeaponActionCategoryParam`).
- Ligeros (slots 0/1, 10/11, 40/41, rodando 5/15/45) = **hechizo** (WAMP tipo 12, TAE 20010010 genérico); el TAE real
  sale de SpellParam. El exportador debe saltarse esos slots o tratarlos aparte.
- Fuertes = golpes normales (tipo 2):

| Arma | Slot 2 / 3 (1M) | 12 / 13 (2M) | PDP 1M F1 (piso, tasa) |
|---|---|---|---|
| Sorcerer's Staff 3800 | 24010010 / 24010020 | 37030021 / 37030011 | 20040700 (25, 0.30) |
| Cleric's Chime 4010 | 20010010 / 20010020 | 20030020 / 20030030 | 20041500 (25, 0.30) |
| Pyromancy Flame 5400 | 20010010 / 20010020 | 20030020 / 20030030 | 20041900 (25, 0.30) |

  - El golpe usa el mismo `M` del catalizador: el bastón pega **magia y oscuro**, la campana **rayo y oscuro**, la llama
    **fuego**, sin físico (`M[0]` = 0). Los PDP de estos golpes no tienen daño plano (`+0x19..+0x1b` = 0).
  - Pyromancy Flame: `WP +0x04` = 3400 (el de Fists), las demás `+0x04` = su propia categoría. Uso de `WP+0x04`: sin
    confirmar.

## 5. Ejemplos (NG, sin efectos ni resinas, mano derecha, estamina ≥ coste, nivel +0)
Con `A+0x7c` = 1.0: factor de escuela `b8+0x48c..` = 1.0 (valor del ctor, sin anillos ni efectos; §6). Víctima enemigo con absorción
`abs` del elemento; `trunc` al final. Dentro de la distancia sin caída.

### A. Sorcerer's Staff +0, Int 20 / Fe 5, Soul Arrow
- `k = 1` (Int ≥ 10). `B = 112`.
- Magia: `S = 106 × 0.50 = 53`; `F = 60` → `valor[1] = (53 + 112)·1 + 60 = 225`.
- Oscuro (el código no lo filtra, §6): `S = PSPL[5]+0x20 = 61 × 0.50 = 30.5` → `valor[4] = 142.5`.
- Víctima: magia `max(61, 1350)/6 · 0.85 = 191.25`; oscuro `max(61, 855)/6 · 0.85 = 121.125`.
  - Total con `abs` 0: `trunc(312.375)` = **312**. Solo la magia: 191 (con `abs` magia 30 %: 133).

### B. Cleric's Sacred Chime +0, Fe 22 / Int 5, Lightning Spear
- `k = 1` (Fe ≥ 10). `B = 90`.
- Rayo: `S = 84 × 0.70 = 58.8` → `valor[2] = 148.8` (sin plano).
- Oscuro (el código no lo filtra, §6): `S = 61 × 0.50 = 30.5` → `valor[4] = 120.5`.
- Víctima: rayo `max(100, 892.8)/6 · 1.5 = 223.2`; oscuro `max(100, 723)/6 · 1.5 = 180.75`.
  - Total con `abs` 0: `trunc(403.95)` = **403**. Solo el rayo: 223.

### C. Pyromancy Flame +0, Int 10 / Fe 10, Fireball (sin ambigüedad: la llama solo tiene fuego)
- `k = 1` (sin requisitos). `B = 125`.
- Fuego: `S = PSPL[10]+0x18 = 72 × 0.80 = 57.6` → `valor[3] = 182.6`; los demás 0.
- Víctima: `max(55, 1095.6)/6 · 0.75 = 136.95` → **136** con `abs` 0; **95** con `abs` 30 % (95.865).
- La misma llama a +10 (`B = 250`, `c[4]` = 1.30): `S = 93.6` → `valor = 343.6` → `257.7` → **257**.

## 6. Seguimiento: la bala, los factores del atacante
### De TAE 2300 al registro del golpe: el vector se copia entero
1. Lanzar: `FUN_1403936a0` → `FUN_140391bf0` → `FUN_140391fe0` escribe el golpe (5 elementos del catalizador) en el
   slot de `chr+0x230` (`FUN_1403747b0`).
2. Evento 2300 (`FUN_140325880`): `FUN_140373c00` mete el slot (0xc0 bytes, `FUN_140374fa0` = copia literal) en la
   lista de balas pendientes del gestor (`+0x150`, cuenta `+0x458`).
3. Cada frame `FUN_140374ad0` recorre esa lista y llama a `FUN_1403751c0` por entrada: copia ids (`+0x04`: bala, PDP) y el
   golpe (`+0x24..`) tal cual → `FUN_140337fb0` (los copia otra vez tal cual, `param_2[4..0x16]`) →
   `FUN_1404415a0` (gestor de balas `DAT_1416148f0+0x68`).
4. `FUN_140445020` (inicio de la bala): `bala+0x80` = copia de 0x9c bytes del golpe de la petición (`+0x8c`), sin tocar;
   `bala+0x2c` = id de bala, `+0x30` = PDP, `+0x58` = fila de BulletParam; velocidad `= petición+0x7c × BulletParam+0x48`.
5. `FUN_140446270` (bala activa): copia `bala+0x80` (0x9c bytes) a la petición y la registra con **`FUN_1403b09d0`**
   (`DAT_1416148f0+0x10`). Es la misma función que usa el golpe cuerpo a cuerpo (`FUN_1403186a0`, que también copia los
   0x9c bytes del slot) y los objetos del mapa (`FUN_1403f1ab0`). La víctima recibe el mismo formato → damage.md §4.
6. Sub-balas (explosiones; `BulletParam +0xd4/+0xd8`, `+0xdc/+0xe0`, `+0xe4/+0xe8` = pares bala/PDP, p. ej. Fireball
   33010001/33010010): `FUN_140445ec0`. Si el atacante es de los tipos marcados en `DAT_1410bfff3[tipo·5]` (que el jugador
   esté marcado: sin comprobar) **heredan el vector de la bala madre** y solo cambian `+0x14` (suma de
   los planos de código 5 del PDP hijo). Si no, ponen a 0 los elementos y usan solo el daño plano del PDP hijo por
   `+0x19..+0x1b`.
- **En ningún paso hay máscara por elemento.** Con el código, un Soul Arrow del Sorcerer's Staff pega magia + oscuro, un
  Lightning Spear de la Cleric's Chime rayo + oscuro. `PDP+0x19..+0x1b` solo filtran en las balas de SpEffect
  (`FUN_1402162a0`) y en las sub-balas de atacantes no marcados.
- BulletParam no tiene campos de ataque: solo física (velocidad `+0x48`, etc.) y los ids de sub-balas.
- Lo que no recorrí: el interior de `FUN_1403b09d0` y el reparto a la víctima (es el mismo que el del cuerpo a cuerpo).

### Factores `b8+0x3bc..` (incluye `+0x420`, `+0x428`, `+0x484`, `+0x488`, `+0x48c..+0x49c`)
- Son un arreglo de multiplicadores: índice i = `b8+0x3bc + 4i`. El ctor de `b8` (`FUN_14031bcf0`, bucle en
  `0x14031c2a0`: `rdx = 0x3f8000003f800000`) pone **1.0** en todo el arreglo (desde `+0x3bc` en adelante; ya lo dice
  equipload.md).
- Los cambia la instrucción de SpEffect `1000[01] (índice, x, valor)` (`SpEffectActionImpl_ChrStatus_Rate`,
  `FUN_14021bd70`; multiplica, ver hollowing.md). Índices: `+0x420` = 25, `+0x428` = 27, `+0x484` = 50,
  `+0x488` = 51, `+0x48c..+0x49c` = 52..56 (escuela 0..4).
- Filas de los `SpEffect*.emevd` (decodificadas con `out\hollow\evd.py`; nombre = id de objeto en `ItemName.fmg` si
  existe):

| SpEffect | Índice / campo | Valor |
|---|---|---|
| 40770000 Sun Seal | 53 (escuela 1) | ×1.05 |
| 40750000 Abyss Seal | 55, 56 (escuelas 3, 4) | ×1.075 |
| 26260102 Penal Handcuffs | 54 (escuela 2) | ×1.075 |
| 21502100 Black Witch Domino Mask | 52..56 | ×1.03 |
| 21660100 Old Bell Helm | 53, 56 | ×1.015 |
| 3930000 Azal's Staff | 52, 55 | ×1.025 |
| 4110000 Disc Chime | 53 | ×1.03 |
| 1140000 Blue Dagger | 52..56 | ×1.05 |
| 31290001 (Unleash Magic 31290000) | 52..56 | ×1.2 |
| 40260000 Red Tearstone Ring | 27 (`b8+0x428`, no se aplica a hechizos) | ×1.2 |
| 40330000/01/02 Clear Bluestone Ring | 51 (`b8+0x488`, velocidad de lanzamiento) | ×0.8 / 0.65 / 0.6 |
| 22190101..03 Lion Mage Robe, 27710100 Chaos Hood | 51 | ×0.95 |
| 27680100 Black Hood | 51 | ×0.9 |
| 900301, 60406020, 1990010 y varios de enemigos | 25 (`b8+0x420`) | 0.95 / 1.15 / 1.05 / … |

- **Cuándo se activa cada SpEffect** (equipado, al lanzar, condición de vida, duración `x`): no lo seguí. Sin ninguno,
  todos valen 1.0.

## 7. Ítems del mod (0.3.1, pedido del usuario 2026-11-09)
- `soulsmode:sorcerers_staff`, `soulsmode:clerics_chime`, `soulsmode:pyromancy_flame` (`SoulsItems`): atacan como
  las entradas de `weapons.json` del mismo nombre (WeaponParam 3800000 / 4010000 / 5400000).
- Fila de `ItemParam` con el mismo id: pila `+0x4a` = 1. Sprite en pixel art propio (`tools/item_sprites.py`, regla del
  usuario: nunca el ícono de DS2 reducido), con la forma del ícono `IC_0003800000` / `IC_0004010000` / `IC_0005400000`. Nombre: `itemname` del mismo id. DS2 no tiene descripción corta para estas armas (`SimpleExplanation` vacío;
  solo `DetailedExplanation`), así que el ítem no muestra descripción.
- Peso = `WeaponParam +0x20` exacto: 2.0 / 0.5 / 0.0 (`SoulsEquipLoad`).
- Requisitos (`WeaponParam +0x18..`): bastón Int 10, campana Fe 10, llama ninguno.
- Recetas (diseño del usuario, no DS2; `data/soulsmode/recipe/`): bastón = pepita de hierro sobre un palo; campana = 2 lingotes de cobre sobre una campana, en columna; llama = Llama de la adversidad sobre un cuero. Sin botín. También en la pestaña de
  combate del creativo. Sin hechizos de datapack no lanzan nada (el
  ataque con catalizador sin hechizo es el lanzamiento fallido de DS2).
- No portado: durabilidad (`WeaponParam +0x28`: 30 / 30 / 50); backlog, Simplificaciones.

## Sin confirmar
- El interior de `FUN_1403b09d0` (se supone el mismo reparto que el cuerpo a cuerpo) y que el juego real muestre el
  daño oscuro de los hechizos de bastón/campana (no se puede ver en los params).
- Las condiciones de activación de los SpEffect de la tabla de §6.
- El índice del bloque de animación de SpellParam, `desc+0x08` (desplazamiento del PDP) y `golpe+0x87`.
- `hit+0xdc/+0xe0` de las balas (escala de la caída por distancia).
- Poise de los hechizos; momento del cobro de estamina al lanzar.
- La etiqueta de `SpellParam +0x08/+0x0a` (spells.md los usa como requisitos de Int/Fe en `FUN_1403978c0`).
