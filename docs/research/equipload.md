# Carga de equipo (peso)

Salidas de Ghidra y scripts: `D:\Reversing\DS2\out\equipload\` (`d1.c`..`d16.c`, `eq34.c`, `traj.py`, `tae_dodge.py`,
`cancel.py`, `scanrows.py`, `armor_items.txt`). Ver también [stats.md](stats.md), [stamina.md](stamina.md),
[animations.md](animations.md), [movement.md](movement.md).

## 1. Ratio de carga — `FUN_14037e430` (jugador, rama con `DAT_1410bfff3`)
```c
eq   = chr vt+0x120 → vt+0x68;                       // objeto de equipo
b0+0x28 = b8+0x4ac × eq+0x44 + (float)(s8)b8+0x306;  // peso efectivo
b0+0x2c = b8+0x3e0 × atributos+0x34;                 // carga máx. (= derivada +0x08, Vitality)
b0+0x30 = b0+0x28 / b0+0x2c;                         // ratio, lo leen todas las curvas
```
Se recalcula si cambia `eq+0x3b` (equipo modificado) o alguno de los cuatro campos de `b8`.

**Peso total `eq+0x44`** = `FUN_14008dda0` (lo escribe `FUN_14034a740` al cambiar el equipo):

| Parte | Función | Campo | Slots |
|---|---|---|---|
| Armas | `FUN_14034b390` | `WeaponParam +0x20` (f32) | las 6: 3 por mano (`eq+0x58 + (mano·3+slot)·0x48`), activas o no |
| Armadura | `FUN_14034b2e0` | `ArmorParam +0x34` (f32) | 4: cabeza, pecho, brazos, piernas (`eq+0x298/+0x2c8/+0x2f8/+0x328`) |
| Anillos | `FUN_14034b340` | `RingParam +0x00` (f32) | 4 (`eq+0x3d0/+0x3e8/+0x400/+0x418`) |

Munición y objetos no cuentan. Accesores: `ArmorParam` `FUN_140358350` (slot `+0x4a0`), `RingParam` `FUN_140358f70`
(`+0x530`). Sin armadura: ids `11001100..03` (peso 0, `FUN_140349a80`).

**Multiplicadores** `b8+0x3e0` (carga máx.) y `b8+0x4ac` (peso): el ctor de `b8` (`FUN_14031bcf0`) pone **1.0** en todo
`b8+0x3bc..+0x4b7`; `b8+0x306` (sumando s8) = **0**. **No encontré quién los cambia** (ninguna escritura directa en el código
desensamblado; seguramente efectos especiales/anillos en código ofuscado). Sin efectos: `ratio = peso / cargaMáx`.

## 2. Umbrales (`ChrEquipLoadParam` fila 1): 0 / 30 / 70 / 100 / 120 %
Interpolación lineal entre umbrales (`FUN_140320230`); por encima de 120 % se queda en el último valor.
`vfunc+0x30` devuelve valor × 0.01; `vfunc+0x38` el valor crudo. **Sobrecarga** = `ratio·100 > 120` (`FUN_140381bd0`, vt+0x28).

## 3. Movimiento — `PlayerMovementCtrl` (`FUN_140385fe0`, asm `1403869b4..140386b59`)
Cada frame:
- **Velocidad**: `b8+0x11c[0] = (1 − fila·0.01) × (sobrecarga ? 0.3 : 1)` (`FUN_14031c990`; 0.3 = `movectrl+0x168`, ctor
  `FUN_140385b40`). La lee `FUN_14031c7f0` en `FUN_1403888f0` (velocidad objetivo): `velocidad = base × b8+0x11c[0]`.
- **Giro**: `b8+0x138[0]` igual con otras filas (`FUN_14031c970`); lo multiplica `FUN_140387f40` (velocidad de giro).

Fila según el modo `movectrl+0xb0` (`b8+0x705 ≠ 0` → fila 6 / 0x13, estado sin identificar):

| Modo | Base (`ChrMoveParam` fila 100) | Fila vel. | Fila giro |
|---|---|---|---|
| 3 dash | `+0x44..+0x48` 4.3–6.15 m/s (fijado `+0x8c..+0x90` 5.0–6.5) | 5 `[0,0,0,0,40]` | 0x11 / 0x12 fijado `[0,0,0,0,70]` |
| 1 andar (y 11, 13) | `+0x04..+0x08` 1.2–1.9 m/s (fijado `+0x68..+0x6c` 1.2–4.2) | 3 `[0,0,0,0,70]` | 0x10 `[..70]` / 0xf fijado `[..50]` |
| resto (trote: `+0x1c..+0x20` 0.5–4.295 m/s) | | 4 `[0,0,0,0,50]` | 0x10 / 0xf |

Multiplicadores resultantes:

| Carga | ≤100 % | 110 % | 120 % | >120 % |
|---|---|---|---|---|
| Andar (fila 3) | 1.0 | 0.65 | 0.30 | 0.09 |
| Trote (fila 4) | 1.0 | 0.75 | 0.50 | 0.15 |
| Dash (fila 5) | 1.0 | 0.80 | 0.60 | — (no hay dash) |
| Giro filas `[..70]` / `[..50]` | 1.0 | 0.65 / 0.75 | 0.30 / 0.50 | 0.09 / 0.15 |

- **>120 %**: `FUN_14037e430` suma 1 a `b8+0x588` y a `b8+0x5e0` mientras dure. `+0x588 ≠ 0` impide el dash (petición 0 y
  `FUN_140385620`, ver stamina.md) y `+0x5e0` pone la pose de cansancio (`BlendWeight_Tire`). No hay animación de "andar
  lento" propia: es la misma locomoción más lenta.
- Velocidad del movimiento raíz (trayectoria del `.nsa`, ver §6): Walk 1.823 m / 1.0667 s = 1.71 m/s, Jog 3.3 m / 0.7667 s
  = 4.30 m/s, Dash 3.6 m / 0.6 s = 6.0 m/s.
- Ya documentado: regen fila 0x1d y drenaje del sprint fila 0x1e (stamina.md).
- Cosmético: `FUN_140385690` usa filas 0x27..0x2b × `PlayerCommonParam +0x98..+0xa8` para un peso en `b8+0x178`
  (consumidor sin identificar). CP Morpheme `LoadTotalWeight` = fila 2 × 0.01 (`FUN_14035e490`); su efecto en la red no
  está trazado (existe `a00_50_0120_AdditiveLoadLeftIdle`).
- Salto: `FUN_140361930` usa la fila 0x1c `[0,6,14,20,50]` × `ChrMoveParam +0x230/+0x234/+0x238` para un CP de su lista
  (sin identificar). No portable al salto de Minecraft sin más análisis.

## 4. Rodar y paso atrás
**Variante** (`FUN_140367ea0` → `FUN_14038e770(ratio)`): `≤70 %` normal; `70 < x ≤ 120 %` → peticiones 8 / 0xb →
`b8+0x9f` / `+0xa0` = 2 ("StaminaEmpty"); `>120 %` → 6 / 0xa → 3 ("Failed") (`FUN_140328b10` / `FUN_140328c20`).
**Los nombres "StaminaEmpty" y "Failed" no tienen que ver con la estamina**; los `.xmd` se llaman *Tired*.
El coste de estamina no depende de la variante (300 / 210, ver stamina.md).

Árboles Morpheme (`c0001.nmb`, `out/grip/grip_tree.py`):

| Estado | Nodos | Animaciones (blend por CP, pesos del nodo 0x6b) |
|---|---|---|
| `BT_DodgeTakeoff` (0x17f7) | speed(`DodgeSpeed`) → blend(`DodgeDistance`, 3.2 / 6.0) | 120020 `a00_12_0020_DodgeRollingShort`, 120021 `…_0021_DodgeRollingLong` |
| `BT_DodgeTakeoffStaminaEmpty` (0x17fe) | speed → blend(`DodgeDistance`, 4.0 / 5.0) | 120030 `…_0030_DodgeRollingShortTired`, 120031 `…_0031_DodgeRollingLongTired` |
| `BT_DodgeTakeoffFailed` (0x1803) | speed, sin blend | 120040 `…_0040_DodgeRollingFailed` |
| `BT_Step` (0x1eca) | blend(`StepDistance`, 0.5 / 3.0) | 110011 `a00_11_0011_StepBackRightShort`, 110031 `…_0031_StepBackRightLong` |
| `BT_StepStaminaEmpty` (0x1edc) | speed(`StepSpeed`) → blend(0.5 / 3.0) | 110051 `…_0051_…ShortTired`, 110071 `…_0071_…LongTired` |
| `BT_StepFailed` (0x1ef3) | sin blend | 110081 `…_0081_StepBackRightFailed` |

**CP `DodgeDistance` / `StepDistance`** = longitud del vector de esquiva `b8+0xb0` (`FUN_14035f750` / `FUN_14035f980`), que
calcula `FUN_14031fed0` (calc vt+0x258) al pedir la esquiva (`FUN_140367cc0` → `manip+0x60`):
`dist = base × (1 − filaCarga·0.01) × escala × (1 − penalización(PlayerLackOfStatsParam))`, mínimo 0.1.
`escala` = producto `b8+0x120..+0x128` (1.0 en el ctor). La penalización usa la falta de stats y un término de estamina
(`thunk_FUN_141ca621f(estamina, coste)`, ofuscado, sin resolver); sin falta de stats y con estamina suficiente vale 0.

| Esquiva | vfunc | Base | Fila de carga | Fila LackOfStats |
|---|---|---|---|---|
| Rodar normal | vt+0xe0 `FUN_14031e710` | `ChrMoveParam+0x110` = 5.0 | 0x19 `[0,10,28,40,50]` | 0x14 |
| Rodar Tired | vt+0xe8 `FUN_14031e820` | `+0x114` = 4.2 | 0x1b `[0,0,0,100,100]` | 0x15 |
| Paso normal | vt+0xf0 `FUN_14031e920` | `+0x104` = 2.5 | 0x17 `[0,9.9,23.1,33,40]` | 0x16 |
| Paso Tired | vt+0xf8 `FUN_14031ea00` | `+0x108` = 2.5 | 0x26 `[0,12,28,40,80]` | 0x17 |

**CP `DodgeSpeed` / `StepSpeed`** (velocidad de reproducción): 1.0, salvo en la variante Tired: fila 0x1a cruda
`[1,1,1,0.9,0.95]` / fila 0x25 `[1,1,1,1,0.95]`.
**`DodgeLoadWeight`, `StepLoadWeight`, `DodgeDirection`, `StepDirection` no existen en `c0001.nmb`** (`FUN_1404e2870`
devuelve falso y no se escriben). Corrige animations.md: la fila 0x18 no mezcla nada.

Distancia del movimiento raíz (trayectoria `.nsa`, §6) y resultado:

| Variante | Animación: duración, distancia raíz | CP 0 % / 30 % / 70 % / 100 % / 120 % | Distancia esperada |
|---|---|---|---|
| Rodar normal (≤70 %) | Short 1.5333 s 3.2 m; Long 1.5333 s 6.0 m | 5.0 / 4.5 / 3.6 | = CP (pesos = distancias): 5.0 / 4.5 / 3.6 m |
| Rodar Tired (70–120 %) | ShortTired 1.8 s 3.397 m; LongTired 1.8 s 5.397 m | 4.2 (70 %) / 2.1 (85 %) / 0.1 (≥100 %) | 3.80 m a 70 %, 3.40 m desde 71.4 % |
| Rodar Failed (>120 %) | 1.5 s, 0.503 m | — | 0.5 m |
| Paso normal (≤70 %) | Short 1.1667 s 0.5 m; Long 1.1667 s 2.304 m | 2.5 / 2.25 / 1.92 | ≈1.94 / 1.77 / 1.53 m |
| Paso Tired (70–120 %) | ShortTired 1.5 s 0.5 m; LongTired 1.5 s 3.0 m | 1.8 / — / 1.5 / 0.5 | = CP: 1.8 / 1.5 / 0.5 m |
| Paso Failed (>120 %) | 1.6667 s, 0.5 m | — | 0.5 m |

"Distancia esperada" supone que el blend 2 de Morpheme mezcla la trayectoria linealmente con
`w = clamp((CP − p0)/(p1 − p0))`; eso es el runtime de Morpheme, **no verificado en el código de DS2**.

Eventos TAE (`c000100_pl.tae`) y pistas `c0001.nmb`:

| Anim | i-frames 110500 | 110100 | Sin regen 101100 | Avoid (re-esquiva) | Ataque 1L / 1H | Guardia |
|---|---|---|---|---|---|---|
| 120020/21 | 0.100–0.667 | −0.167–0.133 | –0.933 | 0–0.8 | 0–0.5 / 0–0.9 | 0–0.733 |
| 120030/31 Tired | 0.100–0.667 | −0.167–0.133 | –1.167 | 0–1.0 | 0–0.867 / 0–1.133 | 0–0.833 |
| 120040 Failed | **ninguno** | — | –1.167 | 0–0.975 | 0–1.167 | 0–0.975 |
| 110011/31 | 0.167–0.333 | — | –0.833 | 0–0.267 | AttackAll 0–0.333 | — |
| 110051/71 Tired | 0.333–0.500 | — | –1.0 | 0–0.567 / 0–0.733 | AttackAll 0–0.667 / 0–0.833 | — |
| 110081 Failed | **ninguno** | — | –1.167 | 0–1.083 | AttackAll 0–1.3 | 0–1.083 |

Tiempos en segundos de animación; en Tired se estiran por `1/DodgeSpeed` (si el TAE sigue el reloj de la animación, sin
verificar).

## 5. Pesos de las armas del mod (`WeaponParam +0x20`)
Longsword 1220000 = 3.0 · Battle Axe 2010000 = 4.0 · Mace 2410000 = 4.0 · Club 2400000 = 3.0 · Spear 2800000 = 3.5 ·
Pickaxe 2610000 = 8.0 · Drangleic Shield 11230000 = 6.0 · Fists 3400000 = 0 · antorcha 6000000 = 0.

## 6. Trayectoria del `.nsa` (confirmado en código 2026-11-02, hit_reaction.md §12)
`.nsa +0xb8` → bloque: `+0x20` f32 frecuencia, `+0x24` u16 frames, `+0x28` 3×f32 offset, `+0x34` 3×f32 escala,
`+0x58` u64 offset de las claves (relativo al bloque); clave u32 de 11/11/10 bits = `v·escala + offset`, eje Y.
Validación: el Jog sale a 4.30 m/s (`ChrMoveParam+0x20` = 4.295) y los pesos del blend de rodar (3.2 / 6.0) son
exactamente las distancias de sus animaciones. Script: `out/equipload/traj.py`.

## 7. Armaduras: ArmorParam = ItemParam − 10000000 (`ItemParam +0x18`), peso `+0x34`
Lista completa: `out/equipload/armor_items.txt`. **Propuesta, pendiente de aprobación del usuario (no aplicada):**

| Minecraft | Set DS2 (ArmorParam cabeza/pecho/brazos/piernas) | Pesos | Total / sin brazos |
|---|---|---|---|
| Cuero | Hunter's Hat / Leather Armor / Gloves / Boots (11040100..03) | 1.2 / 4.7 / 2.8 / 2.8 | 11.5 / 8.7 |
| Malla | Creighton's Steel Mask / Chainmail / Chain Gloves / Chain Leggings (17420100..03) | 4.3 / 9.7 / 3.4 / 4.5 | 21.9 / 18.5 |
| Hierro | Knight Helm / Armor / Gauntlets / Leggings (11050100..03) | 4.2 / 10.9 / 3.5 / 6.4 | 25.0 / 21.5 |
| Oro | Helm / Armor / Gauntlets / Leggings of Aurous (11360100..03) | 1.2 / 3.2 / 1.2 / 2.0 | 7.6 / 6.4 |
| Diamante | Drangleic Helm / Mail / Gauntlets / Leggings (17240100..03) | 5.9 / 14.5 / 6.2 / 7.6 | 34.2 / 28.0 |
| Netherita | Smelter Demon Helm / Armor / Gauntlets / Leggings (13050100..03) | 9.0 / 17.6 / 10.4 / 10.4 | 47.4 / 37.0 |
| Caparazón de tortuga | Old Bell Helm (11660100) | 6.0 | — |

Alternativas: malla → Heide Knight (13010100..03, 28.7); oro → la otra fila de Aurous (11361100..03, 22.9) o King's
(15130100..03, 27.1); diamante → Alonne Knight (13061100..03, 35.3); netherita → Havel's (11160100..03, 50.0) o Ironclad
(12510100..03, 54.9); tortuga → Gyrm Helm (12340100, 8.2).

## Sin confirmar
- Quién escribe `b8+0x3e0`, `b8+0x4ac`, `b8+0x306` (anillos/efectos que suben la carga).
- `thunk_FUN_141ca621f`: término de estamina en la distancia de esquiva.
- Qué es `b8+0x705` (filas 6 / 0x13) y los modos 11 / 13.
- Escala del TAE con `DodgeSpeed` y mezcla lineal de la trayectoria en el blend.
- Consumidores de `LoadTotalWeight`, `b8+0x178` y del CP de salto (fila 0x1c).

## Implementado (`SoulsEquipLoad`, `ds2/Dodge`, `ds2/EquipLoadRows`)
- **Qué pesa (elecciones del usuario, 2026-10-30/31)**: todo el inventario, **un peso por stack** (no por unidad).
  - Armadura: set de DS2 por material, pieza a pieza (casco = cabeza, peto = pecho, pantalones = piernas, botas =
    guanteletes): cuero 11.5, malla 21.9, hierro (Caballero) 25.0, oro (Rey 15130100..03) 27.1, diamante (Drangleic)
    34.2, tortuga 6.0; netherita = pieza de diamante + 1 lingote de netherita.
  - Por lingote = set ÷ 24: hierro 1.04, oro 1.13, diamante 1.43, cuero 0.48; lingote de netherita = 4 oro + 4
    fragmentos (pesan como hierro) = 8.68. Materiales sin armadura: densidad real frente al hierro (7.87): madera 0.6,
    piedra 2.7, tierra 1.6, vidrio 2.5, ligero 0.3, obsidiana 2.6, cobre 8.96, carbón 1.35, gemas 2.7.
  - Armas de DS2: `WeaponParam +0x20` × material frente al hierro (netherita = diamante + 1 lingote).
  - Bloques: 9 unidades de su familia (etiquetas y tipo de sonido) × volumen de su forma.
  - Palo: madera. Comida y objetos de mob: 0.6 (valor del usuario). Resto: densidad 0.3.
- **Efectos (DS2)**: velocidad de andar / trote / dash con las filas 3 / 4 / 5 (× 0.3 sobre 120 %), sin dash sobre
  120 %; variante de esquiva por carga (normal ≤ 70 %, Tired ≤ 120 %, Failed) con sus animaciones, i-frames, bloqueos
  y regen; distancia de la esquiva por las filas 0x19 / 0x1b / 0x17 / 0x26 (mezcla lineal Short/Long, no verificada).
- Velocidad de reproducción de Tired (CP `DodgeSpeed` / `StepSpeed`, filas 0x1a / 0x25 crudas: rodada 0.9 a 100 %,
  0.95 a 120 %; paso atrás 0.95 a 120 %): la animación se reproduce más lenta y sus ventanas (i-frames, bloqueos,
  regen, duración) se estiran por 1 / velocidad. **Verificado en el código**:
  - Los nodos 0x17fc / 0x1edb (tipo 0x7d) usan la tabla de colas 13 de `c0001.nmb` (cabecera de tablas en 0xe72f8:
    42 tablas × 74 semánticas; tabla 13 en 0xe9268): semántica 0 (`UPDATE_TIME_POS`) → fn 126
    `nodePlaySpeedModifierQueueUpdateTime` (`FUN_14064c460`), semántica 0xc (`SYNC_EVENT_TRACK`) → fn 127
    `nodePlaySpeedModifierQueueEventTrack` (`FUN_14064c280`); el resto, `queuePassThroughChild0` (registro en
    `FUN_140606e10`, id = orden + 1).
  - Tarea 0x86 `TaskScaleUpdateTime` (`FUN_140612640`): Δt del hijo = max(0, CP) × Δt del padre (si el padre trae
    tiempo absoluto, pasa sin escalar). Tarea 0x87 `TaskScaleUpdateSyncEventTrack` (`FUN_140612580`): duración de la
    pista de sincronía / CP.
  - El nodo de animación avanza su posición con ese Δt (`TaskAnimSyncEventsUpdateTimePos`, `FUN_14060ec00`) y muestrea
    sus pistas con su propia `FRACTION_POS` (`TaskSampleEventsFromSourceTracksIncDurationEvents`, `FUN_140619b00`);
    el nodo 0x7d deja pasar los eventos muestreados sin tocarlos.
  - `MorphemeEventTrackCtrl` (`FUN_1404dc380`) lee el `SAMPLED_EVENTS_BUFFER` (semántica 0xd) del nodo raíz y reparte
    por userData de pista (`ChrEventTrackHandler`, `FUN_14036be00`): 80 = `CancelControl_*` →
    `ChrEventTrackActionStateControl`; 1000 = `TimeAct` → `ChrEventTrackActionTimeAct` (`FUN_14036b1b0`).
  - El tiempo de TAE = (1 − valor muestreado del evento `TimeAct`) × duración del TAE (`MorphemeTimeActPlayer` +0x34 =
    frames / fps, `FUN_1404e4950`) y se lanza con `FUN_1404e4ad0`; no hay contador de tiempo real. Así 110500
    (i-frames), 101100 (regen) y las pistas `CancelControl_*` siguen el reloj de la animación.
- **Sin portar**: la velocidad de giro, la curva temporal
  del avance (el empuje dura 0.6 s rodando / 0.3 s en paso atrás, valor provisional).

## Implementado: giro por carga
- `b8+0x138` (`FUN_14031c970`): filas 0x10 (fijado 0xf) andando / trotando y 0x11 (fijado 0x12) en dash, mismo
  cálculo que la velocidad (× 0.3 sobre 120 %). Se aplica a la velocidad de giro del cuerpo con la cámara Souls y fijado.
