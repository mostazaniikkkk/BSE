# Animaciones del Hollow Soldier (c1020) en el zombi de Minecraft

Script: `tools/re/enemy_anims.py`. Salidas:
- `src/main/resources/assets/soulsmode/enemy_animations/c1020_<idTAE>.json` (una por animación, formato Bedrock / PAL);
- `src/main/resources/data/soulsmode/ds2/c1020_attacks.json` (índice de ataques y locomoción).

Uso: `python tools/re/enemy_anims.py src/main/resources/assets/soulsmode/enemy_animations src/main/resources/data/soulsmode/ds2/c1020_attacks.json`

## 1. Datos del juego
Extraídos de `GameDataEbl.bdt` con `D:\Reversing\DS2\tools\extract_bdt.py` (BHD ya descifrado,
`D:\Reversing\DS2\extracted\GameDataEbl.bhd`), en `D:\Reversing\DS2\extracted\c1020\`:

| Ruta del juego | Tamaño | Desempaquetado |
|---|---|---|
| `gamedata:/morpheme4/chr/c1020.anibnd.dcx` | 2 317 088 | `undcx` de `unpack_bnd.py` → `c1020.anibnd`; `unpack_bnd4_ex.py` → `anibnd\` (234 `.nsa` + `c1020.nmb`) |
| `gamedata:/model/chr/c1020.bnd` | 918 384 | `unpack_bnd4_any.py` → `model\` (`c1020.flv`, `c1020_s.flv`, ragdoll, tela) |
| `gamedata:/timeact/chr/c102000_pl.tae` | 74 016 | se lee directo con `tae.py` |

Los 234 `.nsa` tienen 57 canales y usan el único mapa canal → hueso de `c1020.nmb` (canal `i` → hueso `i + 1`).

## 2. Esqueleto de c1020
- `nsa.Rig` lee el AnimRigDef de `c0001.nmb` con desplazamientos fijos. Para c1020 se leen por los campos puntero del
  objeto rig (tipo 1 de `nmb.objects`; los punteros son desplazamientos desde el inicio del cuerpo menos 4):
  `+0x44` nº de huesos (58), `+0x54` padres, `+0x34` tabla de nombres (StringTable de Morpheme), `+0x3c` pose de
  enlace (transform buffer; `+0x80` / `+0x88` desde `+0x20`: arreglos de posiciones y cuaterniones, de 16 bytes).
- Comprobación: el mismo lector sobre `c0001.nmb` da exactamente lo de `nsa.Rig` (nombres, padres, pose y mapas), y la
  pose de c1020 coincide hueso por hueso con la tabla de huesos de `c1020.flv` (`flver_dummy.py`) con Y y Z cambiados.
- Huesos que usa el retarget (todos existen en c1020): `Pelvis`, `Spine1`, `Head`, `R/L_UpperArm`, `R/L_Forearm`,
  `R/L_Hand`, `R/L_Thigh`, `R/L_Calf`, `R/L_Foot`. Altura de cadera: `Root` z = 0.9803 m (c0001: 0.97).
- Las armas de c1020 cuelgan de `R_weapon` y `L_shield` (no `R_Weapon` / `L_Weapon` como en c0001). No se exportan
  rotaciones de objeto en la mano.

## 3. Retarget (mismo método que `ds2_anims.py`)
Geométrico, sin poses hechas a mano:
- Espacio de modelo DS2: X izquierda, Y atrás (mira a −Y), Z arriba. Minecraft: X izquierda, Y abajo, Z atrás.
  Vectores `(x, y, z) → (x, −z, y)`.
- `body`: rotación del pecho (`Spine1`) respecto de su pose de enlace. `position`: desplazamiento de la pelvis
  respecto de su pose de enlace, a escala de las piernas: `12 / 0.9803` px por metro.
- `head`: rotación de la cabeza respecto del pecho.
- Brazos y piernas, respecto del pecho: el miembro apunta del hombro a la muñeca (cadera al tobillo), porque en
  Minecraft son rígidos; el giro sobre su eje sale de la bisagra del codo / rodilla (eje Z local del antebrazo /
  pantorrilla), con el signo fijado por miembro para que la bisagra de la pose de enlace apunte a +X de Minecraft.
- 3 claves por muestra de DS2 (30 Hz → 90 claves/s), con el desenvuelto de Euler de `ds2_anims.unwrap`.
- No se aplica movimiento raíz (trayectoria de Morpheme): las animaciones se reproducen en el sitio.

## 4. Formato del JSON y convención de rotación
```json
{"format_version": "1.8.0", "animations": {"c1020_65131010": {
  "loop": false, "animation_length": 3.0,
  "bones": {
    "body":      {"rotation": {"0.0": [x, y, z], ...}, "position": {"0.0": [izq, arriba, atrás], ...}},
    "head":      {"rotation": {...}},
    "right_arm": {"rotation": {...}}, "left_arm":  {"rotation": {...}},
    "right_leg": {"rotation": {...}}, "left_leg":  {"rotation": {...}}}}}}
