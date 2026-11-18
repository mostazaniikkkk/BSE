# Petrificación, maldición y estatuas petrificadas

Investigación del 2026-11-18 (agentes de solo lectura; salidas en `D:\Reversing\DS2\out\petrify\`). Partes:
§0–§7 el estado del jugador (petrificación y maldición); §8 las estatuas y la Rama de antaño fragante; §9 lo que hace
el mod.

Fuente: `DarkSoulsII.exe` (SotFS), proyecto Ghidra `D:\Reversing\DS2\ghidra_proj` (headless, `ghidra.sh`), código
ofuscado seguido con `out\py\deob.py`. Salidas nuevas en `D:\Reversing\DS2\out\petrify\` (`d1.c`…`d20.c` decompilados,
`deob_b49e.txt`, `sc*.txt` barridos de operandos, `vt*.txt` vtables, `x*.txt` referencias). Base: `bleed.md` (gestor de
estados `0x140145000..0x140147000`, `FUN_140145c60`, ranuras 6..12, medidor `chr+0x1c4+12·i`).
Datos: regulation (`ChrCommonParam` CCP, `EnemyCommonParam` ECP, `PhysicalStatsPerLevelStatValuesParam` PSPL,
`ArmorReinforceParam` ARP, `EnemyDamageParam` EDP, `EnemyParam` EP, `DeadTypeParam`, `ChrFullBodySfxParam`,
`CriminalAndDeceasedParam`, `PlayerLackOfStatsParam`), `SpEffect*.emevd` (`out\hollow\evd.py`), `c000100_pl.tae` /
`c000100_snd.tae` (`tools\ds2\tae.py`), `c0001.nmb` y `.nsa` (`tools\re\nsa.py`), textos `common` / `iconHelp` /
`itemname` / `SimpleExplanation` / `DetailedExplanation` / `mapname` (`tools\re\lang.py`).

## 0. Resumen
| | Petrificación | Maldición |
|---|---|---|
| Tipo (`FUN_140145c60`) / ranura del golpe / índice de absorción | **6** / 12 / `abs[12]` | **3** / 9 / `abs[9]` |
| Estado AbnormalStateSub que bloquea la acumulación | entrada `石化` (byte 10 de `0x1415710c0`, `FUN_14023dcc0`) | entrada `呪` (byte 3, `FUN_14023db70`) |
| Al llenarse | SpEffect **901100** + **muerte instantánea** (vida a 0) | SpEffect **900400**: **+1 nivel de Hueco** |
| Resistencia de stats | PSPL `+0x50` de `⌊(3·Adaptabilidad + Vigor)/4⌋` | PSPL `+0x54` de `⌊(3·Adaptabilidad + Sintonía)/4⌋` |
| Armadura | `lerp(ARP+0x28, ARP+0x58)` | `lerp(ARP+0x2c, ARP+0x5c)` |
| Bajada del jugador (CCP 100) | `+0x2dc/+0x2e0` = **45 / 45 s** | `+0x2cc/+0x2d0` = **22 / 22 s** |
| Multiplicador `1000[01]` (índice = tipo) | **6** (`b8+0x3d4`) | **3** (`b8+0x3c8`) |
| Índice `a` de `100090[04]` (defensa ±) | **9** | **8** |
| Texto (es_mx) | "RES petrif." / "La petrificación causa la muerte inmediata." | "RES maldi." / "¡Sufres maldición!" |

**Son dos estados distintos.** Lo que DS2 llama "petrificación" mata; la "maldición" es la "maldición de lo Hueco" (sube el
contador de Hueco). Ninguno es un estado de piedra temporal.

## 1. Acumulación
### 1.1 Qué entra al medidor
Mismo camino que el sangrado (bleed.md §2), con `i = 6` (petrificación) o `i = 3` (maldición):
`entra = (1 − g) · clamp(1 − abs, 0, 1) · Z · max(0, A − def) / R`, con `A = (golpe[6+i] + E_i) × golpe+0xb4 × R_i`.
- **Golpes de enemigos**: `FUN_140170710` suma los pares (tipo, valor) de la fila EDP; tipo **12** = petrificación, **9** =
  maldición. `FUN_140391e60` multiplica las 13 ranuras por `EP+0xd8` y suma el código 7 de SpEffect del enemigo a las
  ranuras 0..4, 6..9 y 12 (enemies.md §4.1). La división `/2505` solo se vio en el atacante jugador (bleed.md §5).
- **Golpes del jugador**: el arreglo del arma tiene 10 valores y solo `[5]`/`[6]` van a las ranuras 6/7 (bleed.md §0);
  **ninguna arma del jugador acumula petrificación ni maldición**. `PlayerDamageParam`: 0 filas con tipo 9 o 12.
- `FUN_140145c60` (tipos 3 y 6): `b8+0x5fc ≠ 0` → nada; estado activo del grupo AbnormalStateSub → nada;
  `medidor + v ≥ 100` (`DAT_1410acb18`) → estallido (§2); si no, `FUN_1401462a0`: solo si `v > 0` y el multiplicador
  `b8+0x3bc+4i < 70` (`DAT_1410bfc78`) → retardo `0.5 s` y `medidor += v`.
- **Solo maldición** (`0x140145cf0` → `0x140145d2a`): antes de todo lee `chr+0xb0 +0x3c` (`FUN_14014ed00`) y la tabla
  `DAT_1410c0050` (20 × 16 bytes): si el byte 1 de la entrada ≠ 0 **no acumula**. Byte 1 vale 0 en las entradas 0, 18 y
  19 y 1..3 en las 1..17. Qué tipo de personaje es cada entrada: sin identificar (thrown_items.md / bonfire.md la usan
  para el rol en línea).

### 1.2 Resistencia del jugador (`FUN_140381350`)
```
l[a] = Σ_piezas 0.01·lerp(ARP min, ARP max, nivel/ARP+0x60) · falta17 + SpE(código 7) + stat
abs[12] = clamp(l[8], 0, 1)     // salida +0x30   (0x14038184c: param_2+6 = local_188[8])
abs[9]  = clamp(l[9], 0, 1)     // salida +0x24
```
- Armadura (`FUN_14034dda0`): `[8] = lerp(+0x28, +0x58)`, `[9] = lerp(+0x2c, +0x5c)`. Comprobación con datos: **Hollow
  Skin** (ARP 11670100, "Provides curse resistance") tiene `+0x2c = 100`, `+0x28 = 0.1`.
- `falta17` = `FUN_140381c20(…, PlayerLackOfStatsParam fila 0x11 = (30, 50))` (los índices 5..9 usan la fila 0x11).
- Stat (`FUN_14038d250(b, k)` = `b+0x84+4k`; escritas por `FUN_14038d790`, `out\agl\derive.c`):
  - `k = 3` (→ `l[8]`, petrificación): **PSPL f32 `+0x50` × 0.01** de la fila `⌊(3·slot8 + slot0)/4⌋` =
    **(3·Adaptabilidad + Vigor)/4**.
  - `k = 4` (→ `l[9]`, maldición): **PSPL f32 `+0x54` × 0.01** de `⌊(3·slot8 + slot3)/4⌋` = **(3·Adaptabilidad +
    Sintonía)/4**. (Slots de stats.md: 0 Vigor, 3 Sintonía, 8 Adaptabilidad.)
  - Valores (iguales en `+0x44..+0x58`): 1 → 10, 5 → 13, 10 → 16, 15 → 20, 20 → 24, 25 → 24.5, 30 → 25, 40 → 26,
    50 → 27, 60 → 28, 70 → 28.5, 80 → 29, 90 → 29.5, 99 → 30 (%).
  - Coincide con el menú de subir nivel (stats.md: petrificación MSP `(3 ADP+VIT)/4`, maldición `(3 ADP+APR)/4`).
- **Cruce 8 ↔ 9 en los SpEffect**: `FUN_141c60e41` (suma de los vectores de SpEffect, la llaman `FUN_140381350` para el
  código 7 y `FUN_14031fe20` = `vt+0x250` para el código 2) hace `out[8] += k·in[9]` y `out[9] += k·in[8]`. Por eso en
  `100090[04] (t, a, b)` **`a = 9` es petrificación y `a = 8` maldición** (en los índices 0..7 no hay cruce).

| Fuente | Instrucción | Petrificación | Maldición |
|---|---|---|---|
| Anillo de nudillo de cenizas (Ash Knuckle Ring) 40135000 | `100090[04] (0, a 9, 40)` | **+40 %** | — |
| Anillo de mordisco maldito (Cursebite Ring) 40130000 | `(0, a 8, 40)` | — | **+40 %** |
| Anillo de resistencia 40150000 / +1 40150001 | `a 5..9`, 12 / 20 | +12 / +20 % | +12 / +20 % |
| Grageas de serpiente tríclope (Triclops Snake Troches) 60230000 | `(90 s, a 9, 20)` + `1000[01] (6, 90, 1.2)` | +20 % y bajada ×1.2, **90 s** | — |
| Perseverancia 32250010 | `(60 s, a 8/9, 30)` + `1000[01] (0..6, 60, 1.2)` | +30 %, ×1.2, 60 s | +30 %, ×1.2, 60 s |
| Carne férrea (Iron Flesh) 33190010 | `(25 s, a 8/9, 20)` | +20 %, 25 s | +20 %, 25 s |
| Lanza de Yorgh 2855000 (SpEffectWeapon) | `(0, a 8/9, 7)` | +7 % | +7 % |
| SpEffectWideUse 130000010 | `(0, a 9, 100)` | **inmune** | — |
- El tiempo `t = 0` es "mientras esté equipado" por cómo lo usan los anillos (no trazado). Quién aplica 130000010: sin
  identificar.

### 1.3 Guardia
- `g = clamp((1 − pen)·p5+0x80+4i, 0, 1)`, solo en la categoría 2 (impacto en guardia; damage.md). Jugador
  `FUN_14037ff30`: `p5[12]` = salida **`[8]`** de `FUN_14034cae0` (escudo), `p5[9]` = salida **`[9]`**. **Los golpes en
  guardia sí acumulan**, reducidos por ese valor del escudo. Qué campo del escudo es cada salida: sin confirmar
  (mismo hueco que weapons.md).

### 1.4 Bajada (`FUN_1401464b0`)
`T = (lo + (hi − lo)·medidor·0.01) · clamp(1/r, 0, 9999)`; `medidor −= (100/T)·dt` tras el retardo de 0.5 s.
- Petrificación: `lo/hi` = `+0x2dc/+0x2e0`, `r = b8+0x3d4`. Jugador CCP 100: **45 / 45 s → −2.22 por s**.
- Maldición: `+0x2cc/+0x2d0`, `r = b8+0x3c8`. Jugador: **22 / 22 s → −4.55 por s**.
- Enemigos (ECP): 120 / 120 s en 213 filas, 90 / 90 s en 12 (las dos).
- Con `r ≥ 70` no acumula y baja casi al instante (Bendición divina, hoguera, §4).

## 2. Al llenarse
### 2.1 Petrificación (`FUN_140145c60`, `0x140145e61..0x140145f6d`)
1. `FUN_140146430(gestor, 6, 100)`: medidor → **0**.
2. Si el bit **3** de la bandera de estado `b8+0x4b8` está puesto (`thunk_FUN_141ca218b(b8, 3)` → `0x141b9e48c`:
   `bt [b8+0x4b8+8·(n>>6)], n&63`) → solo pone **`b8+0x4c8` bit 51** y termina: **ni SpEffect ni muerte**.
   Ese bit lo pone `1000[03] (3)` = **Anillo de protección de alma** 40300000 (SpEffectRing; "Anula la muerte y la
   petrificación, pero se rompe"). El consumidor `FUN_140219ac0` (método en la tabla de datos `0x14156f190`) borra el
   bit y llama `FUN_140219a00`, que hace `FUN_14023ca90(…, ranura del anillo, 0)` y marca un bit en `+0x431`:
   presumiblemente la rotura del anillo (sin confirmar).
3. Si no:
   - aplica **SpEffect 901100** con `{id, 1, −1.0, …}` (`thunk_FUN_141b3cbe1`);
   - **`FUN_14016a400(chr+0x228)` → `FUN_14016a300(ctrl, vida actual)`**: resta toda la vida (recorte a
     `[chr+0x16c, chr+0x170]`). No mata si `FUN_14016f150(chr)` (devuelve 3 sin tocar la vida) o si `FUN_14016d8f0(chr)`
     (deja la vida en 1). Qué banderas son: sin identificar (por la forma, invulnerable / "no puede morir").
   - Registro de muerte en `b8`: `+0x759 = 1`, `+0x75c = 0` (sin atacante, `FUN_14017b410`), `+0x760 = 0`,
     **`+0x768 = 0x6e` (110)**, `+0x76c = 0`, **`+0x76d = 3`**. La muerte normal (`FUN_14016a650`, vida ≤ 0) escribe
     `+0x768 = 10`, `+0x76d = 0` y solo si `+0x759 == 0`, así que no pisa la de la petrificación.
   - Si `FUN_140569260(chr)`: `FUN_1402e7410(jugador local, 0xc)` (sin identificar; igual que el sangrado).
- **No hay estado de piedra temporal**: es muerte inmediata. No se encontró ningún SpEffect que añada la entrada `石化`
  (ningún `100070[00] (1, 872, 10, …)` en la regulation), así que en la práctica la acumulación de la petrificación
  nunca se bloquea por estado.

### 2.2 SpEffect 901100 (`SpEffectAbnormalState.emevd`) — contenido completo
| Instrucción | Efecto |
|---|---|
| `100040[08] (400010, 0)` | `SpEffectActionImpl_FullBodySfx` (ctor `FUN_14021c900`, nombre de la tabla 全身SFX) → `FUN_14023c2d0` → `FUN_140149710(sfx, 4, 400010)` → **`ChrFullBodySfxParam` 400010** (gestor `+0x710`, índice 0x40): FFX **3419** en la ranura **4**, `+0x05 = 1`, `+0x14 = 1.0`, sonido `+0x18 = 0` (ninguno). Al destruirse la acción quita la ranura 4 (`FUN_14021c5e0` → `FUN_14023c560`) |
Es la única instrucción: **901100 no toca vida, estamina ni velocidad**; la muerte la hace el código (§2.1).

### 2.3 Maldición: SpEffect 900400
| Instrucción | Efecto |
|---|---|
| `100040[01] (653, 249, 0)` | SFX **653** en el dummy **249** |
| `100070[00] (1, 872, 3, 2.0)` → `101070[02]` | entrada `呪` del grupo AbnormalStateSub durante **2.0 s** (bloquea la acumulación, §1.1) |
| `1000[00] (17, 0, 1)` | `ChrStatus_Value` (ctor `FUN_14021ae80`): índice **0x11** = byte **`b8+0x316`** (+1). El ctor salta la suma inmediata solo para 0x11; `vt+0x18` `FUN_14021a960` la suma |
- **`b8+0x316` → contador de Hueco**: `FUN_14014cef0(chr)` devuelve `b8+0x316` (0 si `b8+0x4b8` bit 58);
  `FUN_14038b49e` (desofuscado en `deob_b49e.txt`): si es ≠ 0 y `FUN_14016f0f0(chr)`, **`H = clamp(H + b8+0x316, 0, 32)`**
  (`estado+0x1ac`, hollowing.md) y llama **`FUN_1402026e0`** (bandera y apariencia de Hueco).
  → **La maldición llena = +1 nivel de Hueco** (−5 % de vida máxima por la fórmula de hollowing.md, piso 50 %).
  Coincide con el texto: "Resistencia a la maldición de lo Hueco. Los PS se reducen a medida que el estado de Hueco es
  más severo."
- La bit 58 de `b8+0x4b8` es la misma que impide sumar Hueco al morir (hollowing.md).
- Mensaje: `FUN_140102cb0` asocia la entrada `呪` al texto **`common` 21000** = "Cursed!" / **"¡Sufres maldición!"**
  (bandera 0x40000). La misma tabla: 22000 tóxico, 23000 veneno, 24000 sangrado. **No hay mensaje para `石化`**.

### 2.4 La muerte por petrificación frente a la normal
Controlador de muerte `FUN_14013c720` (estado 0 → copia el registro `b8+0x759..` a `+0x5c..+0x68`, `+0x65 = b8+0x76d`):
- **"HAS MUERTO"**: `FUN_14013d250` se llama igual (solo lo salta el tipo 4) → mismo flujo que you_died.md.
- **Hueco**: `FUN_14037dcc0` → `FUN_140202c30(estado, b8+0x76d)`: con **`+0x76d == 3` suma
  `CriminalAndDeceasedParam[0].u8@6` = 5** (normal: `u8@5` = 1). **Esta es la "muerte tipo 3" que hollowing.md tenía sin
  identificar: la petrificación da +5 de Hueco.** (Mismas excepciones que la muerte normal.)
- **Mancha de sangre en línea**: `FUN_14026b8b0`: con `+0x76d == 3` crea `NetSvrCreateStatueBloodstainJob`
  (`FUN_14026c1b0`) en vez de `NetSvrCreateBloodstainJob` (`FUN_14026c0b0`). Es la mancha de red (repetición para otros
  jugadores). Tema cerrado en el mod: solo se anota.
- **Pérdida de almas**: no se trazó una diferencia; el camino es el mismo controlador.
- **Tiempos (`DeadTypeParam`, gestor `+0x6b0`, índice 0x3a)**: id = `FUN_140164630` → `FUN_140164da0(+0x768)` da el índice
  en `{10, 20, …, 180}` y, como `ChrParam 100 +0x40/+0x44 = 100010`, id = `100010 + 100·índice`:
  normal (10) → **100010**, petrificación (110) → **101010**. Solo si `+0x54` (byte que copia `FUN_14013cc30` del
  registro de golpe) vale 1 o 2; si no, no hay fila y el controlador pasa directo al estado 3.

| Campo `DeadTypeParam` (cuándo, en s desde la muerte) | Lo que llama `FUN_14013c720` | 100010 normal | 101010 petrificación |
|---|---|---|---|
| `+0x04` | `b8+0x584` += 1 | 0.7 | 0.7 |
| `+0x08` | `FUN_14013d430` | 0.0 | 0.0 |
| `+0x10` | `FUN_14013d560` (→ `FUN_140202fe0`) | 0.0 | 0.0 |
| `+0x0c` | `FUN_14013cfa0` (`FUN_14036db30`; jugador local → `DAT_1416148f0 vt+0x90`) | 2.0 | **5.0** |
| `+0x14` | `FUN_14013d1f0`: FullBodySfx `+0x18` = **310010** (FFX 314, ranura 1) y `FUN_14013d060`: sonido `+0x24` = 444444444 | 2.0 | **5.0** |
| `+0x1c` | `FUN_14013d320`: `FUN_14031a7f0(…, +0x20 = 0.8, 1)`, `b8+0x4c8` bit 16, **quita las FullBodySfx 0, 2, 3, 4, 5** (la 4 es la piedra 3419) | 2.25 | **5.25** |
| `+0x00` | `FUN_14013cf00` (`FUN_14031a7f0(…, 0.01, 1)` o retirar el personaje) | 6.05 | **9.05** |
- Además en el estado 2, a `t ≥ 0`: `b8+0x5bc` += 1, `FUN_14013d500`, `FUN_14013d000` (`+0x28` = 255 → nada), el
  SpEffect **600** (`thunk_FUN_141c5397a(…, 600)`; `SpEffectCondition` 600 termina curaciones y estados) y `FUN_140442bb0`.
- Qué es cada acción (disolución, vuelta a la hoguera…): **sin identificar**; los tiempos son de datos. La diferencia
  confirmada: en la petrificación todo pasa **3 s más tarde** (la estatua queda 5.25 s antes de quitar el FFX 3419).

## 3. Visual, animación, sonido, HUD
- **Material / piedra**: no hay cambio de modelo ni de material en el código hallado; lo visible es la FullBodySfx
  **400010 → FFX 3419** (ranura 4). El contenido del FFX 3419 (si es la textura de piedra): no abierto.
- **Animación de muerte**: el CP de Morpheme **`DieCategory`** (`FUN_140362b00` lo enlaza, `FUN_140362c40` lo escribe) =
  controlador de muerte `+0x60` = `b8+0x768` → **110** en la petrificación (10 normal). `DieDirection` = `+0x5c`.
  Clips de `c0001` con ese nombre: **`a00_17_0710..0713 DieCurse_00..03`** (TAE 170710..170713), los únicos que aplican
  un SpEffect de la familia 9011xx (evento TAE **310000** con 901110):

| TAE | `.nsa` | Duración `.nsa` | Fin TAE (101100) | 310000 → SpEffect 901110 |
|---|---|---|---|---|
| 170710 | `a00_17_0710_DieCurse_00_untit_930f83d0.nsa` | 4.333 s | 5.0 s | 2.0–2.333 s |
| 170711 | `a00_17_0711_DieCurse_01_untit_205d52ff.nsa` | 4.333 s | 11.0 s | 0.333–0.667 s |
| 170712 | `a00_17_0712_DieCurse_02_untit_f16b3c39.nsa` | 4.333 s | 7.333 s | 3.167–3.5 s |
| 170713 | `a00_17_0713_DieCurse_03_untit_4239ed16.nsa` | 4.333 s | 9.667 s | 2.667–3.0 s |
  - Los cuatro `.nsa` son distintos (md5), 20.08 muestras/s, 58 canales. Están en
    `D:\Reversing\DS2\extracted\morpheme\c0001\`; se decodifican con `tools/re/nsa.py` y se retargetean con
    `tools/re/ds2_anims.py` (mismo camino que el resto de clips de c0001).
  - **SpEffect 901110**: `1000[00] (19, 3600, 19)` y `1000[09] (21, 3600, 6)`: bytes `b8+0x305+19/21`. Carne férrea
    (33190010) usa los mismos índices (`(19, 25, 3)`, `(21, 25, 4)`). Qué son: sin identificar.
  - Qué clip sale: resuelto en §3.1 (uno de los cuatro al azar).
- **Sonido**: 901100 / `ChrFullBodySfxParam` 400010 sin sonido. `DeadTypeParam +0x24` = 444444444 (igual en la normal).
  `c000100_snd.tae` de DieCurse (evento 201): `c8002` (0.333–4.233 s, silenciado salvo bandera, sounds.md), `c5200`+pecho,
  `c5500`, `c5000`+brazos, `c3000` (en 0712/0713); son ruidos de armadura, no un sonido propio de la piedra.
  Maldición: sin sonido en 900400.
- **HUD** (`FUN_14006d020`, tabla `DAT_1415604a0` armada por `FUN_14106bc10`): pares (medidor, estado) en este orden:
  **(6, 石化)**, **(3, 呪)**, (−1, 武器破壊 = sin barra), (5, 猛毒), (0, 毒), (1, 出血). Por cada uno: si el estado está
  activo → entrada tipo 1 con el tiempo restante; si no, si `medidor/máx > 0.02` (`DAT_1410b1410`) → entrada tipo 0 con
  esa fracción. **Máximo 3 barras** (`DLFixedVector` de 3). Dibujo: §3.2.
- Textos (NeutralSpanish = es_mx):
  - `common` 909036 "RES petrif." / 909051 "RES maldi."; 21000 "¡Sufres maldición!".
  - `iconHelp` 909036 "Resistencia a petrificación / Resistencia a quedar petrificado. / La petrificación causa la muerte
    inmediata."; 909337 "…A mayor valor, menos probabilidades de quedar petrificado."; 909410 "…Valor total de toda la
    armadura equipada."; 909322 "Reducción de petrificación / El porcentaje de daño de petrificación reducido.";
    909051 / 909412 "Resistencia a maldición / Resistencia a la maldición de lo Hueco. / Los PS se reducen a medida que el
    estado de Hueco es más severo."; 909413 (armadura); 909411 "Reducción de maldición…".

### 3.1 Clip de muerte de la petrificación (nmb, `D:\Reversing\DS2\out\diecurse\`)
- `SM_Die` = nodo `0x1804` (estado hijo [4] de la raíz `0xd2`), 15 subestados sin transiciones internas; cada
  transición global de la raíz elige el subestado con su ruta attrib 0x3e. **`DieCategory` 110 aparece en una sola**:
  transición `0x2085` = cond 149 (`0xfe8c4`, mensaje 34 `Die`, tabla de nombres de mensaje en `0xe6468`) **y** cond 150
  (`0xfe914`, tipo 0x271, CP `0x5f` DieCategory ∈ [110, 110]); mezcla 0.01 s al subestado `0x1835`.
- `0x1835` tiene el switch `0x1837` (tipo 0x83) con lista {1.0, 3.0, 5.0, 7.0}, attrib 0x39 = evaluar solo al activarse,
  valor más cercano. Su entrada es el nodo `0x1836` (tipo 0x92, attrib 0x37 `ATTRIB_TYPE_RANDOM_FLOAT_DEF`: mín 0.0,
  máx 8.0, intervalo 0, semilla 0 = `rand()`; `FUN_14064ea70` / `FUN_14064ec20`: `mín + (máx − mín)·((x & 0x7fffff)·2⁻²³)`).
  → **[0,2) DieCurse_00, [2,4) _01, [4,6) _02, [6,8) _03: uno de los cuatro al azar, 25 % cada uno**, elegido al entrar.
- Cada clip dura 4.333 s sin bucle; luego la transición `0x183d` (cond 0x25b, valor 1, 0.001 s) pasa al nodo `0x183c`
  (tipo 0x7e, **sin identificar**); no va a RagdollLoop.
- `DieDirection` = `ctrl+0x5c` × 57.29578 (`FUN_140362c40`, `0x140362e36..0x140362ecf`), copiado de **`b8+0x764`**
  (corrige el `+0x760` de §2.1). En la petrificación vale 0 (`FUN_140145c60` escribe `qword [b8+0x760] = 0` en
  `0x140145f4c`) y además no interviene en `0x1835`.
- Muerte normal (DieCategory 10; 40 por `0x2087`): `0x2086` → `0x1851`, switch `0x185b` por DieHitCategory {0, 1, 2} =
  Slash / Thrust / Effect (3–5 recortan a Effect) y dentro un switch por DieDirection {−180, 0, 180} = B / F / B
  (|ángulo| < 90° → F). Duraciones: Slash F 4.6667 / B 4.5333, Thrust F 4.0 / B 4.1667, Effect F 5.3333 / B 3.0667 s.
  Después RagdollLoop (cond 0x25a, 0.5 s).

### 3.2 HUD de los medidores (`menu/01.febnd.dcx` → `l01_06_tolerance.flo`, `D:\Reversing\DS2\out\status_hud\`)
- Escena propia a 60 fps (las instancias `0x102ca2/4/5/9` que usa `FUN_14006cb40` solo existen en este FLO). Una ranura
  ocupa x 536.55–713.50, y 446.05–481.90 en el espacio 1280×720; `FUN_14006cf10` pone hasta 3 (`0x5f5c3e0 + i`) en
  y −30 / +0.5 / +31 (clip `0x29`), la primera arriba; las que sobran se ocultan.
- `FUN_14006cb40`: muestra solo el icono `0x5f5c3e0 + k` del índice de la entrada (`FUN_14006ce40`); frame =
  `10 − valor·10` (`DAT_1410ad5e8`) + 1 (`DAT_1410ac698`) a los dos clips de barra (frame 1 lleno, 11 vacío); tipo 0 →
  clip `0x21` (acumulación), tipo 1 → `0x26` (activo). **Sin color por estado ni texto**: solo cambia el icono.
- Iconos (26×26, `99.febnd`): petrificación `waku_03` (959,135)–(985,161); maldición `Icon_tex` (189,216)–(215,242);
  fondo hexagonal `waku_02` (786.35,89.95)–(826.05,125.80). Los demás estados en el documento de la investigación.
- Barras `Boss_hp` (`01.febnd`, 128×64): acumulación fondo morado (0,34.90)–(128,42.45) y relleno verde azulado
  (0,56.70)–(128,64); activo fondo amarillo (0,42.25)–(128,49.65) y relleno naranja (0,49.60)–(128,56.35). Máscara de
  124 px con 11 claves x −0.25 → −124.25: el relleno crece de izquierda a derecha con la fracción. Marco
  `FE_HPgage_01` y placa de sombra `waku_02`. Posiciones exactas: `tools/re/status_hud.py`.

## 4. Curas
| Objeto / SpEffect | Petrificación | Maldición |
|---|---|---|
| **Bendición divina** 60105000 | `1000[01] (6, 3 s, ×100)` (vacía y no acumula 3 s) + termina `石化` (`100070[00] (1, 871, 10)`) | `(3, 3 s, ×100)` + termina `呪` (871, 3) |
| Hoguera 110000010, 150200000, 150300000 | `(6, 3 s, ×100)` + 871/10 | `(3, 3 s, ×100)` + 871/3 |
| SpEffectWideUse 100000000 / 100000020 | solo termina `石化` (871/10) | solo termina `呪` (871/3) |
- El evento 871 (`101070[01]`, `caseD_1d`) termina la entrada AbnormalStateSub (bonfire.md). **Nada baja el contador de
  Hueco ganado por la maldición salvo lo de hollowing.md (Efigie humana).**
- Quién aplica 100000000 / 100000020: ningún `ItemParam +0x04` los usa; sin identificar.
- **Rama de antaño fragante** 60537000 ("Invierte la petrificación en las cosas vivas."): `ItemParam +0x04 = 0` (sin
  SpEffect), tipo 800. Su efecto no está en la regulation (sería por evento de mapa). **Nada revierte la petrificación del
  jugador**: está muerto.
- Prevención: Anillo de protección de alma (§2.1). El Anillo de protección de vida 40310000 ("Anula la muerte, pero se
  rompe") pone la bandera **2**, no la 3: `FUN_140145c60` no la mira, así que 901100 y la resta de vida ocurren; si la
  bandera 2 evita después esa muerte no está trazado.

## 5. Enemigos que la causan (filas EDP con tipo 12 / 9)
EDP = `100000000 + modelo·10000 + …` (formato de enemies.md §4.2). Mapas: `GeneratorRegistParam` de cada mapa → EP →
modelo `EP+0x00`; nombre del mapa por coincidencia de número con `mapname` (`m10_32` ↔ 10320000).

| Estado | Modelo | Valor por golpe (× `EP+0xd8`) | Mapas |
|---|---|---|---|
| Petrificación | c1010 | 40 (+147 físico, +15 tipo 7) | Things Betwixt, Aldia's Keep, Shaded Woods |
| | c1250 | 35 (+314 físico) | Lost Bastille, The Gutter, Dragon Aerie, Grave of Saints |
| | c2100 | 10 / 5 (sin daño: varias filas, probablemente nube) | Things Betwixt, Brightstone Cove, Aldia's Keep, Heide's, Shaded Woods, Shrine of Amana… |
| | c2220 | 35 (fila 410) / 5 (resto, sin daño) | Aldia's Keep, Huntsman's Copse |
| | c2230 | 15 (+188..256 físico, +100 tipo 11) | Doors of Pharros |
| | c2260 / c2261 | 15 / 45 (+120 tipo 6) | Grave of Saints |
| | c6711 | 25 | Shulva |
| Maldición | c1190 | 50 | (ningún generador) |
| | c1480 | 35 (+316 físico) | Brightstone Cove Tseldora |
| | c3080 | 40 / 50 (+473 / 630 físico) | Shaded Woods |
| | c3180 | 200 (+464 oscuro) / 65 | Things Betwixt, Lost Bastille, Iron Keep, Drangleic Castle… |
| | c5146 | 200 (+1041 físico) | Undead Crypt |
| | c6270 | 70..110 (+ físico u oscuro) | Drangleic Castle |
| | c7540 | 150 | Majula, Forest of Fallen Giants |
- Los nombres de los enemigos no están en los datos con su modelo (no hay fmg de nombres de enemigos).
- Ejemplo: jugador desnudo, Adap 10 / Vigor 10 → fila 10 → 16 %. Golpe de c2230 (15 × 1.0): **12.6** por golpe → **8
  golpes** seguidos (sin contar la bajada de 2.22/s tras 0.5 s). Con el Anillo de nudillo de cenizas (+40 % → 56 %): 6.6.
  Maldición de c6270 (85), Adap 10 / Sintonía 10: 71.4 → **2 golpes** → +1 Hueco.

## 6. Multijugador / fantasmas
- La fila de `DeadTypeParam` de un fantasma sale de `FUN_14016f510(chr)+0x20` en vez de `ChrParam` (`FUN_140164630`).
- Maldición: los tipos de personaje 1..17 de `DAT_1410c0050` no acumulan (§1.1).
- Mancha "estatua" en línea para la muerte tipo 3 (§2.4).

## 7. Correcciones a otras notas
- **bleed.md §0**: el tipo 2 (ranura 8, 900300 / 900301) es **rotura de equipo** (`武器破壊`): las Grageas del mar
  amarillo 60220000 ("Temporarily boosts equipment break resist") dan `100090[04] (90, a 7, 20)` → `l[7]` → `abs[8]`;
  la tabla del HUD lo empareja con `武器破壊` y medidor −1.
- **hollowing.md**: la "muerte tipo 3" (+5 de Hueco) es la **petrificación** (§2.4).
- **agility.md**: el elemento 9 de `vt+0x1d8` = maldición queda confirmado (Hollow Skin, texto, `FUN_140381350`).

## 8. Estatuas petrificadas y Rama de antaño fragante
Investigación del 2026-11-18, solo lectura. Fuentes: `DarkSoulsII.exe` (decomp en `D:\Reversing\DS2\out\achiev\big.c` =
despachador de comandos ESD `FUN_140461f20`, `env.c` = `FUN_14045c6a0`; decomp nuevas en `D:\Reversing\DS2\out\petrify\d1.c..d4.c`),
regulation (`ItemParam`, `ItemTypeParam`, `ItemUsageParam`, `ItemLotParam2_*`, `ShopLineupParam`, `KeyGuideParam`,
`ChrFullBodySfxParam`, `EnemyParam`, `SpEffectAbnormalState.emevd`), params por mapa (`extracted\mapparam\Generator*`),
ESD de eventos de mapa (`gamedata:/EzState/event_<mapa>.esd`, volcados en `out\covenant_pvp\esd\`), ESD de IA
(`gamedata:/ezstate/ai*.esd`), `c0001.nmb`, TAE `c000100_pl/_sfx/_snd.tae` y de los personajes estatua, FMG.
Scripts usados: `scratchpad\*.py` (gen.py, sm.py, tae2.py, nsad.py, lots.py, kg.py…).

**Resumen:** la Rama **no es un ObjAct ni un objeto de mapa**. Las estatuas son **personajes** (generadores del mapa) con un
"estado de arranque" (startup state) petrificado. Un subprograma del ESD del mapa (el mismo en los 9 mapas) detecta que el
jugador "examina" al personaje, pregunta "¿Usar Rama…?", hace al jugador rezar de pie (`a70_18_0010_PrayStand`), a los
1.3 s gasta una Rama y enciende una bandera; otra tarea del ESD ve la bandera y pone el estado de arranque del personaje a 0,
y el personaje sale de la piedra con su clip `StartUp_XX_Out` (efecto SFX de cuerpo entero 103000).


### 1. El objeto: Rama fragante de antaño (ItemParam 60537000)

#### ItemParam 60537000 (0x54 bytes; offsets según hollowing.md / bonfire.md)
| Campo | Valor | Nota |
|---|---|---|
| `+0x00` icono | 60537000 | `IC_0060537000.tpf` (regla de hollowing.md) |
| `+0x04` SpEffect al usar | **0** | no tiene efecto propio de uso |
| `+0x08/+0x0c/+0x10` FFX en mano | 0 / 0 / 0 | |
| `+0x2c` / `+0x30` | 2001 / 7500 | `+0x30` = precio base según titanite.md (sin confirmar en código) |
| `+0x38` velocidad de uso | 0.7 | |
| `+0x40` ItemTypeParam | **800** (compartido con Piedra prisma 60450000 y Piedra de cierre de Pharros 60536000) | `+0x10` = **180** |
| `+0x44` ItemUsageParam | **2240** (única fila que la usa; bytes `ff ff ff ff 07 0c 00 00`) | |
| `+0x4a` máximo | **99** | |
| `+0x52` | 0x08 (bit 0 apagado = "se gasta" por la lectura de bonfire.md) | |

- **Se consume: sí, 1 por estatua.** Confirmado por el ESD: estado 8 del subprograma hace `131401(60537000, 1)`
  (`big.c` caso `0x20149` → `FUN_1401ac430` → `FUN_1401a7960`, el mismo comando que quita ofrendas en covenants.md).
- ItemUseCategory 180 → `a00_18_0180_ItemUsePutDown` (selector del nmb nodo `0x1759`, pesos {100,110,150,170,180,190}
  en `0x4e2a18`; hijo 5 = nodo `0x1765`; 3.667 s; TAE 180180: 220000 a 1.0 s). **No se usa en las estatuas** (ahí el
  jugador hace `PrayStand`, §2). Si el objeto se puede usar desde el menú: sin confirmar (y no tiene SpEffect).

#### Textos
| | Nombre | Corto (SimpleExplanation) | Largo (DetailedExplanation) |
|---|---|---|---|
| es (NeutralSpanish = Spanish) | Rama de antaño fragante | Invierte la petrificación en las cosas vivas. | "Una rama de árbol fragante que despide un suave / y dulce aroma. / Devuelve la vida a las cosas convertidas en / piedra. / Inhalar de forma prolongada el aroma de la rama / puede provocar tos y náuseas." |
| en | Fragrant Branch of Yore | Restores living things from petrification | "A fragrant tree branch with a faint sweet smell. / Restores the life of things turned to stone. / Extended inhalation of the branch's scent can / lead to coughing and nausea." |

(Nota: el nombre es del FMG es "Rama de antaño fragante", no "Rama fragante de antaño".)

#### Dónde se obtiene (solo lista; prefijos de mapa por patrón de id, sin confirmar)
| Fuente | Fila | Detalle |
|---|---|---|
| ItemLotParam2_Chr | 1140300 | ranura 1, ×1, 100 %, modo 3 |
| ItemLotParam2_Chr | 1153100 | ×1, 100 %, modo 0 (Mimic m10_15 gen 8010, mimic.md) |
| ItemLotParam2_Chr | 2240100 | ×1, 100 %, modo 0 |
| ItemLotParam2_Other | 1502000 | ×1, 100, modo 3 |
| ItemLotParam2_Other | 10106420 (ranura 1), 10165120, 10165140 (ranura 2), 10176180, 10185110, 10256160, 10256450, 10275050 (ranura 1), 10325040, 10326160, 20245070 | ×1, valor 1.0, modo 3 (qué significa 1.0 en modo 3: sin confirmar) |
| ShopLineupParam | 75400615 (Vieja mercader Melentia) | existencias 1, factor 1.6 |
| ShopLineupParam | 77200606 (Magerold de Lanafir) | existencias 1, factor 1.0 |


### 2. La interacción (ESD de evento de mapa)

**No es ObjAct.** Es el subprograma de 18 estados que aparece en cada `event_<mapa>.esd` (id distinto por mapa:
m10_02 `2147483579`, m10_15 `2147483507`/`2147483617`, m10_16 `2147483588`, m10_25 `2147483614`, m10_27 `2147483628`,
m10_29 `2147483642`, m10_30/m10_32 `2147483633`, m20_11 `2147483610`; mismo cuerpo). Argumentos:
`(env0 generador, env1 sin uso, env2 = 15 id de acción de evento, env3 bandera, env4 bandera 2, env5 fila KeyGuideParam,
env6 estado de arranque petrificado, env7 tarea ESD que "despetrifica")`.

Nombres de condición sacados del RTTI de los ctors (`EventCondition*::vftable`):

| Estado | Comando / condición | Qué es (fuente) |
|---|---|---|
| 16 (inicio) | `140152(8, gen, env6, 0)` | `EventConditionChr_CompareStartUpState` (ctor `FUN_140459b40`; evaluación `FUN_14046eeb0` lee **byte `+0x77` del generador**) |
| | `140501(8, env7, 1, 0)` | `EventConditionEvent_CompareTaskState` (`FUN_14045a250`) |
| | `140301(2, env3, 1)`, `140301(3, env4, 1)` | `EventConditionFlag_Compare` (`FUN_14045a2c0`): si ya está la bandera, termina |
| 2 | `130408(34, env5)` | crea un `EventKeyGuideCtrl` (`FUN_140457c90` caso `0x1fd68` → `FUN_140470bc0` → ctor `FUN_1404533d0`), busca la fila `env5` (`FUN_140453ba0` → `FUN_14044eb70`) |
| 3 | `140141(0, gen)` + `140402(1,0,0)` | `EventConditionChr_IsSearch` (`FUN_14045a0e0`) = el jugador "examina" al personaje; `EventConditionMap_IsPlayerInsideMap` |
| 5 | `130407()` | quita la guía (`FUN_140470c70`) |
| | `f131401(60537000,1,1,0)` | cuenta de la Rama (`env.c` caso `0x20149`). **Sin Rama → estado 12** |
| 12 | `130443(1017, 3, 15, 220, 2, 0, 0)` | mensaje MapEvent **1017**: es "Una estatua bloquea el camino" / en "A statue blocks your way" |
| 10 | `130441(1002, 4, 220, 2, 60537000, 0)` | diálogo MapEvent **1002**: es "¿Usar\n%s?" / en "Use %s?" con el nombre de la Rama. Respuesta `f130421`: 1 → sigue; 2/3 → cancela (estado 4) |
| 14 | `140155(0, 1)` | `EventConditionChr_DoesPlayerEventAction` |
| 6 | `132301(15)` + `140151(0, 15, 1)` | arranca la **acción de evento 15** del jugador (abajo) y espera `EventConditionChr_IsPlayerPlayingMotion(15)` (`FUN_140459dc0`) |
| 7 | `140511(0, 1.3, 2, 1.3)` | `EventConditionEvent_CompareStateTime` (`FUN_14045a170`): **1.3 s** en el estado |
| 8 | `131401(60537000, 1)` | **gasta 1 Rama** |
| | `130101(env3, 1)`, `130101(env4, 1)` | enciende las banderas (`FUN_140474a60`, gestor `eventMgr+0x20`) |
| 9 | `140151(0, 15, 0)` | espera a que termine la acción 15 y vuelve al estado 1 |

Tarea `env7` (p. ej. m10_02 grupo 4000010 → subprograma `2147483634(gen, 0, env3, 0, 0, …)`): si el personaje no está
muerto (`140111` = `EventConditionChr_IsDead`, `FUN_140459c20`) y la bandera está encendida →
**`131702(gen, 0)`**: `FUN_14017b600` (generador por id) y escribe **`+0x77 = 0`** (el mismo byte que lee
`CompareStartUpState`). Es decir: la Rama no toca al personaje directamente; pone su estado de arranque a 0.

#### Acción de evento 15 del jugador → `a70_18_0010_PrayStand`
- `132301` (`0x204cd`) → `FUN_14017be60` (arg 1 entero, `cvttss2si` si es float) → `FUN_14017c080` tipo 0: escribe en
  `chr+0xc8`: `+0xd0` = destino (el personaje estatua), `+0xd8` = −1, `+0xe0` = 15 y `+0xfc |= 0x400000`.
- `FUN_14032a200` (`0x14032a250 test [rdi+0xfc], 0x400000`) → `FUN_14015a510(15)` = **tipo 0x24 (36)** (tabla en
  `0x14015a59c`) → `FUN_140329b70(36)` = ranura `+0x68` = **`ChrEventLevelUpActionCtrl`** (relleno en `FUN_14032909a`,
  `0x14032922e`).
- `FUN_140140040`: tipo 36 → **`b8+0x2a4 = 230`** (tabla en `0x1401401c8`); `FUN_14035ff3c` lo pasa al CP de Morpheme
  **`EventActionCategory` (nodo 0x75)**.
- nmb, SM `StateMachine_Main|BT_EventAction|SM_EventAction` (nodo `0x1c24`): transición `0x1d71` con condición
  `CP 0x75 ∈ [230, 230]` (cond. 35) → estado `0x1d4a` = **`a70_18_0010_PrayStand`** (validado con otros casos: tipo 8
  hoguera → 60 → `BonfireMake`; puertas 17..21 → 120..140 → `SingleDoor*`/`DoubleDoor`; recoger 16 → 100 → `ItemPickUp`).
- `.nsa` `a70_18_0010_PrayStand_untitle_9524fe2c.nsa`: **4.667 s**. TAE 70180010: **sin eventos** en `_pl`, `_sfx` ni `_snd`.
- `ChrEventLevelUpActionCtrl` vt+0x20 (`FUN_14013f4d0`) calcula la dirección normalizada jugador → destino: el jugador
  **se gira hacia la estatua**.

#### Guía de botón (KeyGuideParam, 0x1c bytes)
| Fila | `+0x00` texto keyguide | f32 `+0x04..+0x10` | bytes `+0x14..` | Usada por |
|---|---|---|---|---|
| 1600 | 100 = es "⑲: Examinar" / en "⑲:Examine" | 1.6, 2.0, 0, 0 | `01 02 50 00 dc 00 00 b4` | todas salvo el Ogro |
| 10159000 | 100 | 2.5, 2.0, 0, 0 | `01 02 64 00 ff ff 00 b4` | Ogro de m10_15 |

- Existe keyguide 509 "⑲: Deshacer petrificación" / "⑲:Undo petrification", pero **ninguna fila de KeyGuideParam lo
  referencia en `+0x00`**; quién lo muestra: sin confirmar.
- Que 1.6 / 2.5 sean radio y 2.0 altura, y 0x50 = 80 / 0x64 = 100 un ángulo: **sin confirmar** (solo datos).


### 3. Las estatuas: 17 en total (todas las llamadas al subprograma en los 28 `event_*.esd` del juego)

Modelo = `EnemyParam +0x00`; IA = `GeneratorRegistParam +0x04/+0x08`; estado petrificado = `env6` =
`GeneratorRegistParam +0x0c` (byte; coincide en las 17); posición = `GeneratorLocation` (coordenadas locales del mapa,
rotación Y en grados). Mapa = `MapName.fmg` por formato (`mXX_YY` → `XXYY0000`, emparejamiento sin confirmar).

| Mapa | Gen. | Personaje | IA (nombre en el ESD) | Estado | Bandera `env3` / `env4` | Lote `GeneratorParam +0x88` | Pos. / rotY |
|---|---|---|---|---|---|---|---|
| m10_02 Altar encantado | 3500 | c1150 (EP 115006) | ai115020 「旅人の服（亡者）：石化ストップ」 / ai115000 「亡者_旅人の服」 | 6 | 102000050 / — | 1020200 (vacío) | (382.49, −15.36, 135.82) / −150.3 |
| m10_15 Torreón de Aldia | 2500 | **c3000 Ogro** (EP 300002) | ai300040 「オーガ：石化ストップ」 / ai300000 | 4 | 115000050 / — | 1150000: Máscara de acólito de dragón | (47.6, 17.0, −95.78) / 89.5 |
| m10_15 | 8600 | c1150 (EP 115000) | ai115020 / ai115000 | 6 | 115000051 / — | 1157000: Ascua de la adversidad ×2 | (33.2, 22.19, −103.09) / 141.8 |
| m10_15 | 8601 | c1150 | ai115020 / ai115000 | 6 | 115000052 / — | 1157100: Seta de Elizabeth ×2 + Hierba del atardecer marchita ×2 | (33.07, 22.24, −96.01) / 90.0 |
| m10_16 Fortaleza perdida | 145 | **c7680 Straid de Olaphis** (EP 768000, nombre npcMenu 7680) | ai768010 「スペルマニア」 / ai768000 | 3 | — / **102741** | — | (−49.29, 23.75, 39.11) / −179.5 |
| m10_16 | 9900 | c1150 | ai115020 / ai115000 | 6 | 116000031 / — | — | (−22.38, 8.06, −34.47) / 180.0 |
| m10_25 Alcantarilla | 8000 | c1150 | ai115020 / ai115000 | 6 | 125000027 / — | — | (−390.09, −66.52, −40.93) / 160.8 |
| m10_27 Atalaya del dragón | 6000 | c1150 | ai115020 / ai115000 | 6 | 127000030 / — | — | (−119.72, 243.52, 130.48) / −87.3 |
| m10_29 ("Majula" en el FMG) | 115 | **Rosabeth de Melfia** (EP 763000, modelo **1 = c0001**, nombre npcMenu 7630) | ai763010 「呪術屋」 / ai763000 | 15 | — / **102640** | — | (−24.65, 0.54, −12.44) / 0.0 |
| m10_30 Torre de la Llama de Heide | 9000 | c1150 | ai115020 / ai115000 | 6 | 130000010 / — | — | (17.63, −56.05, 38.57) / 131.7 |
| m10_32 Bosques sombríos | 2040 | **c3080 獅子戦士 (Guerrero del León)** (EP 308000) | ai308030 「獅子戦士：石化ストップ」 / ai308000 | 3 | 132000010 / — | 1320200: piezas del León 3 % c/u, Gran hacha del León 3 %, Escudo del clan del León 3 %, Grageas de serpiente tríclope 5 % | (83.29, 2.01, 389.69) / −70.9 |
| m10_32 | 2310 | c3080 | ai308030 / ai308000 | 3 | 132000012 / — | 1320200 | (50.11, 10.48, 360.64) / −169.3 |
| m10_32 | 2041 | c3080 | ai308030 / ai308000 | 3 | 132000014 / — | 1320000 (vacío) | (44.78, 5.44, 400.06) / 171.9 |
| m10_32 | 2210 | c3080 | ai308030 / ai308000 | 3 | 132000016 / — | 1320200 | (108.05, 0.86, 372.24) / 120.8 |
| m10_32 | 2103 | c3080 | ai308030 / ai308000 | 3 | 132000017 / — | 1320100: Máscara de brujo | (66.71, 11.14, 303.42) / −7.4 |
| m10_32 | 2500 | c3080 | ai308030 / ai308000 | 3 | 132000018 / — | 1320200 | (80.19, 11.05, 326.83) / −97.2 |
| m20_11 Santuario de Amana | 5300 | c1150 | ai115020 / ai115000 | 6 | 211000030 / — | — | (−107.44, 2.87, 145.07) / 174.6 |

- Totales: **8 c1150** (hueco "ropa de viajero"), **1 c3000** (Ogro), **6 c3080** (Guerrero del León), **2 NPC**
  (Straid, Rosabeth). **No hay estatuas con Rama en Castillo de Drangleic (m20_21)** ni en los DLC: ningún
  `event_*.esd` de esos mapas ni ningún `talk_*.esd` menciona 60537000.
- **Qué desbloquea cada una:**
  - Enemigos (c1150 / c3000 / c3080): la IA "superior" se llama 石化ストップ ("detención por petrificación"); al pasar a
    estado 0 quedan con la IA normal del enemigo (hueco, ogro, guerrero del león) → **se vuelven enemigos**. El lote es
    el botín al matarlos (lectura de mimic.md / enemies.md).
  - Además, cada bandera dispara otro subprograma `(<id>, 0, 2, bandera, 0)` que llama `132131(id, 0)` y
    `132132(id, 2)` (`big.c` `0x20423`/`0x20424`: objeto del gestor `eventMgr+0x30` vía `FUN_1404799f0`/`FUN_140459570`,
    luego `FUN_1401edd90` / `FUN_1401ee140`). Ids: m10_02 6000020; m10_15 502000 / 6005020 / 6006020; m10_16 6011020;
    m10_25 6004020; m10_30 6000020; m10_32 1002000 / 1102000 / 1202000 / 6000020 / 6001020 / 6002020; m20_11 6000020.
    m10_27 no tiene. **Qué objeto es y qué hacen esos dos comandos: sin confirmar** (probable bloqueo de paso).
  - Straid (bandera 102741) y Rosabeth (102640): las banderas las leen otras partes del ESD (p. ej. m10_16
    `2147483570(1900000, 0, 2, 0, 102741)`, grupo 111407; m10_29 grupos con `140301(*, 102640, …)`) → el NPC queda
    disponible. El detalle (tienda, diálogo) no se trazó.

#### Pose de la estatua (para decodificar; no hay poses a mano)
**No es un modelo de piedra aparte.** Son los personajes normales, con su esqueleto, en un clip de arranque de una pose
de muy corta duración; el material de piedra viene aparte (§4).

| Personaje | Pose petrificada (In) | Salida (Out) | Archivos |
|---|---|---|---|
| c1150 | `a75_01_7140_Wep01_StartUp_05_In` **0.333 s** (TAE 75017140) | `a75_01_7141_Wep01_StartUp_05_Out` **10.0 s** (TAE 75017141) | `c1150.anibnd.dcx` |
| c3080 | `a75_01_7110_Wep01_StartUp_02_In` **0.333 s** | `a75_01_7111_Wep01_StartUp_02_Out` **8.0 s** | `c3080.anibnd.dcx` |
| c3000 | `a75_01_7120_Wep01_StartUp_03_In` **0.333 s** | `a75_01_7121_Wep01_StartUp_03_Out` **10.0 s** | `c3000.anibnd.dcx` |
| c7680 Straid | `a75_01_7110_Wep01_StartUp_02_In` **0.333 s** | `a75_01_7111_Wep01_StartUp_02_Out` **10.0 s** | `c7680.anibnd.dcx` |
| Rosabeth (c0001) | `a77_05_0010_NPCIdleRosabethFossil` **0.0333 s = 1 cuadro** (TAE 77050010) | `a77_05_0030_NPCTransitRosabethFossil` **12.167 s** (TAE 77050030) | `c0001` (en el anibnd del jugador, ya retargeteable con el pipeline actual) |

- Cómo se eligió cada clip: **por datos, no por código**. Son los únicos StartUp de cada personaje con un In de 0.333 s
  y un Out largo, y los únicos Out con el evento `_sfx.tae` **2103 = 103000** (ver §4). El Rosabeth "Fossil" (化石) se
  identifica por el nombre. **Falta confirmar** la tabla estado de arranque → clip (estado 6 → StartUp_05 en c1150,
  3 → StartUp_02 en c3080, 4 → StartUp_03 en c3000, 3 → StartUp_02 en c7680, 15 → Fossil en Rosabeth): la decide la IA
  (`ai115020` etc., comandos de IA `5300/5301/5900…` sin decodificar).
- Los demás StartUp (01..04 en c1150, 01 en c3080/c3000, 01/03 en c7680) son arranques normales, no petrificados
  (duraciones 1.0–4.67 s sin el SFX 103000).


### 4. Visual y audio de la despetrificación

| Clip | `_sfx.tae` | `_snd.tae` (tipo 201, `+0` id; nombre = `c` + chr·100000 + id, sounds.md §3) |
|---|---|---|
| c1150 StartUp_05_Out | **2103 (103000) 0–2.0 s**; 260001 0–2.0 s | ids 5124 (1.333–1.5 y 2.333–2.5), 5324 (1.9–2.233), 4100 var. 1 (1.933–2.267) |
| c1150 StartUp_05_In | 260001 0–0.167 s | — |
| c3080 StartUp_02_Out | **2103 (103000) 0–1.333 s**; 260001 −0.033–1.333 | 6000, 2000 var., 4000, 6100… (16 eventos 0.1–7.667 s) |
| c3000 StartUp_03_Out | **2103 (103000) 0–3.667 s**; 260001 −0.033–3.667 | 6000 (2.267, 3.6, 6.433, 8.7 s), 2000, 4000, 8004 |
| c7680 StartUp_02_Out | **2103 (103000) 1.667–3.667 s**; 260001 1.667–3.667 | 3000 (3.9), 5224 (4.067, 7.3) |
| Rosabeth TransitFossil (77050030) | **2103 (103000) 0–1.333 s**; 260001 0–1.333 | 4000 var. 1 (0.967), 5024 (6.367, 8.233) |
| Rosabeth IdleFossil (77050010) | 260001 0–0.033 | — |
| Jugador PrayStand (70180010) | — | — |

- `103000` y `400010` son filas de **`ChrFullBodySfxParam`** (53 filas): 103000 → `+0x00` = **3665**, f32 1.0;
  400010 → `+0x00` = 3419, f32 1.0. Que TAE 2103 = "SFX de cuerpo entero" con esa fila y que `+0x00` sea el id FFX:
  **sin confirmar en código** (coincidencia de datos; 103000 solo existe en esa tabla).
- Los parámetros del TAE `260001` y su efecto: sin decodificar.
- **Material de piedra:** los modelos c1150, c3080, c3000 y c7680 traen las texturas **`EneStone_d/_n/_s`** (c3000 y
  c7680 además `EneStone_mask`). `c0001.bnd` **no** las tiene (Rosabeth usa partes de jugador). Cómo se mezcla el
  material de piedra y cómo se quita al despetrificar: **sin trazar**.
- Sonido / SFX en el jugador al usar la Rama: ninguno en el TAE de `PrayStand`.


### 5. ¿Puede el jugador volverse una de estas estatuas?

- **No por este sistema.** El subprograma solo actúa sobre **generadores** (personajes del mapa) con un estado de
  arranque petrificado; el jugador no tiene generador. Las estatuas son una característica del mundo.
- La **petrificación como estado** existe aparte: estado anormal tipo 6 (ranura 12) → SpEffect **901100**
  (bleed.md, `FUN_140145c60`), cuyo script (`SpEffectAbnormalState.emevd`) es solo `100040[08] (400010, 0)` =
  **全身SFX** (nombre de la acción en el exe, `0x1410cde90`) con la fila 400010 de `ChrFullBodySfxParam`. El texto
  iconHelp 909036 dice: "Resistance to petrification. **Petrification causes instant death.**" (es: "Resistencia a quedar petrificado.
  La petrificación causa la muerte inmediata."). La
  resistencia del jugador está en bleed.md / stats.md (ARP `+0x28`, `⌊(3·ADP+VIT)/4⌋`). La hoguera limpia
  `872 k = 10 石化` (bonfire.md). Qué mata al jugador (la muerte instantánea) **no se trazó**.
- No hay ningún vínculo encontrado entre la Rama y la petrificación del jugador (la Rama no tiene SpEffect).

## Sin confirmar
### Estado del jugador
- **Medidor del jugador**: mínimo y máximo de `chr+0x1c4+12·6` / `+12·3` (el ctor base `FUN_1403114f0` los pone a 0;
  quién pone el máximo 100 no se halló). El umbral 100 es fijo en el código.
- **Valor de `+0x54`** del controlador de muerte en la petrificación (sin él no hay fila de `DeadTypeParam`), y si
  `FUN_14013cc30` (rama del registro de golpe, bandera 0x19) puede pisar `+0x76d = 3` en el mismo frame.
- Qué hace cada acción de `DeadTypeParam` (§2.4) y si la pérdida de almas cambia.
- Que el nodo `0x1836` (tipo 0x92) sea exactamente OperatorRandomFloat (atributo y campos coinciden; la tabla que une el
  tipo con esas funciones no se halló), y qué es el nodo `0x183c` (tipo 0x7e) que sigue a los 4.333 s. Que "DieCurse" sea la
  petrificación se apoya en `DieCategory` = 110 escrito por la petrificación y en el SpEffect 901110, no en el nodo.
- SpEffect 901110 (índices 19 y 21 de `b8+0x305`) y el contenido del FFX 3419.
- **Cuántas veces suma la maldición**: si `b8+0x316` vale 1 un solo frame o más (ciclo de `ChrStatus_Value` con
  duración 0) y si tras `FUN_1402026e0` se recalcula el tope de vida en ese momento o al reiniciar (hollowing.md).
- `FUN_14016f0f0` (condición para sumar la maldición), `FUN_14016f150` / `FUN_14016d8f0` (no morir), `FUN_1402e7410(…, 0xc)`.
- Rotura del Anillo de protección de alma (`FUN_140219a00` → `FUN_14023ca90`) y la clase de `0x14156f190`.
- Guardia: qué campo del escudo son las salidas `[8]` / `[9]` de `FUN_14034cae0`.
- Duración de las acciones con `t = 0` (anillos) y quién aplica 100000000 / 100000020 / 130000010.
- HUD: la lectura de la máscara (convención de Flash), si el frame fraccionario interpola o va en pasos de 10 %, cuál de
  `entrada+0x20` / `+0xa0` es el tiempo restante, las acciones de frame 0x64/0x65/0x66, el modo de mezcla, cuándo se
  ocultan todas (`FUN_140506b40(0x101c00)`) y cómo pasa el espacio 1280×720 a la pantalla.
- Golpe enemigo: que el valor de estado no se divida por 2505 (no visto, bleed.md).
- Nombres de los enemigos de §5 (la wiki puede orientar, no está verificado) y el mapeo `mXX_YY` ↔ id de `mapname`
  (por número).

### Estatuas
1. Significado de los campos de **KeyGuideParam** (1.6 / 2.5 radio, 2.0 altura, 0x50 / 0x64 ángulo) y quién usa el
   texto keyguide 509 "Deshacer petrificación". El prompt de datos es keyguide 100 "Examinar".
2. Primer argumento `34` de `130408` y argumentos `4, 220, 2` / `3, 15, 220, 2` de los diálogos `130441` / `130443`.
3. Comparador exacto de `140511(0, 1.3, 2, 1.3)` (se lee como "≥ 1.3 s en el estado").
4. Tabla **estado de arranque → clip StartUp** (la decide la IA; elegida aquí por duración + SFX 103000).
5. Qué son los objetos `6000020`, `502000`, `1002000`… y qué hacen `132131(id, 0)` / `132132(id, 2)`; si son el bloqueo
   del camino. La frase "bloquea el camino" sale solo del mensaje 1017.
6. TAE 2103 = SFX de cuerpo entero con `ChrFullBodySfxParam` y su campo `+0x00` = FFX 3665; parámetros de 260001.
7. Cómo se aplica y se quita el material **EneStone** (ningún string del exe lo nombra).
8. Nombres de mapa por emparejamiento de FMG: m10_29 sale como "Majula" (10290000; también 10040000 = Majula). La wiki
   pone a Rosabeth en Bosques sombríos: no verificado.
9. Lotes `ItemLotParam2_Other` modo 3 con valor 1.0 (¿peso, %?) y el significado de los prefijos de id; precio de tienda
   (`+0x30 × factor` = 12000 Melentia / 7500 Magerold, regla de titanite.md sin confirmar).
10. Si la Rama se puede usar desde el inventario (ItemUsageParam 2240 sin decodificar; sin SpEffect daría nada).
11. Qué hacen Straid y Rosabeth al despertar más allá de la animación (tienda/diálogo vía banderas 102741 / 102640).
12. Qué causa la muerte instantánea del jugador petrificado (SpEffect 901100 solo pone el SFX de cuerpo entero).

## 9. En el mod
- **Medidores** (`SoulsStatus`): petrificación y maldición del jugador en Modo Souls, umbral 100, retardo 0.5 s, bajada
  45 s / 22 s, resistencia de stats (PSPL +0x50 / +0x54, exportado como `absorbPetrify` / `absorbCurse`) y armadura
  (ARP +0x28/+0x58, +0x2c/+0x5c). Fuentes solo de datapacks (decisión del usuario, 2026-11-18): `petrify` / `curse` en
  la fila de daño de un enemigo (× `attackMultiplier`), `/souls status`, `bse.status`. Hoguera y reaparecer los vacían.
- **Petrificación llena**: muerte (`soulsmode:petrified`, sin armadura, efectos ni enfriamiento), +5 de Hueco
  (`SoulsHollow.onDeath`), cuerpo en piedra con uno de DieCurse_00..03 al azar y su último cuadro hasta 5.25 s
  (`StatueLivingEntityMixin`, `LocalPlayerStoneMixin`). Un tótem que la evita deja al jugador sin piedra.
- **Maldición llena**: +1 de Hueco, "¡Sufres maldición!", 2 s sin acumular (la barra activa del HUD).
- **HUD** (`StatusHud`, `tools/re/status_hud.py`): las piezas de `l01_06_tolerance.flo`, escaladas a la altura de la
  pantalla y centradas como la pantalla de muerte.
- **Estatuas** (`SoulsStatues`): aldeano, aldeano zombi, pillager y vendedor ambulante en piedra (diseño del usuario),
  generados sueltos en la superficie del Overworld (`soulsmode:petrified_statue`, 1 de cada 256 chunks, valor del
  mod). Sin rama: MapEvent 1017; con rama: MapEvent 1002, PrayStand (4.667 s, mirando a la estatua), a 1.3 s se gasta
  una rama y el mob vuelve, persistente (el vendedor sin temporizador de partida). Rama en cofres (20 % por cofre, valor
  del mod): puestos de saqueadores, mazmorras, minas, portales en ruinas, templos del desierto y de la jungla.
- **Piedra**: la textura del mob en gris sobre el bloque de piedra de Minecraft (`StoneTextures`), sin objetos en las
  manos, sin parpadeo rojo y sin el balanceo de reposo.
- Lo que quedó fuera: `docs/todo-bse-extensible.md`, "Petrificación, maldición y estatuas".
