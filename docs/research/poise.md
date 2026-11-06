# Estabilidad (poise) del jugador y su propia reacción al golpe

Objetivo: todo lo necesario para que el jugador tenga la estabilidad de DS2 y reaccione cuando le pegan: cuánta tiene,
cómo se recupera, cuánto le quita cada golpe, qué pasa si aguanta (reacción 1) y qué pasa si no aguanta.

Fuente: `DarkSoulsII.exe` (SotFS) en Ghidra headless (`D:\Reversing\DS2\ghidra.sh`, scripts `Decompile`, `Disasm`,
`FindScalar`, `Xrefs`, `DecompileRange`), código ofuscado con `out\py\deob.py`, bytes del exe con
`tools/re/disasm.py`. Salidas en `D:\Reversing\DS2\out\poise\`:
- `p1.c` (`FUN_14037fdd0`, `FUN_140381080`, `FUN_140137aa0`, `FUN_140139160`, `FUN_140139380`, `FUN_14036c310`,
  `FUN_140203c10`, `FUN_140145a40`), `p2.c` (`FUN_14031c460`, `FUN_14031c580`, `FUN_140348690`, `FUN_14031e680`),
  `p3.c` (`FUN_1403811f0`, `FUN_14035e820`), `p4.c` (`FUN_14034a830`, `FUN_14034d150`), `p5.c` (`FUN_140385fe0`,
  `FUN_14035ef90`), `p7.c`/`p8.c` (reparto `M`), `p9.c`, `r36c.c` (`FUN_14036c3f0`, `FUN_14036c2f0`, …), `std.c` /
  `tstd.c` (ScaleToDuration), `agt.c`, `mgr.c` (`FUN_14048ba80`).
- `d136570.txt`: desensamblado completo de `FUN_140136570`. Tabla de saltos en `0x140136984` leída del exe.
- `sc305.txt` / `sc724.txt`: escritores y lectores de `b8+0x305`, `+0x7d0`, `+0x3a0`, `+0x720`, `+0x724`.
- Ya existentes: `out\enemies\poise.c`, `out\enemies\hit1.c` (`FUN_1401345c0`, `FUN_140136220`, `FUN_140136570`),
  `out\f6240.c` (eventos TAE, `FUN_140326240`), `out\agl\derive.c` (`FUN_14038d790`), `out\cnt.c`
  (`FUN_140136ab0`), `out\hitreact\p391fe0.txt`.

Datos: `ArmorParam`, `PlayerLackOfStatsParam`, `PhysicalStatsPerLevelStatValuesParam` (PSPL), `ChrParam` 100,
`ChrCommonParam` (CCP) 100, `PoiseDamageParam`, `DamageCtrlParam` (DCP), `EnemyDamageParam` (EDP),
`PlayerDamageParam` (PDP), `WeaponParam` (WP), `DamageMotionWeightParam`, TAE `c000100_pl.tae`, `c0001.nmb`,
`SpEffect*.emevd` (volcados de `out\hollow\evd.py`), `itemname`.

Script y datos: **`tools/re/poise.py`** → **`src/main/resources/data/soulsmode/ds2/poise.json`** (§9).

Lo que ya está en otras notas y no se repite: la cadena de reacción y los clips del jugador
([hit_reaction.md](hit_reaction.md) §1–§2, §9–§12), el orden del golpe ([stamina.md](stamina.md) "Orden dentro de un
golpe"), los campos de `A` y del golpe ([damage.md](damage.md) §1, §4), el reparto del set de armadura del mod
([equipload.md](equipload.md) §7).

## 1. Resumen
- **Máximo** = armadura (4 piezas `ArmorParam +0x40`, con castigo si faltan stats) + bono por stats
  (PSPL `+0x90` en `min(Aguante, Adaptabilidad)`) + `ChrParam 100 +0x118` (0) + SpEffects (anillo de los Gigantes,
  Piel de Hierro).
- **Regeneración** = `ChrParam 100 +0x11c` = **0.56 por s**, fuera de la reacción. Si una reacción termina con la
  estabilidad en 0, **se rellena al máximo de golpe**.
- **Daño por golpe** = **`DamageCtrlParam +0xa0`** del golpe (Hollow Soldier 20 / 25 / 31.25; Ogro 35 / 43.75 / 94.5;
  arma del jugador a una mano 10 / 12.5). Corrige a enemies.md §7.1 (no es `golpe+0xa0` = 1.0).
- **Cuándo cuenta** la estabilidad lo decide `DCP+0x02`: 0 siempre, 1 solo si la víctima está en una ventana
  TAE 111900, 2 nunca. Los golpes del Hollow Soldier son 0; los del Ogro, 2 (siempre te hace reaccionar).
- **Si aguanta**: reacción 1. En la locomoción, la parte de arriba del cuerpo hace un `DmgKnockBackLight` parcial y la
  pelvis se inclina (HipsIK) durante 1.1 s. La velocidad de movimiento baja a ×0.7. **No corta un ataque ni una
  esquiva**: fuera de `BT_Move` no se ve nada.
- **Si no aguanta**: la reacción normal de hit_reaction.md con la columna 2: `DmgKnockBack` (1.3333 s / v), bloqueos
  de ataque, rodada y guardia, y `DmgExtraHeavy` / `DmgTumble` para los golpes fuertes del Ogro.
- **Superarmadura** (hyper armour): TAE 110500 / 110600 (rodada, paso atrás y algunos ataques) quitan la reacción
  aunque no haya estabilidad. TAE 111900 multiplica el daño de estabilidad recibido por `WeaponParam +0xb8` del arma que
  ataca (solo la Gyrm Axe del mod tiene ≠ 0: 0.6).

## 2. Máximo — `FUN_140145a40` (cada frame)
`máx = vt+0x1f8 + ChrParam+0x118 + (s8) b8+0x305 + (i32) b8+0x3a0` (enemies.md §7.1). En el jugador:

### 2.1 `vt+0x1f8` del jugador = `FUN_14037fdd0` (`PlayerGameParamCalculator`, vtable `0x1410e4ec8`)
```
equipo = chr vt+0x118 → vt+0x68
para i = 0..3 (cabeza, pecho, brazos, piernas):
    fila = equipo+0x298 + 0x30·i            (FUN_140348690; la ArmorParam de la pieza, la misma de equipload.md)
    si fila: suma += ArmorParam+0x40 · (1 − pen(0x1c, f_i))   (calc vt+0x70 = FUN_14031e680 → vt+0x68 = FUN_14031e660)
