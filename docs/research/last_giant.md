# El Último Gigante (c3096) en el gigante de Minecraft

Pedido del usuario (2026-11-03): al romper una hoguera activa aparece un gigante de Minecraft (zombi gigante) como
castigo, con el moveset del jefe de DS2 **The Last Giant**; se queda en la zona hasta que lo matan, con los VFX y el
mensaje de invasión de los invasores NPC. Esta nota junta lo que dice DS2 del jefe (como c3000_ogre.md para el Ogro).
**Solo investigación y exportación de datos**: el código del mod no se tocó.

Script: `tools/re/last_giant_anims.py` (retarget de `enemy_anims.py`, lector de rig de `ogre_anims.py`). Salidas:
- `src/main/resources/assets/soulsmode/enemy_animations/c3096_<idTAE>.json` (47 clips, formato Bedrock / PAL);
- `src/main/resources/data/soulsmode/ds2/c3096_attacks.json` (fases, tablas de la IA, acciones, pasos, golpes,
  trayectorias, locomoción, daño, muerte, filas EDP / DCP, EnemyAttackMoveParam, bullets).

Uso: `python tools/re/last_giant_anims.py src/main/resources/assets/soulsmode/enemy_animations src/main/resources/data/soulsmode/ds2/c3096_attacks.json`

## 1. Quién es
- Nombre: `talk/charaname` id **3096** = `'The Last Giant'` (3097 = `'Giant Lord'`). El id de texto coincide con el
  modelo.
- **Modelo c3096**, **EnemyParam 309600** (modelo 3096, ECP / EMP 309600). Lo pone el generador del Bosque de los
  Gigantes Caídos: `GeneratorRegistParam_m10_10_00_00` fila **30960000** = `(309600, 309610, 309600)` →
  EnemyParam 309600, **ESD superior `ai309610`**, **inferior `ai309600`** (enemy_ai.md §3).
  - Nombres internos: `ai309600_下位：新・巨人上級兵士（亡者）` ("inferior: nuevo soldado gigante superior (hueco)") y
    `ai309610_上位：…（亡者）`. El Señor de los Gigantes (EP 309700) usa `ai309700` / `ai309710` (`…（生者）`, "vivo").
  - También existe **EnemyParam 3096** (mismo modelo, ECP / EMP 309600, solo modo 1, PV 2770, poise 4180, almas 235);
    lo nombran params de otros mapas (`GeneratorRegistParam_m10_14`, `m20_21`, `m50_36`, `m50_37`). **Qué es: sin
    confirmar**; no es el jefe del bosque.

### 1.1 Archivos (en `D:\Reversing\DS2\extracted\c3096\`, sacados de `GameDataEbl.bdt` con `extract_bdt.py`)