```
- Claves: tiempo en segundos (texto). Valores en **grados**. Interpolación lineal entre claves.
- Euler como `ModelPart.translateAndRotate`: `R = Rz(z) · Ry(y) · Rx(x)`.
- `head`, `right_arm`, `left_arm`, `right_leg`, `left_leg`: `xRot = rad(x)`, `yRot = rad(y)`, `zRot = rad(z)` del
  `ModelPart` de `HumanoidModel`, **absolutos** (la pose de reposo de Minecraft es 0): reemplazan lo que pone
  `setupAnim`, incluido el brazo levantado del zombi (`AnimationUtils.animateZombieArms`). Los pivotes no cambian.
- `body` no es el `ModelPart` `body`: es una rotación de **todo el modelo** (como el hueso `body` de PAL,
  `PlayerRendererMixin`) alrededor del pivote de cadera, punto de modelo `(0, 12, 0)` px (12 px sobre los pies), con
  la misma convención `Rz·Ry·Rx` en espacio de modelo. `position` = `(izquierda, arriba, atrás)` en px: en espacio de
  modelo (Y abajo) es `(+p0, −p1, +p2) / 16` bloques. Las rotaciones de cabeza y miembros ya son relativas al pecho,
  así que se aplican dentro de esa transformación; el `ModelPart` `body` queda en reposo.
- En PAL, el loader convierte grados a radianes sin cambiar signos (`MolangLoader.parseJson`), los miembros se copian
  tal cual al `ModelPart` (`RenderUtil.translatePartToBone`) y el `body` se aplica antes del volteo `scale(−1, −1, 1)`
  con `rotX`, `rotY` negados, que equivale a `Rz·Ry·Rx` en espacio de modelo (fuentes de PlayerAnimationLib 1.1.6).

## 5. Animaciones exportadas
Ataques de `ai102000` (EnemyParam 102000, modo 0 → `M` = 1; enemy_ai.md §7.3 y §8.2):

| Acción | Entrada | Animación | Duración (s) | Golpes 2200 (s) → ranura → EnemyDamageParam | Alcance / ángulo | EnemyAttackMoveParam |
|---|---|---|---|---|---|---|
| 13 | Second[3] | 65131010 | 3.000 | 0.867–1.033 → 0 → 110200310 | 4 / 180° | 40 |
| 14 | Second[4] | 65141010 | 2.667 | 0.900–1.033 → 0 → 110200410 | 2.8 / 40° | 34 |
| 15 | Second[5] | 65151010 | 4.333 | 1.400–1.533 → 0 → 110200510 | 4.1 / 20° | 40 |
| 16 | Second[6] | 65161010 | 4.667 | 0.867–1.033 y 1.600–1.767 → 0 → 110200610; 2.267–2.400 → 1 → 110200620 | 4 / 25° | 8 |
| 17 | Second[7] | 65171010 | 4.333 | 1.400–1.533 → 0 → 110200710 | 5.1 / 20° | 40 |
| 50 | Combo[0] | 65111010 / 020 / 030 | 2.167 / 2.167 / 2.667 | 0.533–0.700 en los tres → 0 / 1 / 2 → 110200110 / 120 / 130 | 2.9 / 20° | 40 |
| 51 | Combo[1] | 65121010 / 020 / 030 | 2.333 / 2.333 / 3.000 | 0.533–0.700, 0.533–0.700, 0.867–1.033 → 0 / 1 / 2 → 110200210 / 220 / 230 | 3.8 / 20° | 34 |

- Duración: `.nsa +0x28`. Golpes: evento TAE 2200 de `c102000_pl.tae`, `parámetro[0]` = ranura `k` (releído aquí con
  `tae.py`; coincide con enemy_ai.md §8.3).
- Filas EDP releídas de la regulation: `EnemyBehaviorSecondParam 102000` entrada `+0x00 + 4k` y
  `EnemyComboBehaviorParam 102000` entrada `+0x08 + 4k`; `EnemyAttackMoveParam` de `+0x18` / `+0x3c + 4·paso`.
- Alcance / ángulo: argumentos de `S2147483595` en la tabla de ataques `S2147483585` de `ai102000.esd`. Se lanza si
  `f7001()` (distancia al objetivo) < alcance y `f7002()` (ángulo absoluto al objetivo) < ángulo. La 14 también entra
  por `S2147483597(14)` y la 51 por `S2147483602(51, 5, 10, 10, 20)`.

Locomoción (bucles; sin eventos en el TAE):

| Id | `.nsa` | Duración (s) |
|---|---|---|
| 7001 | `a00_00_7001_Wep01_Idle` | 2.000 |
| 37001 | `a00_03_7001_Wep01_Walk` | 1.500 |
| 47001 | `a00_04_7001_Wep01_Jog` | 1.000 |
| 0 | `a00_00_0000_Idle` (no las `Idle_Head_A/B/C`) | 2.000 |
| 30010 | `a00_03_0010_Walk` | 1.500 |
| 40010 | `a00_04_0010_Jog` | 1.000 |

- `Wep01` es la variante de `M` = 1 (modo 0 de la 102000), por la misma correspondencia de nombres de enemy_ai.md §8.2.
  Se exportan también las genéricas. En los bucles la primera y la última clave coinciden (diferencia < 1°).
- DS2 no tiene un "run" aparte para c1020: el más rápido es `Jog` (no hay `Dash` en el anibnd).

## 6. Comprobaciones
- **Pose de enlace**: `body`, `head` = 0; piernas `xRot` 3.7°; brazos `zRot` ±43.4° (`xRot` 0.7°, `yRot` ∓3.9°).
  La pose de enlace de c1020 tiene los brazos abiertos 43° (pose A), así que no es la de reposo de Minecraft (brazos
  colgando); el resultado es geométricamente correcto: hacia afuera en los dos brazos. La pose de reposo del idle
  `Wep01_Idle` (t = 0) da brazos a ~±18–20° de `zRot` y cuerpo con `yRot` 14.5°.
- **Golpe contra barrido** (velocidad máxima de la punta del arma en el espacio de DS2, hueso `R_weapon`, muestreo
  a 30 Hz; para la 14 `L_shield`, la dummy 130 de su golpe):

| Animación | Ventana 2200 | Pico de velocidad | Pico de giro de `right_arm` |
|---|---|---|---|
| 65131010 | 0.867–1.033 | 24.6 m/s a 0.967 | 1586°/s a 0.967 |
| 65141010 | 0.900–1.033 | `L_shield` 18.7 m/s a 1.000 | `left_arm` cambia de (−3, −40, −101) a (−57, −41, −69) entre 0.933 y 1.000 |
| 65151010 | 1.400–1.533 | 27.4 m/s a 1.500 | 1166°/s a 1.467 |
| 65161010 | 0.867–1.033 (+ 2 más) | 20.3 m/s a 1.000 | 1846°/s a 1.000 |
| 65171010 | 1.400–1.533 | 13.9 m/s a 1.500 | 881°/s a 1.500 |
| 65111010 / 020 / 030 | 0.533–0.700 | 12.4 / 13.0 / 12.2 m/s a 0.667 / 0.600 / 0.633 | dentro de la ventana |
| 65121010 / 020 / 030 | 0.533–0.700 / 0.533–0.700 / 0.867–1.033 | 13.7 / 13.7 / 8.6 m/s a 0.633 / 0.600 / 0.933 | dentro de la ventana |

  En todas, el pico del barrido cae dentro de la ventana de golpe del TAE.

## Sin confirmar / pendiente
- **Movimiento raíz**: no se decodifica la trayectoria de Morpheme, así que el avance de los ataques (estocadas, pasos)
  no está en los JSON. Hace falta decodificar la fuente de trayectoria del `.nsa`.
- **Qué estado de la red reproduce cada animación**: ataques y locomoción `Wep01` salen de la correspondencia de
  nombres de enemy_ai.md §8.2; el código Morpheme que la elige no está trazado.
- **Paso de un paso de combo al siguiente** (enemy_ai.md §8.3): sin confirmar; el índice solo da cada paso por separado.
- **Brazos rígidos**: el codo y la rodilla de DS2 no se ven (igual que en el jugador); el arma en la mano del zombi no
  sigue `R_weapon` (no se exportan rotaciones de objeto).
- No probado en el juego: hace falta el código Java que aplique estos JSON al `HumanoidModel` del zombi.