devuelve suma + bloque(chr+0x490)+0x4c         (FUN_14038b990)
```
- `f_i` = `equipo+0x2b8 + 0x30·i`, escrito por `FUN_14034a830` con `FUN_14034d150(pieza, stats)`:
  `f = clamp(Σ max(0, 1 − stat/req), 0, 1)` sobre Fuerza, Destreza, Int y Fe (stats 4..7, `FUN_14038d510`), con
  `req` = **u16 `ArmorParam +0x2c / +0x2e / +0x30 / +0x32`**; un `req` = 0 no cuenta.
  - Corrige a agility.md §2.3 ("ArmorParam no tiene esos valores"): sí los tiene. El set del Rey (oro del mod) pide
    Fuerza 14 / Destreza 12; Havel, Fuerza 20.
- `pen` = `FUN_1403811f0({0x1c, f, 0})` con `PlayerLackOfStatsParam` fila **0x1c = (30, 80, 0, 30)**:
  `pen = clamp(0.01·(30 + 50·f), 0, 1)` si `f > 0`; si no, 0. Con una falta mínima la pieza ya pierde el 30 %.
- No mira la mejora (`ArmorReinforceParam`) ni la durabilidad: solo la fila base.
- **Bono por stats** = bloque `+0x4c` = derivada `+0x20` de `FUN_14038d790` (`out\agl\derive.c`, `param_1 + 0x10` en
  `undefined2*`): **PSPL `+0x90` en la fila `min(stat[1], stat[8])`** = `min(Aguante, Adaptabilidad)` (stats.md:
  slot 1 = Endurance, slot 8 = Adaptability), con los stats efectivos y fila fuera de 1..99 → 1.

| min(Agu, Adp) | 1 | 5 | 6 | 10 | 15 | 20 | 25 | 30 | 35 | 40 | 50 | 99 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bono | 0.3 | 1.5 | 1.8 | 3.0 | 4.5 | 6.0 | 7.5 | 9.0 | 10 | 11 | 13 | 18 |

(0.3 por punto hasta 30; luego +0.2 por punto hasta 35 y +0.1 por punto desde 40. Tabla entera en `poise.json`.)

### 2.2 Sets del mod (`ArmorParam +0x40`, reparto de `SoulsEquipLoad`: casco = cabeza, peto = pecho, pantalones = piernas, botas = brazos)

| Minecraft | Set DS2 (ArmorParam) | Casco | Peto | Pantalones | Botas | Total | Requisitos |
|---|---|---|---|---|---|---|---|
| Cuero | Hunter's / Leather (11040100..03) | 0 | 0 | 0 | 0 | **0** | — |
| Malla | Creighton / Chain (17420100..03) | 6 | 14.5 | 7.5 | 6.5 | **34.5** | — |
| Hierro | Knight (11050100..03) | 8 | 20 | 12 | 6 | **46** | — |
| Oro | King's (15130100..03) | 2.5 | 16.5 | 10 | 8.5 | **37.5** | Fue 14 / Des 12 |
| Diamante | Drangleic (17240100..03) | 10 | 28 | 12 | 10 | **60** | — |
| Netherita | la pieza de diamante (regla del mod) | 10 | 28 | 12 | 10 | **60** | — |
| Tortuga | Old Bell Helm (11660100) | 10 | | | | 10 | — |

- Ejemplo del castigo: set del Rey con Fue 10 / Des 10 → `f = (1 − 10/14) + (1 − 10/12) = 0.452` →
  `pen = 0.30 + 0.5·0.452 = 0.526` → cada pieza ×0.474 → **17.8** en vez de 37.5.
- Ejemplo: Knight completo con `min(Agu, Adp)` = 6 → `46 + 1.8 = 47.8`.

### 2.3 Los otros sumandos
- `ChrParam 100 +0x118` = **0** (el jugador usa la fila 100: los mismos lectores de `chr+0x38` que en enemies.md §2).
- `(s8) b8+0x305` y `(i32) b8+0x3a0`: **no hay evento TAE que los escriba**. Son los arreglos de stats de SpEffect que
  `FUN_14031c580` pone a 0 cada frame y que las instrucciones `1000[00]` (índice 0) y `1000[09]` (índice 33) vuelven a
  sumar (speffects.md §11.7). Escritores (`sc305.txt`): solo `FUN_14021a760` / `FUN_14021a960` / `FUN_14021ae80`
  (SpEffect), `FUN_14031bcf0` (init) y `FUN_14031c580` (reinicio).
- Todos los SpEffects que los tocan (búsqueda en los 11 `SpEffect*.emevd`):

| SpEffect | Instrucción | Efecto | Origen |
|---|---|---|---|
| 40290000 / 40290001 / 40290002 | `1000[00] (0, 0, 10 / 20 / 30)` | **+10 / +20 / +30** de máximo | Ring of Giants / +1 / +2 (`itemname`) |
| 33190010 | `1000[09] (33, 25, 100)` | **+100** de máximo | Iron Flesh (speffects.md §11.8) |
| 96560100 | `1000[00] (0, 0, 30)` | +30 | `SpEffectEnemy` (de enemigo) |
| 40230000 | `1000[00] (2, 0, 30)` | `b8+0x307` +30: **daño de estabilidad de los ataques propios** (§4.3) | Stone Ring |

- Ningún SpEffect de arma o de ataque los usa: **la "superarmadura" de DS2 no sube el máximo**; va por §5.3.

### 2.4 Cambios del máximo
- Si el máximo cambia, la estabilidad actual se recorta a `[0, máx]` (no sube).
- Al entrar o salir de una ventana con `b8+0x610` (TAE 111200, `0x1b260`), la estabilidad **pasa al máximo**
  (`FUN_140145a40`, bandera `+0x19`). No hay ningún 111200 en `c000100_pl.tae`: no afecta al jugador.

## 3. Regeneración — `FUN_140145ba0` (dentro de `FUN_140145a40`)
- Solo con `b8+0x7e6 == 0` (fuera de la pista `DamageDuration` de la reacción, hit_reaction.md §2).
- `prev` = `b8+0x7e6` del frame anterior (`ctrl+0x18`, se guarda al final de `FUN_140145a40`).
  - Si `prev ≠ 0` y la estabilidad es ≤ 0: **se pone al máximo** (la reacción que la rompió acaba de terminar).
  - Si no: `estabilidad += ChrParam 100 +0x11c · dt` = **0.56 por s**, recortada al máximo
    (`+0x124` = 0 con `b8+0x610 ≠ 0`; no ocurre en el jugador).
- Es el mismo código que el enemigo: no hay una variante del jugador.

## 4. Daño de estabilidad que recibe el jugador
### 4.1 Fórmula — `FUN_140137aa0` → `vt+0x120` = `FUN_140139160`
Desensamblado (`0x140137eab`: `rdx = rcx = [r8+0x10]`, la fila DCP; `0x1401391e1`: `movss xmm0, [rdi+0xa0]`):
```
si categoría (info+0xc) ≠ 1: 0                     // solo golpes normales; la guardia (2) no quita estabilidad
base = DCP+0xa0 · A+0x74 + víctima.calc vt+0x188(daño de vida) + (s8) A+0x70
d    = base · m · (1 + 0.01 · DCP+0xb8 · t)
si víctima.b8+0x7d0 > 0: d *= b8+0x7d0
```
- **`DCP+0xa0`** (f32) es el daño de estabilidad. **Corrección a enemies.md §7.1**: el segundo argumento de
  `FUN_140139160` es la fila de `DamageCtrlParam` (`local_150`), no el golpe; el "`golpe+0xa0` = 1.0" era otro campo.
- `A+0x74` = `golpe+0xb4` (1.0 en golpes de enemigo; en el jugador `b8+0x420` del atacante, 1.0 sin SpEffects).
- `A+0x70` = `golpe+0xce` (s8): 0 en golpes de enemigo; en el jugador atacante, `b8+0x307` (Stone Ring +30, §2.3).
- **`vt+0x188` es de la víctima** (`param_1` es el `ChrDamageActionCtrl` de la víctima), no del atacante como dice
  enemies.md. Su argumento es `resultado+0x34` (el daño de vida, entero → float, `0x140139198`).
  - Jugador (`FUN_140381080`): tabla `PoiseDamageParam` (`FUN_1403b5870`, gestor `+0x150` = índice 0x14 de
    `FUN_14048ba80`): umbrales `+0x00..+0x10`, valores `+0x14..+0x24`; devuelve el valor del mayor umbral superado.
    **La fila 0 vale 0 en todo → suma 0.** (Si el daño solo supera el umbral 0, el código devuelve `+0x00` y no
    `+0x14`: da igual con todo a 0.) Esto resuelve el "PoiseDamageParam sin lector" de enemies.md.
  - Enemigo (`FUN_1401f9bb0`): 0.
- `m` = u16 `+8` × 0.01 de la entrada de `ChrMultiplayParam` de `A+0x90`; 1.0 sin red.
- `t` = `info+0x78` (`FUN_140136ab0`): 0..1 entre `DCP+0xa4 · golpe+0xdc` y `DCP+0xa8 · golpe+0xe0` según la
  distancia 3D, **solo si `info+0x68` (`FUN_140139a80()`) no es nulo** y la distancia supera `CommonParam+0x35c`; si
  no, 0. Las balas lo traen (hit_reaction.md §8); si el cuerpo a cuerpo también: **sin confirmar**. Importa porque los
  DCP de las armas del jugador sí tienen caída (Longsword R1: `+0xb8` = −70 entre 1.4 y 2.0 m). Todas las filas DCP de
  los enemigos del mod tienen `+0xb8` = 0.
- Se aplica en `vt+0xf0` (`FUN_140136220`: `FUN_140145970`, recorte a `[0, máx]`), salvo con `b8+0x4b8` bit 52
  (SpEffect, sin identificar), **antes** de decidir la reacción (`vt+0x80`). Así **el golpe que deja la estabilidad en 0
  ya hace reaccionar**.
- Sin daño de vida aplicado (rama `local_2a8[1] == 0` de `FUN_1401345c0`) también se resta si `FUN_14016f150(chr)`,
  `b8+0x640 ≠ 0` y `thunk_FUN_141ca218b(b8, 0x34)` es falso. Qué son: sin identificar.

### 4.2 Golpes de los enemigos del mod (`poise.json enemyHits`)

| Enemigo | EDP | DCP | `+0xa0` daño | `+0x02` regla | `+0x10` reacción / `+0x0c` nivel |
|---|---|---|---|---|---|
| c1020 (zombi, illagers) | 110200110 / 210 | 541002101 | **20** | 0 | 2 / 3 |
| c1020 | 110200120 / 130 / 220 / 230 / 610 / 620 | 541002102 | **20** | 0 | 2 / 3 |
| c1020 | 110200310 / 510 / 710 | 541003103 | **25** | 0 | 3 / 3 |
| c1020 | 110200410 | 544301103 | **31.25** | 0 | 3 / 3 |
| c3000 Ogro | 130000010 / 020 / 030 / 610 / 710 | 560103102 | 35 | **2** | 3 / 5 |
| c3000 | 130000040 | 560103117 | 94.5 | **2** | 8 / 5 |
| c3000 | 130000110 | 560103103 | 43.75 | **2** | 4 / 5 |
| c3000 | 130000050 / 120 (tasa de daño 0) | 590006400 | 0 | **2** | 23 / 3 |

- Hollow Soldier contra Knight + 1.8 (47.8): el 1.º golpe deja 27.8, el 2.º 7.8 y **el 3.º la rompe**.
  Con cuero (solo el bono) **cada golpe** hace reaccionar.
- El Ogro tiene regla 2: la estabilidad **no cuenta nunca** contra él (§5.1), aunque igual la baja.

### 4.3 Atacantes de tipo jugador y de datapack
- **Quién arma el golpe** (`FUN_140392b20`, desofuscado; `0x141b5b916` = `lea rcx, [0x1410bfff3]`): si
  `DAT_1410bfff3[5·tipo] ≠ 0` → `FUN_140391fe0` (camino del jugador); si no → `FUN_140391e60` (enemigo).
  Tabla `DAT_1410bfff0` (5 bytes por tipo, leída del exe): byte 3 = 1 solo en los tipos 1 y 2.
  - **El invasor NPC** (`chr+0x54` = 4, hit_reaction.md §9) tiene byte 3 = 0: arma sus golpes **como un enemigo**
    (`FUN_140391e60`, `EP+0xd8`), aunque sea "tipo jugador" para la dirección y la reacción (byte 0 = 2).
    Su daño de estabilidad = `DCP+0xa0` de la fila de su arma, con `A+0x74` = 1.0 y `A+0x70` = 0.
  - Un jugador humano (tipos 1 / 2): el mismo `DCP+0xa0`, × `b8+0x420`, + `b8+0x307` (§4.1). Sin SpEffects da lo mismo.
  - Así **`golpe+0xa0` no hace falta** (el pendiente de enemies.md sobre `FUN_140391fe0` deja de importar para la
    estabilidad). Para el registro: en `FUN_140391fe0` `golpe+0x7c..+0xac` = `WP+0x90 × M[i]`, con `WP+0x90` = 0 en
    436 de 473 armas.
- **Daño de estabilidad de las armas del mod** (`DCP+0xa0` de cada PDP; mismo valor como atacante jugador, invasor o
  contra un enemigo de datapack). Valores típicos (`poise.json playerWeapons`):

| Ataque | Espadas / lanza / puños | Hachas / maza / garrote / pico | Gyrm Axe |
|---|---|---|---|
| R1 / combo | 10 | 10 | 10 |
| R2 / combo | 12.5 | 12.5 | 12.5 |
| corriendo / paso atrás | 15 | 15 | 15 |
| rodando | 8 | 8 | 8 |
| 2M R1 | 27 | 18 | 17 |
| 2M R2 | 33.75 | 22.5 | 21.25 |
| 2M corriendo / paso atrás | 40.5 | 27 | 25.5 |
| patada | 9999 (rompe siempre) | 9999 | 9999 |

- **Enemigos de datapack** (`SoulsEnemy`): su golpe es una fila EDP con su DCP, así que su daño de estabilidad es
  `DCP+0xa0` de esa fila. Y lo que el jugador les quita es la columna de arriba (hoy `SoulsEnemy` no lo porta: "poise
  damage of each hit not traced").

## 5. Efecto — `FUN_140136570` (`vt+0x80`, desensamblado en `d136570.txt`) y `FUN_140139380`
### 5.1 Árbol para un golpe normal (categoría 1) con la víctima viva
```
conPoise = estabilidad > 0                                          (FUN_1401459d0, ya restado el golpe)
si DCP+0x02 == 2: conPoise = no
si DCP+0x02 == 1 y no (b8+0x640 ≠ 0 y info+0x86 == 0) y b8+0x7d0 ≤ 0: conPoise = no
super = b8+0x5ec ≠ 0  o  (b8+0x5e8 ≠ 0 y DCP+0x02 ≠ 2)
si chr.e0+0xd8 → +0x10 ≠ 0:                     reacción 0                     (sin identificar)
si no, si 2.º medidor roto (+0xc) y byte 1 del 6.º argumento == 0: reacción 10 (enemies.md §7.2)
si no, si super:                                 reacción 0                      (0x140136779 → 0x1401366e2)
si no, si conPoise y víctima tipo 2 (byte 0):    reacción 1 + FUN_14036c310(e0+0xb8, 4, 0)
si no, si conPoise:                              reacción 0 (enemigo: aguanta sin animación)
si no:                                           la reacción normal (DCP+0x10 remapeada, hit_reaction.md §1)
```
- **Patadas** (`DCP+0x10` = 10, `0x140136707`): en categoría 1 (sin guardia) la reacción pasa a **2** (`lea edi,
  [rsi+1]`, `0x14013681d`; resultado `+0xc` = 2) si no hay `super`, y a 0 si lo hay, **con o sin estabilidad**. Solo
  en categoría 2 (cubriéndose) da 10 = rotura de guardia (`+0xc` = 1, con `CCP+0xed ≠ 0`). Corrige a hit_reaction.md
  §3 ("patada: reacción 10 → `DmgExtraHeavy`"), que vale solo contra un objetivo que se cubre.
- Reacción 0 = `DmgCategory` 10: ninguna transición de `c0001.nmb` entra con 10 (búsqueda en todas las SM; solo
  `0x210e` lo usa negado). **No hay animación**.
- Después, en `FUN_1401345c0` (`0x140134a8c..0x140134ab1`), si el resultado no es de muerte,
  **`FUN_140139380` puede forzar la reacción 1** (y `FUN_14036c310(…, 4, 0)`) **haya estabilidad o no**:
  `CCP+0x130 ≠ 0` (1), `b8+0x5e8 == 0` y `+0x5ec == 0`, no invulnerable (`thunk_FUN_1403486f3`: `b8+0x5f0 / +0x5f8 /
  +0x5fc ≠ 0`, `chr+0xd0` bit 1, `chr+0xd8` bit 0) salvo con `fila+0x65` bit 1, `|DCP+0x2c| > 1.19e-7` y
  (`DCP+0x4d ≠ 0` o `golpe+0xfc ∈ {5, 6}`). Solo 116 filas DCP tienen `+0x2c ≠ 0` (familias 35..38, flechas y virotes
  66..68, 200, 590); **ninguna de los enemigos del mod**.

### 5.2 Regla `DCP+0x02`
| Valor | Filas | Estabilidad |
|---|---|---|
| 0 | 1466 | cuenta siempre |
| 1 | 840 | cuenta solo si la víctima tiene `b8+0x7d0 > 0` (ventana TAE 111900, §5.3) |
| 2 | 922 | no cuenta nunca: siempre reacción normal (y anula el `b8+0x5e8`) |

### 5.3 Superarmadura por TAE (`FUN_140326240`, `poise.json taeWindows`)
- **110500** (`0x1afa4`) → `b8+0x5e8`; **110600** (`0x1b008`) → `b8+0x5ec`; 110000 con `b8+0x18` = 1 / 2 hace lo
  mismo (no aparece en el TAE del jugador).
- **Corrección a agility.md §2.1 y animations.md** ("durante el 110500 un golpe que entra provoca siempre la reacción
  normal"): `FUN_140136570` lee `+0x5e8` / `+0x5ec` directamente y da **reacción 0, sin animación**, haya o no
  estabilidad (con `+0x5e8`, salvo `DCP+0x02 == 2`; con `+0x5ec`, siempre). `FUN_140139380` es otro camino (§5.1).
  `Dodge.inNoPoiseWindow` (que hoy aplica "reacción normal") sigue la lectura vieja.
- 110500 en `c000100_pl.tae` (s del clip):

| Animaciones | Ventana |
|---|---|
| Rodada 120020 / 21 / 30 / 31 | 0.1–0.667 |
| Paso atrás 110011 / 31 (Tired 110051 / 71) | 0.167–0.333 (0.333–0.5) |
| Ataques: 20010022, 20010212, 24030031, 26010211, 26030031, 26030211, 26030221, 27010210, 31010037, 31030039, 31050037, 32010020 (pico R2 combo), 32010280 | ver `poise.json` |

- 110600: solo 70160010 (−0.167–1.5) y 70170010 / 70170020 (−0.167–4.167).
- **111900** (`0x1b51c`) → `b8+0x7d0 = WP+0xb8` del arma de la mano que ataca (`FUN_14034e6f0`,
  `thunk_FUN_141c5212f(…, b8+0x188 ≠ 0 ? −1 : −2)`; −1 derecha, −2 izquierda, powerstance.md). `FUN_14031c460` lo pone
  a 0 cada frame, así que vale solo mientras el evento está activo. Efecto en la víctima (§4.1, §5.2): **daño de
  estabilidad × `WP+0xb8`** y los golpes de regla 1 respetan la estabilidad.
  - 136 animaciones lo tienen (casi siempre de 0.667 s a 1.0–1.333 s, la fase del golpe).
  - **En el mod solo la Gyrm Axe tiene `WP+0xb8` ≠ 0 (0.6)**: en sus ataques con 111900 recibe ×0.6. En las demás
    armas `WP+0xb8` = 0, así que el 111900 deja `b8+0x7d0` = 0 y no hace nada.

## 6. Si aguanta: reacción 1 (jugador y tipos con byte 0 = 2)
### 6.1 `FUN_14036c310(ctrl = chr.e0+0xb8, 4, 0)` y su actualización `FUN_14036c3f0`
- Tipo 4: `peso = CCP+0x134 · 0.01` = **0.7**, `subida = CCP+0x138` = **0.1 s**, `bajada = CCP+0x13c` = **1.0 s**,
  estado 1, `b8+0x720 = 1`, `b8+0x724 = subida + bajada` = **1.1**.
- Cada frame (`FUN_14030eb60` → `FUN_14036c3f0`; `+0x2c` = 90 × 0.01745 rad):
  - estado 1: `c += dt / 0.1`, `w = 0.7 · sin(c·π/2)`; con `c ≥ 1` → `w = 0.7`, estado 2;
  - estado 2: `c += dt / 1.0`, `w = 0.7 · (1 − sin(c·π/2))`; con `c ≥ 1` → `w = 0`, estado 0.
  - Que `thunk_FUN_141bf05f1` sea el seno: por la forma (argumento en radianes, 90°); sin confirmar.
- **`MoveHit_Weight` = w** (`FUN_140362c40`, `0x140362df4`, con el tipo 4; con otros tipos el peso va a
  `LandingRate`).
- **`DmgDuration` = 1 / `b8+0x724` = 0.909** mientras `b8+0x720` (`FUN_14035e820`, `0x14035ede4`). `FUN_1401345c0`
  pone `b8+0x720 = 0` al principio de cada golpe.
- Con `b8+0x720`, `FUN_14013b4d0` usa 0 como distancia base (`0x14013b7e0`): **`DmgDistance` = 0** (+ el extra si ya
  estaba en reacción).
- **Velocidad**: mientras el tipo 4 está activo (`FUN_14036c2f0`), `FUN_140385fe0` (movimiento del jugador) escribe
  `b8+0x11c[4] = 1 − 0.01·CCP+0x140` = 0.5 (`0x140386a7a`) y luego, en la misma ranura, `1 − 0.01·CCP+0x144` =
  **0.7** (`0x140386b59`). Queda el segundo: **velocidad ×0.7 durante 1.1 s** (`FUN_14031c7f0`, equipload.md §3).

### 6.2 Lo que hace `c0001.nmb` con la reacción 1 (`poise.json reaction1`)
Solo dos SM reaccionan a `DmgCategory` 20 (búsqueda en todas las SM): `0x5c7` en `BT_Move` y `0x187d` en `BT_Damage`.
El SM principal `0xd2` **no** entra en `BT_Damage` con 20 (hit_reaction.md §1.5).
- **En locomoción (`BT_Move`)**:
  - SM `0x5c7`: con el mensaje 16 y `DmgCategory` = 20 pasa (mezcla **0.1 s**, también desde sí mismo) a
    `0x5ea` = **FeatherBlend2** (`BlendWeight_1` = 1) de la locomoción `0x4e0` y de
    `0x5e9` (ApplyGlobalTime) → `0x5e5` (**ScaleToDuration** con `DmgDuration`, tabla de colas 23) → switch
    `DmgSlashOrThrust` → switch `DmgDownOrUp` → BlendN `DmgDirection` de los **16 `DmgKnockBackLight`**
    (`a00_16_0010..0043`, los mismos de hit_reaction.md §2, sin los Heavy).
  - Pesos por hueso del FeatherBlend2 (atributo 0x57; los de `0x1923` que ya conocía hit_reaction.md salen bien con el
    mismo lector): **Spine 0.4, Spine1 0.5, Neck 0.6, Head 0.75, Clavicle 0.6, UpperArm 0.5, Forearm 0.4**; manos,
    armas, pelvis, piernas y raíz **0** (siguen la locomoción). Es una sacudida parcial del torso y los brazos.
  - **ScaleToDuration** (`TaskScaleToDuration` `FUN_140612430`, tarea 0xba): `Δt hijo = Δt · D / max(1e-4, CP)`;
    el clip entero dura **CP = 0.909 s** (no es una velocidad, a diferencia del nodo 0x7d de `BT_Damage`). El clip de
    1.3333 s corre a ×1.467.
  - Sale (`0x5ec`, mezcla **0.15 s**) dentro del evento de pista userData 80 (`StateControl`, 1.0 s del clip) =
    **0.682 s** tras el golpe; ya mezclado del todo hacia 0.83 s.
  - SM `0x4f9`: con `MoveHit_Weight > 0` (condición 0x25e, mezcla 0.01 s) entra en `0x4fc`: switch `DmgDirection`
    entre **HipsIK** `0x4fb` (`MoveHit_Rotation_L`) y `0x4fa` (`MoveHit_Rotation_R`) sobre la locomoción `0x523`, con
    peso `MoveHit_Weight`. Vuelve con peso 0 (mezcla 0.2 s).
  - `MoveHit_Position` / `MoveHit_Rotation_L` / `_R` **no los escribe el exe** (solo está la cadena
    `ControlParameters|MoveHit_Weight`): valen su defecto del NMB: posición **(0, −0.1, −0.15)**, rotación L
    **(5°, 15°, −15°)**, R **(5°, −15°, 15°)** (Euler, mismo HipsIK y convenciones que hit_reaction.md §10).
- **Ya en reacción (`BT_Damage`)**: SM `0x187d` dentro del FeatherBlend2 `0x18c1` (Spine y todo lo de arriba a 1;
  pelvis y piernas siguen la reacción en curso `0x18c2`): mezcla 0.1 s a un Blend2 `DmgDistance` 0.8 (Light) .. 2.5
  (Heavy) de los mismos cuadrantes, sin ScaleToDuration. Con `DmgDistance` = 0 queda en Light.
- **Durante un ataque, una esquiva, la guardia, etc.** (fuera de `BT_Move` y `BT_Damage`) no hay nodo que lea
  `DmgCategory` 20 ni `MoveHit_Weight`: **no se ve nada y la acción sigue**. Solo actúa la velocidad ×0.7.

### 6.3 Bloqueos durante la reacción 1
- La reacción 1 no pasa por `BT_Damage`, así que no hay bloqueos de esa rama.
- El clip Light lleva `CancelControl_AttackAll` 0–0.9, `Avoid` 0–0.767, `Guard` 0–0.9 y `DamageDuration` 0–1.333 s
  (a ×1.467: 0.614 / 0.523 / 0.614 / 0.909 s). El FeatherBlend2 pasa los eventos de sus hijos
  (`nodeBlend2QueueSampledEventsBuffers`, tabla 9) y la salida de `0x5c7` se dispara con el evento 80 del clip, así que
  los eventos llegan al menos hasta esa SM. **Que esas pistas bloqueen de verdad el ataque, la rodada y la regen**
  (`MorphemeEventTrackCtrl` sobre el búfer raíz, mezcla parcial): **sin confirmar**.

## 7. Si no aguanta: la reacción del jugador
La cadena es la de hit_reaction.md §1 con la víctima jugador: `reacción = FUN_140138a40(DCP+0x10, DCP+0x0c)`, fila
`1000·(r+1) + 10·(nivel+1)` de `DamageMotionWeightParam`, **columna 2** (`FUN_140380b30`).
- Velocidad de `DmgKnockBack` (reacciones 0..3): `v = fila+0x2c + clamp(0.15·n − lerp(0, 0.2, t), 0, 1.5)`
  (`FUN_1403807c0`, hit_reaction.md §1.4): `n` = golpes ya recibidos en la reacción, `t` = carga 40 % → 60 %.
  Con el primer golpe, `v` = `EDP+0x2c`.
- El SM principal entra en `BT_Damage` desde cualquier estado con el mensaje 16 (`0x2094..`): **corta el ataque o la
  acción en curso**. `b8+0x7e6` (pista `DamageDuration`) para la regen de estabilidad hasta que termina.

| Enemigo (EDP) | DCP `+0x10` / nivel | Fila → col. 2 | Reacción → `DmgCategory` | Clip | v | Total | Ataca | Rueda | Se mueve | Sale quieto |
|---|---|---|---|---|---|---|---|---|---|---|
| c1020 ligeros 110200110 / 130 / 210 / 230 / 620 | 2 / 3 | 3040 → 2 | 2 → 30 | `DmgKnockBack` Light↔Heavy | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 (stick) / 0.783 (Heavy) | 0.870 |
| c1020 ligeros 110200120 / 220 (610) | 2 / 3 | 3040 → 2 | 2 → 30 | ídem | 1.5 (1.4) | 0.889 (0.952) | 0.600 (0.643) | 0.511 (0.548) | 0.556 / 0.600 (0.595 / 0.643) | 0.667 (0.714) |
| c1020 fuertes 110200310 / 510 | 3 / 3 | 4040 → 3 | 3 → 40 | ídem, tira a Heavy | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 / 0.783 | 0.870 |
| c1020 110200410 / 710 | 3 / 3 | 4040 → 3 | 3 → 40 | ídem | 0.9 | 1.481 | 1.000 | 0.852 | 0.926 / 1.000 | 1.111 |
| Ogro 130000010…, 610, 710 | 3 / 5 | 4060 → 3 | 3 → 40 | ídem | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 / 0.783 | 0.870 |
| Ogro 130000110 | 4 / 5 | 5060 → 4 | 4 → 50 | `DmgExtraHeavy*Short/Long` (3.0 s) | 0.85 (`CCP+0x174`) | 3.529 | 2.353 | 2.353 | 2.353 (Move 0–2.0) | 2.941 (`StateControl` 2.5) |
| Ogro 130000040 | 8 / 5 | 9060 → 8 | 8 → 90 | `DmgTumbleFaceDown/Up` (0.667 s) y luego en el suelo | — | ver abajo | | | | |
| Ogro 130000050 / 120 | 23 / 3 | sin fila 24040 | 23 | — | | | | | | |

- `remapped` en cada golpe de `poise.json` (`enemyHits` y `playerWeapons[].attacks[].hits`) es ya esta reacción
  remapeada para la víctima jugador (columna 2, sin fila o nivel < 0 → `DCP+0x10`, 7 → 4 si `CCP+0x1a4 == 0`; vale 1,
  así que no aplica): el mod no necesita la tabla en tiempo de ejecución.
- "Ataca / rueda / se mueve / sale" = fin de `CancelControl_AttackAll` / `_Avoid` / `StateControl_1` (o `_Move` de
  Heavy) / `StateControl` divididos por `v` (hit_reaction.md §2; ventanas en `poise.json staggerClips`).
- `DmgExtraHeavy` (160110..160183): `Move`, `AttackAll` y `Avoid` 0–2.0 s, sin pista de guardia, `StateControl_1`
  2.0, `StateControl` 2.5, `DamageDuration` 0–3.0. Velocidad `vt+0x140(CCP+0x174 = 0.85)` y `b8+0x100 = CCP+0x178`.
  Árbol en §7.3.
- `DmgTumble` (160510 / 160520, cuál de los dos: §7.3): `AttackAll` y `Avoid` 0–0.667 s; reacción 8 usa `b8+0x104 = DCP+0x18` = **1.5 s**
  ("tiempo en el suelo", enemies.md §4). El encadenado al suelo y al levantarse: **sin decodificar**.
- Reacción 23 (filas sin daño del Ogro): `FUN_140138930` no la lista; sin fila de remapeo. Qué hace en la víctima:
  **sin confirmar** (hit_reaction.md §8 la ve en los PDP de apoyo que no golpean a nadie más).
- El invasor NPC usa los PDP de sus armas: ligero → 2, fuerte → 3 (tabla de hit_reaction.md §3, columna 2), y su
  patada → **2** si el jugador no se cubre (§5.1), 10 si se cubre.
- **Guardia** (categoría 2): no quita estabilidad (§4.1) y tiene su propia cadena (stamina.md "Guardia", rotura →
  reacción 10). Tema aparte.

### Entrada a `BT_Damage` (`0x1ae7`): mezclas (SM `0xd2`, atributo 0x3b `+0x10` de cada transición)
| Transición | Condición (mensaje 16 +) | Mezcla |
|---|---|---|
| `0x20a0` | `DmgCategory` 30 | 0.05 s |
| `0x2094` | 40 | 0.05 s |
| `0x209e` | 50 | 0.05 s |
| `0x209d` | 60 (BlowAway) | **0.0 s** (corte seco) |
| `0x209f` | 80 | 0.05 s |
| `0x2096` | 90 (Tumble) | 0.05 s |
| `0x20cf` | 95 | 0.05 s |
| `0x2095` | 100 | 0.05 s |
| `0x209c` / `0x209b` / `0x209a` | 140 / 150 / 160 | 0.05 s |
| `0x2099` / `0x2098` | 180 / 190 | 0.05 s |
| `0x2097` | 200..210 | 0.05 s |
| `0x20a1` | 310 | **0.001 s** (y `+0x14` = 3; en las demás, 0: **sin identificar**) |
| `0x20a2` | 320 | 0.05 s |
| `0x2091` | mensaje 15 (HitBack) | 0.05 s |
| `0x20e3` | desde `0x1aee`, otra condición (0x269 / 0x25b) | 0.3 s |

- Todas son "desde cualquier estado" (origen nulo) salvo `0x20e3`.
- **Curva** (`TaskTransitUpdateTimePos` `FUN_1406334f0`, tarea 0xac; `out\poise	uc.c`): `x = t / duración`;
  `w = 0.5·(1 + sin((x − 0.5)·π))` = **(1 − cos πx) / 2**, la habitual. El seno es un polinomio por cuadrantes
  (`DAT_14110d078..`). Con duración 0, `w` = 1 al momento. Las constantes 0.5, π, 2/π y 2 se leyeron del exe.
- Que estas transiciones (tabla de colas 7, `nodeTransit*`) usen esa tarea y no la variante con sincronía
  (`nodeTransitSyncEvents*`): por la tabla de colas; sin confirmar en código para cada nodo.

## 7.1 Entradas de la reacción para los golpes de enemigo (`poise.json enemyHits`)
- **No salen del bloque del golpe**: `FUN_14013b4d0` y `FUN_140138cc0` leen la **fila de daño** directamente.
  - `FUN_14013b0a0` (`ChrDamageActionCtrl vt+0x140`) arma en `rbp−0x58` la estructura que recibe `FUN_14013b4d0`:
    `+0x10` = `[rdi+8]` (`0x14013b17f`) y `+0x18` = `[rdi+0x10]` (`0x14013b143`).
  - `rdi` es la estructura `{id, id DCP, fila, DCP}` de `FUN_1401345c0` (`local_298`). La fila =
    `thunk_FUN_141b0fe63(id)` → gestor `+0xb40` → `FUN_1403b56d0`, que elige la tabla por el rango del id
    (`cmp edx, 0x5f5e0ff`: PDP hasta 99 999 999, luego EDP / SDP; desofuscado con `deob.py`).
  - **Para un golpe de enemigo, la fila es la de `EnemyDamageParam`**, y como el mismo código lee los mismos offsets,
    los campos son los de PDP por construcción. `FUN_140391e60` y el bloque del golpe no intervienen.
- Campos (víctima jugador):

| Campo | Lector | Fuente |
|---|---|---|
| `speed` | `FUN_14013b4d0` `0x14013b866/893/8ad` → víctima `vt+0x140` (`FUN_1403807c0`) | fila `+0x2c` (reacciones 0–3, 5–7, 12, 13, 15, 16) |
| `distance` | `0x14013b7b9` | fila `+0x28`; reacciones 4 / 10 / 11 / 14 → `CCP+0x170` (1.5, máscara `0x4c10`); con `b8+0x720` → 0; reacción 5 / 6 con `ctrl+0x5a4` → (float) u8 fila `+0x5d` (`0x14013b6fc`) |
| `thrust` | `0x14013b916` | s8 fila `+0x32`; si es 0xff, `DCP+0x00`. `DmgSlashOrThrust` = 1 si vale 1 |
| `downUp` | `FUN_140138cc0` = `vt+0x148`, llamado con `rdx = [rdx+8]` = la fila (`0x14013b130`) | u8 fila `+0x43`: 0 abajo, 1 arriba, 2 por altura del punto (hit_reaction.md §9) |
| `near` / `far` / `falloff` | `0x14013b944`, hit_reaction.md §9 | `DCP+0xa4` / `+0xa8` / `+0xbc` |

- Valores: c1020 `speed` 1.15 (1.5 en 110200120 / 220, 1.4 en 610, 0.9 en 410 / 710), `distance` 0.5 / 1.5 / 1.25,
  `thrust` 1 en los ligeros y 0 en los fuertes, `downUp` 0 o 1. Ogro `speed` 1.15 (2.0 en las filas sin daño),
  `distance` 1.25 / 1.5. Todos los enemigos del mod: `near` = `far` = `falloff` = 0.
- La tabla de §7 usa `v` = `EDP+0x2c`, confirmado por esta vía.

## 7.2 `Dmg_Rotation` (HipsIK) del jugador víctima
- `FUN_14035e820` sortea `Dmg_Rotation` con `chr+0x40 +0x30c..+0x318` (hit_reaction.md §10). En el jugador `chr+0x40` es
  `ChrCommonParam 100`: **D, A, B, C = 15, 10, 15, 8** (`poise.json hipsRotation`), los mismos que hit_reaction.md
  §10 ya listaba para el jugador. Los nodos HipsIK de `BT_Damage` son los de c0001 (`0x1924` / `0x1a92`, peso
  `BlendWeight_1` = 1), así que el jugador lo lleva como el invasor, con estos valores en vez de 45 / 10 / 20 / 15.

## 7.3 Árboles de `DmgExtraHeavy` (reacción 4 → 50) y `DmgTumble` (reacción 8 → 90) en `c0001.nmb`
Mismo lector que §6.2 (`nx.py` / `smx.py` del scratchpad de la sesión, escáner de `out\grip\grip_nodes.py`).
```
0x1925 SM (un solo estado)
└ 0x1966 HipsIK (Dmg_Position, Dmg_Rotation, BlendWeight_1)              // como 0x1924, hit_reaction.md §10
  └ 0x1965 PlaySpeedModifier (0x7d) con DmgDuration (velocidad, 0.85)
    └ 0x1954 Blend2 (0x6b) DmgSlashOrThrust, 0 .. 1                         // mezcla, no switch
      ├ 0x193c switch DmgDownOrUp (0, 1): [0] 0x1927 SlashUp, [1] 0x1932 SlashDown
      └ 0x1953 switch DmgDownOrUp (0, 1): [0] 0x193e ThrustUp, [1] 0x1949 ThrustDown
         cada uno: Blend2 (0x6b) DmgDistance 0.0 (Short) .. 2.5 (Long)
           └ BlendN DmgDirection (−225 BR, −135 BL, −45 FL, 45 FR, 135 BR, 225 BL)