| Ruta del juego | Tamaño | Desempaquetado |
|---|---|---|
| `gamedata:/morpheme4/chr/c3096.anibnd.dcx` | 3 347 111 | `undcx` → `c3096.anibnd`; `unpack_bnd4_ex.py` → `anibnd\` (106 `.nsa` + `c3096.nmb`) |
| `gamedata:/model/chr/c3096.bnd` | 660 320 | `unpack_bnd4_any.py` → `model\` (`c3096.flv`, `c3096_h.hkx`, ragdoll, `.acb`) |
| `gamedata:/model/chr/c3096.texbnd` | 21 411 863 | sin desempaquetar |
| `gamedata:/timeact/chr/c309600_pl.tae` | 75 648 | se lee directo con `tae.py` (108 animaciones) |
| `gamedata:/ezstate/ai309600.esd` / `ai309610.esd` | 36 416 / 8 992 | volcados con `esd.py` a `D:\Reversing\DS2\out\ai3096\ai3096{00,10}.txt` |

(También `ai309700/10` del Señor de los Gigantes, volcados al mismo directorio.) Nota: `esd.py` como script no
cierra el archivo de salida y el texto quedaba truncado; los volcados se hicieron con `esd.dump` y el archivo cerrado.

## 2. Params
### 2.1 EnemyParam 309600 (enemies.md §2)

| Campo | Valor |
|---|---|
| `+0x28` PV (NG) | **2530**; ciclos 2..8 (`+0x140..`): 5313, 6376, 7439, 7970, 8501, 9033, 10095 |
| `+0x2c` / `+0x30` estamina / regen | 1·10⁸ / 125 (en la práctica infinita) |
| `+0x4c` / `+0x50` modos 0 / 1 | `EnemyEquipWeaponParam` 30960000 / **30961000** → juegos de comportamiento **309600** / **309610** |
| `+0x74` estabilidad de guardia | 30 |
| `+0x78..+0x98` % bloqueado | 60 en todos |
| `+0x9c` defensa física | **380** |
| `+0xa0..+0xac` absorción magia / rayo / fuego / oscuro | **30 / 30 / 30 / 10 %** |
| `+0xb0..+0xbc` | 30 / 30 / 100 / 100 (índices 6..9, sin confirmar qué son) |
| `+0xd0` almas | **0** (el jefe no paga por EnemyParam; la fuente de sus almas — `BossParam 309600`, `BossBattleParam 1010010` — **sin decodificar**) |
| `+0xd8` multiplicador de ataque | 1.0 |
| `+0xdc` / `+0xe0` / `+0xe4` | 0.8 / 0.25 / 1.0 (sin identificar) |
| `+0xfc` / `+0x100` segundo medidor | 21978 / 2.2 por s |
| **`+0x118` / `+0x11c` poise** | **0** / 1.47 |
| `+0x134` lote | 40 |
| `+0x24` ChrMultiplayParam | 100 |

- **No reacciona a los golpes normales**: `EnemyCommonParam 309600 +0x04` = **7** (columna de reacción). En
  `DamageMotionWeightParam` la columna 7 vale 0 en todas las filas salvo las de nivel 7 (`x080`: 5080 / 6080 → 1).
  Como la reacción sale de esa tabla (hit_reaction.md §1), con poise 0 igual no hace animación de daño con las armas
  del jugador.
- `ECP +0x1ac` = 1.7453 (100°); `+0x30c..+0x318` = 0.
- `EnemyMoveParam 309600`: `+0x20` 1.7, `+0x2c` / `+0x30` **1.5708 / 1.5708 rad/s (90°/s)** → giro fuera de un
  ataque 90°/s quieto o andando (enemy_ai.md §8.5); `+0x54 / +0x58` 1.5708, `+0x78` 1.5708, `+0x48` 5.5.
- `EnemyAttackMoveParam` (giro durante el ataque, solo con `chr.b8+0x4ec` / `+0x4f0`, sin confirmar quién los pone):

| Fila | `+0x08` → `+0x0c` | `+0x10` … `+0x14` | `+0x24` | Quién la usa |
|---|---|---|---|---|
| 2000 | 2.0944 → 2.0944 (120°/s) | 1.0 … 2.0 | 1.0 | 13, 16, 50 paso 0, 51 paso 1 (modo 0) … |
| 2100 | 2.0944 → 2.0944 | 1.0 … 2.0 | 1.0 | 12, 14, 17, 18, 50 paso 1 … |
| 39 | 2.0944 → 2.0944 | 1.0 … 2.0 | 5.0 | modo 1: 1, 8, 9, 19 (y Behavior[1] del modo 1) |
| 0 | 3.1416 | 0 | 0 | acciones 0 y 1 del modo 0 (`+0x18` = 0) |

- `BossPartsParam 30960001..30960006` (daño por partes, enemies.md §7.3): existen; **disposición sin decodificar**.
- Tamaño / cápsula del cuerpo: no está en EnemyParam ni en `ChrModelParam`; seguramente en `c3096_h.hkx`, **sin leer**.
  De la pose de enlace: cadera (`Root`) a **4.967 m**, cuello 8.96 m, cabeza 9.08 m, hombros a ±1.21 m.

### 2.2 Juegos de comportamiento (enemy_ai.md §7)
`EnemyBehaviorParam` / `EnemyBehaviorSecondParam` (entradas de 0x58; acción = índice / 10 + índice) y
`EnemyComboBehaviorParam` (entradas de 0xd8; acción = 50 + índice), solo las encendidas (`+0x1f` / `+0x07` ≠ 4):

| Modo | Acción | Entrada | EDP `+0x00 + 4k` | AMP |
|---|---|---|---|---|
| 0 | 0 | Behavior 309600[0] | — | 0 |
| 0 | 1 | Behavior[1] | 130960001, 130960003 | 0 |
| 0 | 12 / 13 | Second[2] / [3] | 130960210 / 130960310 | 2100 / 2000 |
| 0 | 14 | Second[4] | 130960410, 130960420 | 2100 |
| 0 | 15 | Second[5] | 130960510 (la IA no la pide) | 39 |
| 0 | 16 / 17 | Second[6] / [7] | 130960610 / 130960710, 130960720 | 2000 / 2100 |
| 0 | 18 | Second[8] | 130960810, 130960815 | 2100 |
| 0 | 50 (`+0x00` 10) | Combo[0] | 130960010, 020, 030, 040 | 2000, 2100 |
| 0 | 51 (11) | Combo[1] | 130960110, 120 | 2100, 2000 |
| 0 | 52 (15) | Combo[2] | 130960510, 520, 530 | 2000, 2000, 2100 |
| 1 | 1 | Behavior 309610[1] | 130960001, 130960003 | 39 |
| 1 | 8 / 9 | Behavior[8] / [9] | 130961008 / 130961009 | 39 |
| 1 | 12 / 13 | Second[2] / [3] | 130961210 / 130961310 | 2100 / 2000 |
| 1 | 14 | Second[4] | 130961410, 130961420 | 2100 |
| 1 | 16 | Second[6] | 130961510 (la IA no la pide) | 39 |
| 1 | 17 / 18 / 19 | Second[7] / [8] / [9] | 130961610 / 130961710, 130961715 / 130961810 | 2000 / 2100 / 39 |
| 1 | 50 (10) | Combo[0] | 130961010, 020 | 2000, 2100 |
| 1 | 51 (11) | Combo[1] | 130961110, 120 | 2000, 2000 |
| 1 | 52 (16) | Combo[2] | 130961510, 520, 530 | 2000, 2000, 2100 |

Clip de cada acción: `a65_NN_MSSS` (enemy_ai.md §8.2) con `M` = modo + 1; `NN` = acción o `+0x00` del combo; `SSS` =
010 / 020 / 030 por paso, **050** en las acciones 0, 1, 8 y 9 (son los únicos `.nsa` que hay para ellas,
`…_OneActi…`). Todas las acciones encendidas tienen su `.nsa` y no sobra ninguno de ataque.

## 3. Esqueleto y retarget
- `c3096.nmb`: 79 huesos (mismo lector que c3000, `ogre_anims.Rig`; un solo mapa canal → hueso de 78 entradas).
  Humanoide con dedos y `F_Pillar` / `B_Pillar`, más una cadena **`R_Weapon → R_Weapon_Main → R_Wep_L_Hand / …
  R_Wep_L_Forearm → R_Wep_L_UpperArm`** con dedos: es **el brazo izquierdo arrancado que empuña en la fase 2**.
  `c3096.flv` confirma que es su arma: las dummies **100** (`R_Wep_L_Forearm`) y **101** (`R_Wep_L_UpperArm`) son las de
  los golpes del modo 1 (cápsula r 0.75, largo 3.8).
- Gigante de Minecraft (1.21.1): `GiantZombieModel` extiende `AbstractZombieModel` (= `HumanoidModel`), y
  `GiantMobRenderer` lo escala ×6. Cadera a 12 px (piernas con pivote y = 12 y 12 px de largo) → en el mundo
  72 px = **4.5 bloques**, contra 4.967 m de DS2 (**0.906**). Cabeza / cuello: 24 px × 6 = 9 bloques contra 8.96 m.
  Las proporciones casi coinciden 1:1 en metros = bloques.
- Retarget: el de c1020 (c1020_anims.md §3) sin cambios, porque `enemy_anims.Retarget` ya usa `12 / Root z` px por
  metro (12 / 4.9672 = 2.416):

| Parte de Minecraft | De DS2 |
|---|---|
| `body` (rotación de todo el modelo sobre la cadera, punto (0, 12, 0) px) + `position` | rotación de `Spine1` respecto de su pose de enlace; desplazamiento de `Pelvis` × 2.416 px/m |
| `head` | `Head` respecto del pecho |
| `right_arm` / `left_arm` | `R/L_UpperArm` → `R/L_Hand`, giro por la bisagra `R/L_Forearm` |
| `right_leg` / `left_leg` | `R/L_Thigh` → `R/L_Foot`, giro por `R/L_Calf` |
| **`held_arm`** (solo clips del modo 1 y 65001050) | `R_Wep_L_Forearm` (empuñadura) → `R_Wep_L_UpperArm` (codo), bisagra Z de `R_Wep_L_Forearm`; `position` = empuñadura respecto del hombro derecho (`R_UpperArm`), en el marco del pecho, ejes del modelo de Minecraft, px |

  - Los brazos del `HumanoidModel` ya giran sobre el hombro (pivote x = ±5), así que no hace falta el arreglo de
    pivote del golem (c3000_ogre.md §2).
  - `held_arm` es **un dato extra**: el modelo del gigante no tiene esa parte. Si el mod la dibuja (una caja hija
    del brazo derecho, o un objeto en la mano), la pose sale de ahí; si no, se ignora.
  - La pose de enlace de c3096 es una "A" (manos a 4.26 m del centro, a 5.0 m de altura): el retarget va por
    direcciones absolutas, así que el reposo de DS2 sale con los brazos abiertos, como corresponde.
  - El gigante de DS2 va **muy encorvado**: en el quieto (7001) `body` ≈ 69° hacia delante y las piernas,
    relativas al pecho, −59° / −91° (casi verticales en el mundo). Es la pose del `.nsa`, no un error.
- Los valores de Euler se desenvuelven dentro de cada clip, así que entre un clip y el siguiente pueden diferir en
  múltiplos de 360° (pasa en 65112010 → 65112020 en `body` y 65162010 → 65162020 en `right_arm`).

## 4. IA (`ai309600` inferior, `ai309610` superior)
### 4.1 Fases y mapas
Byte 0 de las variables lógicas (`f5900(0)`, enemy_ai.md §5.1) = 37 / 38 / 39 → el superior (grupo 10, estado 3) pide
el mapa **10 / 20 / 30** del inferior (`S2147483647(env)` → `5301(env)`).
1. **Arranque**: el superior (`S2147483646`) pone los bytes 0, 1, 2 = 37 → mapa 10. Cuando su búsqueda
   (`S2147483645`) encuentra objetivo pone el byte 1 = 38. El mapa 10 del inferior espera byte 0 = 37 y byte 1 = 38,
   hace `5230(0, 6)` y `5234(0)` (**órdenes sin identificar**) y pone byte 0 = 38, byte 1 = 45 → mapa 20.
2. **Fase 1 (mapa 20, modo 0)**: estado 2 → 3 → 5 = tabla `S2147483644` (§4.2); vuelve por el estado 4 (byte 2 =
   38; vuelve al 2 si `f7300() != 0`).
3. **Cambio de fase**: en cada paso por el estado 2 del mapa 20:
   `cmd 5:5300(f5200() < 50 && f5207(1) != 1, 0, 39)`.
   - **`f5200()` = PV en %**: `ChrAi vt +0xd8` = `FUN_1404252f0` = `trunc(chr vt+0x1b8 / chr vt+0x1c0 × 100)`
     (`DAT_1410acb18` = 100.0); en las vtables de personaje `0x1410df218` / `0x1410e4bb8`, `+0x1b8` =
     `FUN_140312910` (`chr+0x168`, PV actuales) y `+0x1c0` = `FUN_140312a20` (`chr+0x174`, el máximo guardado al
     crear, enemies.md §3).
   - Así: **por debajo del 50 % de PV** el byte 0 pasa a 39 y el superior pide el mapa 30.
4. **Fase 2 (mapa 30)**: estado 1: si `f5100() == 0` (modo 0) → estado 5 = `S2147483646(10)`: `5232()`, espera
   `f5000()`, **`5204(0, 0)` = acción 0** (Behavior[0], sin golpes) = clip **65001050** (`Attack_00_OneAction`,
   8.667 s, **arrancarse el brazo**); termina con éxito si `f5100() == 1`. Ya en modo 1 → tabla `S2147483643`
   (§4.3).
   - Quién pasa el modo a 1: no es una orden del ESD. 65001050 lleva el evento TAE **2500** (parámetro 1) en
     4.533–4.833 s y 270001 en 3.267–8.667 s; que el 2500 cambie el modo de arma: **sin confirmar**.
   - El modo 1 elige el juego 309610 (EP `+0x50`, enemy_ai.md §7.1): todas sus acciones usan los clips `M` = 2.
5. Funciones sin identificar en estas transiciones: `f5207(1)`, `f7300`, `f5003`, `f5065`, `f5621`, `f5903`,
   `f5908`, `f5909`, `f7303`. Ojo: **con `f7300() = 0` (como en el Ogro) el estado 4 del mapa 20 / 30 no vuelve nunca
   a la tabla**; en pelea tiene que valer ≠ 0. Qué devuelve: sin confirmar.

### 4.2 Tabla de la fase 1 — `S2147483644` (estado 3; `r` = `f1(0)` = `RandInt(1, 100)`)
Se comprueba en cada tick **mientras corre el acercamiento** `S2147483645(5, 0.8, 1, 3, 10, 2.5)` (§4.4); la primera
fila que se cumple gana. `a` = `f7002()` (°, absoluto), `d` = `f7018(mín, máx)`, "lado" = `f7003(0, 179)` ≠ 0,
"vel." = byte 4 que escribe el acercamiento (37 / 38 = corriendo, ver §4.4; "lento" = ni 37 ni 38).

| # | Estado | Ángulo | Lado | Vel. | `r` | Distancia | Acción |
|---|---|---|---|---|---|---|---|
| 1–3 | 10 | < 10 | — | lento / 38 / 37 | < 10 | 7–9 / 9.2–11.2 / 11.5–13.5 | **18** |
| 4–6 | 5 | < 60 | — | lento / 38 / 37 | — | 6–7 / 8.2–9.2 / 10.5–11.5 | **17** |
| 7 | 12 | 70–90 | sí | — | < 50 | 2.5–4.5 | combo **51** (5 m, 120°, corte 30) |
| 8 | 9 | < 90 | sí | — | < 100 | 2.5–4.5 | **16** |
| 9 | 11 | 30–150 | no | — | — | 2.5–3.5 | combo **50** (5 m, 120°, corte 30) |
| 10 | 13 | < 60 | no | — | — | 2.5–5 | combo **52** (5 m, 90°, cortes 10 / 30) |
| 11 | 8 | — | no | — | < 30 | 0–3.5 | **14** |
| 12 | 6 | — | no | — | — | 0–3.5 | **12** |
| 13 | 7 | — | sí | — | — | 0–3.5 | **13** |

- Al volver el acercamiento sin atacar: `d < 8` → estado 4 = `S2147483642(1, 5)` = **acción 1**; si no, sale.
- Después de cada ataque (estado 2): `r = RandInt(1, 100)`, byte 4 = 0; `0 < r < 10` y `d < 4.5` → acción 1; si no,
  sale (el mapa 20 vuelve a entrar en la tabla).
- `S2147483647(acción, 0)` (ataques simples): `5204(acción, 0)` **donde está**, sin acercarse ni girar (a diferencia
  del Ogro); termina con `f5001() != 1`.
- `S2147483642(1, 5)`: `5420`, `5232`, espera `f5000()`, una vuelta de `5465(…)` / `7011` y `5204(1, 0)`.

### 4.3 Tabla de la fase 2 — `S2147483643` (estado 3; acercamiento `S2147483645(5, 1, 1, 3.5, 10, 2.5)`)

| # | Estado | Ángulo | Lado | Vel. | `r` | Distancia | Acción |
|---|---|---|---|---|---|---|---|
| 1–3 | 13 | < 5 | — | lento / 38 / 37 | — | 7–10 / 9.2–12.2 / 11.5–14.5 | **19** |
| 4–6 | 11 | < 10 | — | lento / 38 / 37 | < 10 | 7–9 / 9.2–11.2 / 11.5–13.5 | **18** |
| 7–9 | 7 | < 60 | — | lento / 38 / 37 | — | 6–8.5 / 8.2–10.7 / 10.5–13 | **17** |
| 10 | 10 | < 60 | no | — | — | 2.5–6.5 | combo **52** (5 m, 90°, cortes 10 / 30) |
| 11 | 14 | < 135 | no | — | — | 3.5–6 | **8** |
| 12 | 14 | 135–180 | no | — | — | 6–9 | **8** |
| 13 | 15 | < 60 | sí | — | — | 3–5 | **9** |
| 14 | 15 | 60–90 | sí | — | — | 5–7 | **9** |
| 15–17 | 8 | < 90 / 90–135 / 135–180 | no | — | — | 2–3.5 / 3–5 / 4–6 | combo **50** (4.5 m, 180°, corte 30) |
| 18–19 | 9 | < 135 / ≥ 135 | sí | — | — | 2–5 / 3.5–7 | combo **51** (5 m, 90°, corte 30) |
| 20 | 6 | < 180 | no | — | < 30 | 0–3.5 | **14** |
| 21 | 4 | < 180 | no | — | — | 0–3.5 | **12** |
| 22 | 5 | < 180 | sí | — | — | 0–3.5 | **13** |

Mismo final que la fase 1 (acción 1 con `0 < r < 10` y `d < 4.5` tras cada ataque; `d < 8` al volver el acercamiento).

### 4.4 Subrutinas
**Combo `S2147483641(combo, alcance, ángulo, 0, c0, c1, 0, 0)`**: `5232()`, espera `f5000()`, `5204(combo, 0)` donde
está; a los 0.5 s mira el paso con `f5008()`. En el paso `k`:
- **corte por azar**: `0 < f1(0) < c_k` → `5208` (no sale el paso siguiente). `f1(0)` es la tirada de la tabla
  (`RandInt(1, 100)`), una por pasada. Combos 50 / 51: con `r < 30` solo sale el paso 0. Combo 52: `r < 10` → 1 paso,
  `10 ≤ r < 30` → 2, si no, 3;
- **corte por posición**: `f7001() ≥ alcance` o `f7002() ≥ ángulo` (con `f5013() != 1`), o `f5050(14/13/7)`,
  `f5643() != 1` (sin identificar).

**Acercamiento `S2147483645(e0..e5)`** (fase 1: 5, 0.8, 1, 3, 10, 2.5; fase 2: 5, 1, 1, 3.5, 10, 2.5):
- al entrar: byte 3 = 0 si `d < e3`, 38 si `d < e3 + e0`, 37 si no; byte 4 = 0;
- byte 3 = 37 y `a < 30` → **corre** (`5535(…, e4, e4, e1, e2, …)`), byte 4 = 39 entre 0.4 y 0.7 s, **38 entre 1.1 y
  1.3 s, 37 desde 1.3 s** (por eso las filas de alcance largo piden 38 / 37);
- byte 3 = 38 y `a < 45` → **camina** (`5505(…, e1, e2, …)`), mismos bytes; se rinde a `f2(0) ≥ e5` (2.5 s);
- si no, **gira** (`5903`) hasta `a < 45` (o `a < 30` para correr);
- a `d < e3` (3 / 3.5 m) para (`5508`) y gira; se rinde a los `e4` = 10 s;
- los estados 5, 6, 11, 12, 16, 17 usan `5536`, `5565`, `5506`, `5566` (moverse de lado / alrededor) por navmesh
  (`f5600`, `f5602`, `f5604`, `f5605`, `f5620`, `f7086`, `f7310`): **sin identificar, no portables tal cual**.
- Qué significan los argumentos de `5505` / `5535` (e1, e2): sin confirmar (enemy_ai.md §5.3).

## 5. Ataques (clips, golpes, raíz)
Duración: `.nsa +0x28`. Golpes del TAE:
- **2200** (`0x898`): ranura `k` = `parámetro[0]` → fila EDP `k` de la entrada (enemy_ai.md §8.3);
- **2202** (`0x89a`, `FUN_1403269e0`, decompilado en `out\c3096\f3269e0.c`): **golpe con id de daño explícito**
  (`parámetro[0]`), condición en el byte `parámetro[1]` (0 = siempre; 1 = solo con `b8+0x4c8` bit 18), poder ×
  `EP+0xd8`; lo libera al terminar el evento como el 2200;
- **2301** (`0x8fd`): bullet (`EnemyBulletParam`, id de daño) (casting.md §2). Los dos bullets que usa son quietos:
  **100001000** (y 100000310 en el 65012050): origen 0 (dummy del dueño), vida **0.25 s**, velocidad 0, 1 disparo, no
  se destruye al tocar → **onda de impacto** con el volumen de su fila EDP (esferas de 0.5–1.8 m, cápsulas de 2.3 m).

"Fin `HitDuration`" = pista 123 de `c3096.nmb`. Raíz (izquierda, atrás) m al final; giro de la trayectoria al final.

**Modo 0 (fase 1)**

| Acción | Paso | TAE | s | Golpes (s) → fila (dummy) | Fin `HitDuration` | Raíz / giro |
|---|---|---|---|---|---|---|
| 0 | — | 65001050 | 8.667 | ninguno (2500 en 4.533–4.833) | — | 0 |
| 1 | — | 65011050 | 3.833 | 2200 1.9–2.233 k1 → 130960003 (6); 1.967–2.3 k0 → 130960001 (5); 2301 2.2 → 130960004, 2.3 → 130960002; 2202 2.267–2.4 → 130970000 (tasa 0); 110600 1.2–2.5 | 2.5 | **0, +10.0** (salta 10 m atrás) |
| 12 | — | 65121010 | 5.833 | 2.5–2.7 → 130960210 (5 = pie D); 2301 2.667 → 130960211 | 2.667 | 0 |
| 13 | — | 65131010 | 5.833 | 2.5–2.7 → 130960310 (6 = pie I); 2301 2.667 → 130960311 | 2.667 | 0 |
| 14 | — | 65141010 | 10.5 | 2.4–2.6 k0 → 130960410; 4.733–4.933 k1 → 130960420; 7.0–7.2 k0 → 130960410; 2301 tras cada uno (…411 / …421) | 7.167 | 0 |
| 16 | — | 65161010 | 8.5 | 2.267–2.6 → 130960610 (1 = mano D) | 2.833 | 0 |
| 17 | — | 65171010 | 8.167 | 1.967–2.933 k0 → 130960710; 3.033–3.733 k1 → 130960720 | 2.833 | 0, −1.854 |
| 18 | — | 65181010 | 16.667 | 4.1–4.667 k0 → 130960810 (249); 4.367–4.867 k1 → 130960815 (3); 2202 4.733–4.933 → 130960000 (0), 4.767–5.2 → 130960823 (9, cápsula r 2.3 / 3.9); 2301 4.767 → 130960822; 110600 2.933–13.033 | 5.0 | +0.46, **−9.19** (se lanza 9 m) |
| 50 | 0 | 65101010 | 6.0 | 1.933–2.3 k0 → 130960010; 2.1–3.1 k1 → 130960020 | 2.333 | 0 |
| 50 | 1 | 65101020 | 7.333 | 2.067–2.2 k2 → 130960030; 2.1–2.5 k3 → 130960040; 2301 2.167 → 130960031 | 2.167 | 0, +2.91 |
| 51 | 0 | 65111010 | 6.0 | 2.0–2.367 k0 → 130960110 | 2.333 | 0 |
| 51 | 1 | 65111020 | 8.667 | 2.7–2.867 k1 → 130960120; 2301 2.833 → 130960121 | 2.833 | 0 |
| 52 | 0 / 1 / 2 | 65151010 / 020 / 030 | 6.0 / 6.5 / 7.767 | 2.033–2.2 k0 / 2.733–2.933 k1 / 2.533–2.767 k2 → 130960510 / 520 / 530; 2301 después (…511 / …521 / …531) | 2.167 / 2.9 / 2.667 | 0 |

**Modo 1 (fase 2, con el brazo)**

| Acción | Paso | TAE | s | Golpes (s) → fila | Fin `HitDuration` | Raíz / giro |
|---|---|---|---|---|---|---|
| 1 | — | 65012050 | 3.833 | igual que 65011050 (bullet 100000310) | 2.5 | 0, +10.0 |
| 8 | — | 65082050 | 7.5 | 2.833–3.5 → 130961008 (100) | 3.5 | 0, +1.94; **−180°** |
| 9 | — | 65092050 | 7.5 | 2.833–3.5 → 130961009 (100) | 3.5 | +1.07, +1.41; **+90°** |
| 12 / 13 | — | 65122010 / 65132010 | 5.833 | 2.5–2.7 → 130961210 / 130961310; 2301 2.667 | 2.667 | 0 |
| 14 | — | 65142010 | 10.5 | como 65141010 con 130961410 / 420 (y …411 / …421) | 7.167 | 0 |
| 17 | — | 65172010 | 7.5 | 2.567–3.467 → 130961610 (100) | 3.5 | 0 |
| 18 | — | 65182010 | 17.0 | como 65181010 con 130961710 / 715, 2202 → 130961722, 2301 → 130961721 | 4.667 | +0.46, −9.19 |
| 19 | — | 65192010 | 6.667 | 2.567–2.767 → 130961810 (100); 2301 2.7 → 130961811 y 130961813 | 2.667 | 0, **−5.74** |
| 50 | 0 / 1 | 65102010 / 020 | 6.333 / 6.667 | 2.567–2.767 k0 → 130961010; 2.3–2.6 k1 → 130961020 | 2.667 / 2.667 | paso 1: −2.32, +0.73; **−90°** |
| 51 | 0 / 1 | 65112010 / 020 | 6.333 / 6.667 | 2.267–2.7 k0 → 130961110; 2.533–2.733 k1 → 130961120; 2301 2.7 (…121 / …122) | 2.667 / 2.667 | paso 1: +0.20, +0.05; **+45°** |
| 52 | 0 / 1 / 2 | 65162010 / 020 / 030 | 6.0 / 5.267 / 8.567 | 2.3–2.5 k0 / 2.267–2.467 k1 / 3.133–3.333 k2 → 130961510 / 520 / 530; 2301 dobles | 2.4 / 2.367 / 3.233 | 0 |

- 65102020, 65162020 y 65162030 repiten en 0–0.133 s los 2301 del paso anterior. Si es un re-disparo real o un
  resto de la mezcla: **sin confirmar** (están en el JSON tal cual).
- **Giro de la trayectoria**: 65082050 (−180°), 65092050 (+90°), 65102020 (−90°), 65112020 (+45°) giran con la raíz.
  Decodificado con el formato de hit_reaction.md §12 (`trajectoryYaw` en el JSON, + = antihorario visto desde arriba,
  hacia la izquierda del modelo). El resto de clips de ataque tiene la rotación constante.
- Superarmadura (TAE 110600, poise.md §5): acciones 1 (1.2–2.5 s) y 18 (2.933–13.033 s). Con columna 7 da igual.
- Pistas por clip (`tracks` en el JSON): `AttackStateControl` 106 / 100 / 101 / 102, `HitDuration` 123,
  `AttackCancelControl_Attack` 112, `StateControl` 80.

### 5.1 Encadenado de combos
Como en el Ogro (c3000_ogre.md §5), el exe no se trazó, pero **la primera pose de cada paso coincide con la del
anterior al final de su `HitDuration`** en los 8 encadenados (suma de diferencias de Euler de los 6 huesos, módulo
360°): 65101010 → 020 9.3°; 65111010 → 020, 65151010 → 020, 65151020 → 030, 65102010 → 020, 65112010 → 020,
65162010 → 020, 65162020 → 030: **0.0–0.2°**. Al final de `AttackCancelControl_Attack` o del clip, 250–1170°.
→ el paso siguiente arranca en el fin de `HitDuration` (lectura de las poses, no del código de Morpheme).

### 5.2 Filas de daño (EnemyDamageParam → DamageCtrlParam)
Todas físicas (tipo 0), piso 34, tasa 1.0 (salvo las de valor 0), `+0x20` 1.2. Dummies de `c3096.flv`: 1 `R_Hand`,
2 `L_Hand`, 3 `Spine1`, 5 `R_Foot`, 6 `L_Foot`, 9 `Master`, 10 `R_Forearm`, 11 `R_UpperArm`, 20 `L_Forearm`,
21 `L_UpperArm`, 50 / 51 `R_Calf` / `R_Thigh`, 60 / 61 `L_Calf` / `L_Thigh`, **100 / 101 `R_Wep_L_Forearm` /
`R_Wep_L_UpperArm`** (el brazo arrancado), 249 `Spine`.

| DCP | Reacción (`+0x10` / `+0x12`), nivel | Poise `+0xa0` / regla | Al jugador (columna 2) | Filas (físico) |
|---|---|---|---|---|
| 730960000 | 3 / 4, nivel 5 | 45 / 1 | 3 (DmgCategory 40) | 130960001 / 003 (622), 040 / 720 (240) |
| 730961000 | 3 / 4, nivel 5 | 45 / 0 | 3 | 130960002 / 004 (426, bullet), 031 / 121 / 1011 / 1012 / 1121 / 1122 (407) |
| 730960010 | **5** / 4, nivel 5, `+0x18` 0.25 s | 9999 / 2 | 5 (DmgCategory 60) | 010 / 011 / 110 / 111 / 610 / 611 / 710 / 711 / 1008 / 1009 / 1020 / 1110 / 1610 / 1611 (473), 020 / 021 (426) |
| 730960020 | **8** / 4, nivel 5, 0.25 s | 9999 / 2 | 8 (DmgCategory 90) | 030 (631), 120 / 510..530 / 910 / 1010 / 1120 / 1510..1530 (594), 210 / 310 / 410 / 420 / 1210 / 1310 / 1410 / 1420 (622), 1810 / 1812 (612) |
| 730960030 | 5 / 4, nivel 5, 0.25 s | 9999 / 2 | 5 | 810..821, 1710..1720 (640) |
| 730961010 | 5 / 4, nivel 3, 0.25 s | 9999 / 2 | 5 | ondas `…11` / `…21` / `…31` (407–426), 822..825 / 1721..1724 (463) |
| 700010310 / 700010320 / 700010130 | 23, nivel 3 | 0 | sin fila | 130960000 / 130960005 / 130960006 / 130970000 (todo 0, tasa 0) |

- Estamina `+0x24`: 4355 (golpes) / 2720 (ondas). Velocidad / distancia de empuje `+0x2c` / `+0x28`: 0.9 / 1.5 m
  (reacción 3, 8), 1.0 / 13 m (reacción 5), 1.0 / 15 m (730960030).
- Volúmenes: golpes de mano / pie cápsula r 0.65, largo 0.65–0.8; brazo arrancado cápsula r 0.75, largo 3.8;
  ondas esfera r 0.5–1.8 (130960031 crece de 0.5 a 1.75 en 0.2 s); caída del 18 cápsula r 2.3, largo 3.8–3.9.
- Fila por fila (tipos, volumen, DCP) en `damageRows` del JSON. `poise.py` **no** las lee todavía (solo mira
  `c1020_attacks.json` / `c3000_attacks.json`).

## 6. Locomoción, daño, muerte
Locomoción exportada (`loop`, sin eventos de golpe):

| TAE | `.nsa` | s | Raíz por ciclo | Velocidad |
|---|---|---|---|---|
| 7001 | `a00_00_7001_Wep01_Idle` | 8.0 | 0 | 0 |
| 7002 | `a00_00_7002_Wep02_Idle` (modo 1) | 8.0 | 0 | 0 |
| 37001 | `a00_03_7001_Wep01_Walk` | 3.0 | 6.5 m adelante | **2.167 m/s** |
| 47001 | `a00_04_7001_Wep01_Jog` | 2.0 | 12 m adelante | **6.0 m/s** |

- Hay `Wep02` solo para el quieto. Qué andar / trotar usa la red en el modo 1 (los `Wep01`, o los sin arma 30010 /
  40010, mismas velocidades): **sin trazar**. También hay giros 180° (37071 / 37091, 47071 / 47091), giros en el
  sitio (10011 ×4, 10012 ×2), pasos laterales (67011..67014 a 1.125 / 0.75 m/s, 77011..77014 a 1.83 m/s) y saltos;
  no exportados.
- **Daño** (exportados, sin trazar qué estado de `c3096.nmb` los pide): `DmgExtraHeavySlash` 160115 / 116 / 125 /
  155 / 156 / 165 (3.167 s; `DamageDuration` 0–2.0, `StateControl` 3.0–3.167; raíz 0 / 0 / 0 / +2 / −2 / +2 m),
  `DmgBossHit` 168000 (0.333 s), `DmgBreakParts03` 168103 (5.667 s, `DamageDuration` 0–5.333). Con la columna 7
  los golpes normales no llegan a ninguno (§2.1); `DmgBreakParts03` es seguramente la rotura de partes
  (`BossPartsParam`), **sin confirmar**.
- **Muerte**: 170010 `DieNormalSlash_F` (8.333 s, raíz +0.81 atrás), 170016 (F, sin raíz), 170020 `DieNormalSlash_B`
  (−0.75). Cuál pide la red (`DieCategory`): sin trazar.
- **Arranque**: 75017101 `Wep01_StartUp_01` (6.0 s, raíz 6.85 m adelante; 2202 a 0.333 / 1.0 / 1.333 s → 130960000
  sin daño, 2.8–3.033 → 130960910 (594, reacción 8), 2301 3.0 → 130960911). En DS2 es salir de la pared al empezar
  la pelea; que lo lance el `5230(0, 6)` del mapa 10: **sin confirmar**. Sirve como animación de aparición.

## 7. Para el mod (propuesta, el código lo escribe el usuario)
- **Datos**: `c3096_attacks.json` — `phases[]` (tabla de cada fase con sus filas en orden, acercamiento, después del
  ataque), `phaseChange` (PV < 50 % → acción 0 / 65001050 → modo 1), `actions[]` (modo, acción, combo, pasos con
  golpes 2200 / 2202 / 2301, `hitDurationEnd`, pistas, trayectoria y `trajectoryYaw`), `locomotion`, `damage`,
  `death`, `startup`, `damageRows`, `attackMove`, `bullets`.
- **Clips**: `c3096_<TAE>`; los del modo 1 y 65001050 traen además `held_arm` (rotación y posición).
- Unidades: metros de DS2 = bloques; el gigante de Minecraft mide 0.906 del de DS2, así que alcances y raíz casi
  coinciden con el modelo sin escalar.
- Diferencias con el Ogro que importan al portar:
  - los ataques simples **no se acercan ni giran**: la tabla se evalúa mientras el acercamiento corre y lanza en el
    sitio cuando la distancia cae en la banda de la fila;
  - las filas de alcance largo dependen de la "velocidad" (byte 4) del acercamiento;
  - `f7003(0, 179)` (lado) decide entre 12 / 13, 8 / 9 y 50 / 51; **qué lado es "verdadero"** no está confirmado
    (§8);
  - giro fuera del ataque 90°/s (EMP 309600), más lento que el Ogro (260–280°/s).

## Sin establecer
- **`f7003(0, 179)`**: el caso `0x1b5b` de `FUN_1404319b0` llama a `FUN_14043b4a0` (ángulo con signo entre el frente y
  el objetivo, con un producto vectorial; decompilado ofuscado en `out\c3096\f43b4a0.c`). Que devuelva "el objetivo
  está entre 0 y 179° hacia un lado", y **hacia qué lado**, no está confirmado.
- **Quién pasa a modo 1** tras la acción 0 (¿el TAE 2500 de 65001050?): sin confirmar. Tampoco cuándo desaparece el
  brazo izquierdo del modelo (visibilidad de mallas de `c3096.flv`): sin trazar.
- **`f5207(1)`** en la condición del cambio de fase (con 0 queda "PV < 50 %"), **`f7300`** (con 0 el bucle no vuelve
  a la tabla), `f5003`, `f5013`, `f5065`, `f5621`, `f5643`, `f5903`, `f5908`, `f5909`, `f7303`, `f7086`, `f7310` y
  las de navmesh (`f5600..f5620`): sin identificar.
- **Órdenes** `5230(0, 6)`, `5234(0)` (arranque), `5232`, `5420`, `5465`, `5508`, `5536`, `5565`, `5506`, `5566`
  (movimientos del acercamiento y laterales): sin identificar; los laterales **no portados**.
- **Acercamiento**: qué son `e1` / `e2` (0.8 / 1 y 1 / 1) en `5535` / `5505`.
- **`f7018`** corrige la distancia por el cuerpo (`chr+0x2b8+0x3c`, enemy_ai.md §5.2): detalle sin confirmar; con un
  cuerpo de 3.6 bloques de ancho puede cambiar las bandas.
- **Encadenado de combos**: por la pose (§5.1), no por código.
- **Giro durante el ataque** (`EnemyAttackMoveParam` 2000 / 2100 / 39, 120°/s): solo con `chr.b8+0x4ec` / `+0x4f0`,
  sin confirmar quién los pone (enemy_ai.md §8.5).
- **Volumen del golpe**: dummies y formas están en `damageRows`; el eje de la cápsula sigue sin confirmar
  (enemy_ai.md §8.4). Dummy de origen de los bullets 2301 (origen 0 = dummy del dueño): que sea la `+0x31` de su fila
  EDP, sin confirmar.
- **2301 al inicio de 65102020 / 65162020 / 65162030** (repiten los del paso anterior): re-disparo o resto de mezcla,
  sin confirmar.
- **Reacciones al daño y muerte**: qué estado de `c3096.nmb` pide cada `DmgExtraHeavySlash`, `DmgBossHit`,
  `DmgBreakParts03` y cada `DieNormalSlash`; formato de `BossPartsParam`.
- **Locomoción del modo 1** (solo hay `Wep02_Idle`).
- **Almas del jefe** (`EP+0xd0` = 0; `BossParam` / `BossBattleParam` sin decodificar). En el mod el gigante es un
  castigo: si da almas es decisión del usuario.
- **EnemyParam 3096**: qué variante es.
- **Cápsula del cuerpo** (`c3096_h.hkx`): sin leer; el gigante de Minecraft mide 3.6 × 12 bloques.
- **Escala**: metros = bloques (0.906 del modelo); escalar o no es decisión del usuario.
- `held_arm`: el modelo vanilla no tiene esa parte; cómo dibujarla es decisión del mod.
- No probado en el juego.