```
- **Short o Long lo elige `DmgDistance`**, mezclando de forma continua: 0.0 = Short puro, 2.5 = Long puro.
  - Con la reacción 4, `DmgDistance` = `CCP+0x170` (1.5) × (1 + 0.01·`DCP+0xbc`·t) + extra (§7.1, `0x14013b7ce`).
    Para el Ogro 130000110, con `+0xbc` = 0, da **1.5 → 60 % Long**.
- Clips:

| Variante | Short | Long |
|---|---|---|
| SlashUp | 160110–160113 | 160150–160153 |
| SlashDown | 160120–160123 | 160160–160163 |
| ThrustUp | 160130–160133 | 160170–160173 |
| ThrustDown | 160140–160143 | 160180–160183 |

  (FL / FR / BL / BR = 0 / 1 / 2 / 3.)
- Hay que mirar el orden de los hijos: el switch pone **Up** en el valor 0 de `DmgDownOrUp` y **Down** en el 1, aunque
  hit_reaction.md §9 llama "abajo" al 0. Se deja tal como está en los datos.
- **`DmgTumble`**: el nodo `0x197c` (switch 0x83, hijo directo de `SM_Damage` `0x18c2`) elige por `DmgDirection`
  entre los valores −180, 0 y 180: FaceDown (160510) / **FaceUp** (160520) / FaceDown. Con la regla del valor más
  cercano (hit_reaction.md §9), un golpe de frente (|ángulo| < 90°) da FaceUp y uno de espaldas, FaceDown. No hay
  nodo de velocidad.

## 8. Qué no hace la estabilidad
- No cambia el daño de vida (se aplica igual con o sin reacción, enemies.md §7.1).
- No afecta a la guardia ni a la estamina.
- El "segundo medidor" `chr+0x1b8` (enemies.md §7.2) usa también `vt+0x1f8` en su máximo (`ChrParam+0xfc` 500 +
  armadura) y al romperse fuerza la reacción 10; es otro sistema y queda fuera de esta nota.

## 9. Datos exportados — `tools/re/poise.py` → `poise.json`
`python tools/re/poise.py src/main/resources/data/soulsmode/ds2/poise.json`
- `armor`: por material y pieza de Minecraft, `armorParam`, `poise` (`+0x40`) y `requirements` (Fue / Des / Int / Fe).
- `armorLackOfStats` (fila 0x1c y fórmula), `statPoise` (PSPL `+0x90`, 1..99), `base`, `regenPerSecond`,
  `regenPerSecondAlt`, `poiseDamageParam`.
- `reaction1`: `hipsWeight`, `rampIn`, `rampOut`, `dmgDuration`, `moveSpeedFactors` (el segundo es el que queda),
  `overlay` (pesos por hueso, mezclas, los 16 clips, pistas), `inDamage`, `hipsIK` (posición / rotaciones por defecto
  y mezclas).
- `staggerClips`: duración y pistas de 160010 (Light), 160050 (Heavy), 160110 / 160150 (ExtraHeavy), 160510 (Tumble),
  161210 (Stagger).
- `taeWindows`: todas las ventanas 110500 / 110600 / 111900 del TAE del jugador.
- `enemyHits`: filas EDP de `c1020_attacks.json` / `c3000_attacks.json` con su DCP (`poiseDamage`, `poiseRule`,
  `reaction`, `level`, caída `poiseFall` / `fallNear` / `fallFar`, `forceLight` = `+0x2c`, `forceLightFlag` = `+0x4d`).
- `playerWeapons`: por arma, `attackPoiseFactor` (`WP+0xb8`) y por ataque `taeId`, los DCP de sus PDP (mismos campos)
  y sus ventanas `b8+0x5e8` / `+0x5ec` / `+0x7d0`.
- Los nodos de `c0001.nmb` se leen con el mismo escáner que `out\grip\grip_nodes.py` (copiado en el script) y se
  comprueba su tipo (`0x5ea` = 0x72, `0x5e5` = 0x97, `0x4f9` = SM).

## 10. Correcciones a otras notas
- enemies.md §7.1: el daño de estabilidad es `DCP+0xa0`, no `golpe+0xa0`; `vt+0x188` es de la víctima (jugador:
  `PoiseDamageParam`, todo 0). `PoiseDamageParam` sí tiene lector (`FUN_140381080`).
- agility.md §2.1 y animations.md: con `b8+0x5e8` (110500) el golpe que entra da **reacción 0** (sin animación), no la
  reacción normal. `FUN_140139380` es el camino que fuerza la reacción 1 por `DCP+0x2c`.
- agility.md §2.3: la fila de la falta de stats del equipo es `ArmorParam` (`+0x2c..+0x32`).
- hit_reaction.md §3: la patada sin guardia es reacción 2 (`DmgKnockBack`), no 10 (10 solo contra quien se cubre).
- hit_reaction.md §1.5 / §6: la reacción 1 no lleva "nodo 0x97 con `DmgDuration`" como velocidad: `0x5e5` (tipo 0x97)
  es **ScaleToDuration** y el CP es una duración (0.909 s).

## Sin confirmar
- Si `FUN_140139a80()` (`info+0x68`) existe en los golpes cuerpo a cuerpo, es decir, si la caída de estabilidad por
  distancia (`DCP+0xb8`, −70 en la Longsword) se aplica a los golpes del jugador.
- **Bloqueos de la reacción 1** (§6.3): si las pistas `CancelControl_*` y `DamageDuration` del clip Light, mezclado por
  el FeatherBlend2 sobre la locomoción, llegan a `ChrEventTrackActionStateControl` y bloquean ataque / rodada / guardia y
  la regen de estabilidad.
- La fase del clip de la reacción 1: el nodo `0x5e9` (ApplyGlobalTime, `FUN_14060ef80` copia el tiempo de entrada) está
  encima del ScaleToDuration; se supone que el clip empieza en 0 al entrar al estado.
- Qué HipsIK (L o R) elige el switch `0x4fc` por `DmgDirection`: sus dos valores son 0.0 en el atributo 0x9.
- Las condiciones 0x25e / 0x260 / 0x26a / 0x25f de las transiciones (peso > 0, evento 80, …): lectura del formato, como
  en hit_reaction.md.
- Que `thunk_FUN_141bf05f1` sea el seno (envolvente de `MoveHit_Weight`).
- `chr.e0+0xd8 → +0x10` (anula la reacción), `b8+0x640` / `info+0x86` (convierte la regla 1 en 0), `golpe+0xfc`
  (condición de `FUN_140139380`), `b8+0x4b8` bit 52 (anula el daño de estabilidad), `FUN_14016f150` y
  `thunk_FUN_141ca218b(…, 0x34)` (daño de estabilidad sin daño de vida).
- La reacción del jugador a las filas de reacción 23 (Ogro 130000050 / 120) y el encadenado `DmgTumble` → suelo →
  levantarse de la reacción 8 (`DCP+0x18`).
- Que el switch 0x83 elija por el valor más cercano también con 3 valores (`DmgTumble`) y el sentido Up/Down del switch de `0x1925`: lectura del formato.
- Nombres de los tipos 1 y 2 de `DAT_1410bfff0` (los que arman el golpe con `FUN_140391fe0`); se supone jugador
  anfitrión y fantasmas, por la tabla y el uso.
- Los multiplicadores `b8+0x420` (`golpe+0xb4`) del atacante jugador con SpEffects, y los SpEffects que suman a
  `M[9]` (resinas) en `FUN_140391fe0`: no afectan a la estabilidad tal como quedó la fórmula, pero no se recorrieron.
- Cómo se equipan los anillos (Ring of Giants, Stone Ring): Minecraft no tiene anillos; solo se identificaron sus
  SpEffects.
