# Animaciones, TAE y game feel

## Archivos del jugador (en `GameDataEbl.bdt`, se extraen con `extract_bdt.py`)
- **TAE**: `gamedata:/TimeAct/chr/c000100_pl.tae` (id 100 = el jugador, igual que ChrParam). 1137 animaciones, 5493 eventos.
  Parser: `D:\Reversing\DS2\tools\tae.py`.
- **Morpheme**: `gamedata:/morpheme4/chr/c0001.anibnd.dcx` → BND4 (entradas de 0x24, nombres UTF-16) con 897 `.nsa`
  (animaciones) y `c0001.nmb` (red de estados). Se desempaqueta con `unpack_bnd4_ex.py`.

## Formato TAE (64 bits, versión 0x1000C) — verificado contra los datos
- Cabecera: `+0x50` u32 id de personaje (100), `+0x54` u32 número de animaciones, `+0x58` u64 tabla.
- Tabla: entradas `(u64 id, u64 offset)`.
- Animación (0x30 bytes): `u64 eventos, u64 grupos, u64 tiempos, u64 archivo, i32 nEventos, i32 nGrupos, i32 nTiempos`.
- Evento (0x18 bytes): `u64 inicio, u64 fin, u64 datos`; los tiempos son f32 en segundos.
  Datos: `i32 tipo, i32 relleno, u64 parámetros`.
- **Validación cruzada con el código**: el tipo `101100` (`0x18aec`) aparece 636 veces y es exactamente el que
  `ChrMorphemeTimeActTrackStaminaCtrl` usa para bloquear la regen de estamina.

## Nombre de cada animación
Los `.nsa` del anibnd se llaman `aGG_SS_NNNN_<Nombre>_<hash>.nsa`, y el id de TAE es `GG·1000000 + SS·10000 + NNNN`.
Ejemplos:

| Id TAE | Nombre (del archivo) |
|---|---|
| 30010 | Walk |
| 40010 | Jog |
| 50010 | Dash |
| 110011 / 110031 | StepBackRightShort / Long |
| 120020 / 120021 | DodgeRollingShort / DodgeRollingLong |
| 120030 / 120031 | DodgeRollingShort / DodgeRollingLongT (otra variante) |
| 120040 | DodgeRollingFailed |
| 130020 / 130021 | JumpTakeoff / StandJumpTakeoff |
| 16xxxx | Dmg* (reacciones a daño) |

`.nsa` `+0x28` f32 parece ser la duración: Idle 2.0, Dash 0.6 (ciclo), StepBack 1.1667, DodgeRollingShort 1.5333.
Sale de la estructura, sin confirmar en el código.

## Eventos de invulnerabilidad (manejados en `ChrMorphemeTimeActTrackDamageActionCtrl`, `FUN_140326240`)
Cada evento suma 1 a un contador de `chr.b8` mientras está activo:

| Tipo | Contador | Quién lo lee |
|---|---|---|
| 110500 (`0x1afa4`) | `+0x5e8` (o `+0x5ec`, o `+0x5f0` y `+0x608`, según el parámetro) | `FUN_140139380`: **el golpe solo entra si `+0x5e8 == 0` y `+0x5ec == 0`** |
| 110100 (`0x1ae14`) | `+0x5f0`, `+0x608` | `FUN_140139b60`: el golpe solo entra si `+0x608 == 0` |
| 110210 (`0x1ae82`) | `+0x594` | `FUN_14013b0a0` (sin analizar) |
| 110400 (`0x1af40`) | `+0x5f4` | `FUN_14013a620` (sin analizar) |
| 111100 / 111200 / 111300 / 111400 / 111500 | `+0x60c` / `+0x610` / `+0x614` / `+0x618` / `+0x5c0` | sin analizar |

**Corrección (agility.md §2.1):** el daño solo lo bloquea `+0x608` (`PlayerDamageActionCtrl vt+0xe0` =
`FUN_140139b60`). `+0x5e8` (110500) solo anula el camino de golpe aguantado por la estabilidad de `FUN_140139380`.
Los i-frames de las esquivas son 110100 más una fracción del 110300, que depende de la AGL.

**Ventanas medidas en el TAE** (110500 no es i-frame, ver la corrección):
- DodgeRollingShort/Long (120020/21/30/31): `110500` de **0.100 a 0.667 s**; además `110100` de −0.167 a 0.133 s.
- StepBack (110011/31): `110500` de **0.167 a 0.333 s**.

Otros eventos del roll aún sin significado: 2202, 2106, 2402, 100, 300 (300 aparece también en Walk/Jog/Dash con
parámetros 257/258/259; probablemente pasos), 310000, 260005, 110200 (0 a 1.0 s), 110300 (0.1 a 0.567 s), 300100.

## Roll vs paso (backstep) — `ChrDodgeCtrl` y el controlador Morpheme `FUN_14035f640` / `FUN_14035fbc0`
Tabla de peticiones: `Dodge`, `DodgeStaminaEmpty`, `DodgeFailed`, `Step`, `StepStaminaEmpty`, `StepFailed`.
- `chr.b8+4 == 3` → rama **Dodge**, con variante en `chr.b8+0x9f` (1 normal, 2 StaminaEmpty, 3 Failed).
  Esa variante la escribe `FUN_140328b10`, que usa el factor `ChrCommonParam+0x2c` (1.0): **coste de rodar = 300**.
- `chr.b8+4 == 2` → rama **Step**, con variante en `chr.b8+0xa0`. La escribe `FUN_140328c20`, con factor `+0x28` (0.7):
  **coste del paso = 210**.
- Parámetros Morpheme del dodge: `DodgeDistance`, `DodgeLoadWeight`, `DodgeDirection`, `DodgeSpeed`, `StepSpeed`
  (`FUN_14035f440`). Sus valores de origen no están localizados.

## Distancia del roll (en curso)
`FUN_14035f750` actualiza los parámetros Morpheme del dodge (nombres en `FUN_14035f440`, índices 0–7:
DodgeDistance, DodgeLoadWeight, DodgeDirection, DodgeSpeed, StepDistance, StepLoadWeight, StepDirection, StepSpeed):
- **DodgeLoadWeight** = curva de carga `ChrEquipLoadParam` fila `0x18` (0, 30, 70, 100, 100 según la carga).
  Es el peso de mezcla entre animaciones (roll ligero / pesado).
- **DodgeDistance** usa la fila `0x19` (penalización 0, 10, 28, 40, 50 %) y `FUN_14031c690`, cuando no hay
  dirección de entrada y existe `ChrMoveParam`. La decompilación pierde cómo se combina el resultado; falta leer el ensamblador.
- **DodgeSpeed**: solo en la variante StaminaEmpty (`chr.b8+0x9f == 2`), fila `0x1a` (1.0, 1.0, 1.0, 0.9, 0.95).
- **La distancia base sale del movimiento raíz de la animación**, guardado comprimido en el `.nsa` (formato NMP de
  NaturalMotion). Cabecera vista: `+0x28` duración (1.5333 en el roll), `+0x2c` f32 (20.2, posible frecuencia de muestreo),
  `+0x30` número de canales (58). La descompresión no está hecha.

## Elección entre roll y paso atrás — confirmada (`FUN_140367ea0`)
- `FUN_14038e900`: botón de esquiva (`entrada+0x130` bit 5) **con** dirección (|stick| > 0) → petición de **roll** (Morpheme 5).
- `FUN_14038e9a0`: botón **sin** dirección → petición de **paso atrás** (9).
- **Si la estamina está agotada (`FUN_1403337c0`), no se envía ninguna de las dos peticiones.**
- Variante por carga, `FUN_14038e770(ratio de carga)`:
  - 0 hasta `ChrEquipLoadParam` fila 1 `[2]` (70 %);
  - 1 hasta `[4]` (120 %);
  - 2 por encima.

  Roll: peticiones 8 (carga 1) y 6 (carga 2). Paso atrás: 0xb y 0xa. La 7 depende de `chr.b8+0x52c`.
- Las peticiones son bits en `ctrl+0x18`+2 (`FUN_140366020`).

**Aplicado**: sin dirección → paso atrás (TAE 110011): i-frames 0.167–0.333 s, 35 frames, re-esquiva a los 8 frames
(0.267 s). `Dodge` es ahora un enum ROLL / BACKSTEP.

## Sprint
La animación Dash (50010) **no tiene eventos de estamina en el TAE** (solo 300). El drenaje sale de un flag
(`chr.c0+0x2a`) que no se escribe directamente; probablemente viene de la red Morpheme (`c0001.nmb`), que tiene pistas de
eventos con nombre (`CancelControl_Move`, `AttackDuration`, `GuardDuration`, …). **Sin resolver.**

## Red Morpheme `c0001.nmb` — descifrada (parser `D:\Reversing\DS2	ools
mb.py`)
- **Objetos**: cabecera de 0x30 bytes (`u32 0x18, u32 10, u32 tipo, guid[16], u32 id, u64 tamaño, u32 alineación, u32`),
  luego el cuerpo.
  - Tipos 3 y 4: pistas de eventos (6273).
  - Tipo 10: NetworkDef (6.5 MB).
  - Tipo 13: tabla de nombres. Son 4 tablas de cadenas de 994 entradas: `.nsa`, formato, `.xmd` de origen y take.
    El índice de la tabla coincide con el id de la entrada en el anibnd.
- **Pista de eventos**: `u32 n, u32 tipo, u64 offNombre, u32 userData de pista, u32 id de nombre, u64 offEventos`.
  Cada evento es `(f32 inicio, f32 duración, u32 userData)`, con tiempos **normalizados** a la duración de la animación.
- **NetworkDef**: atributos con cabecera `ffff TT00 cdcdcdcd`.
  - El tipo `0x15` es la fuente de animación: en `+0x6c` está el índice de animación.
  - Lo sigue un `0x17`, el conjunto de pistas. Base = cabecera − 8; desde `base+0x10` hay 3 grupos (discreto, curva,
    duración) de `(u32 n, pad, u64 offIds, u64 offTamaños)`. Los ids son los 4 primeros bytes del GUID de cada pista.
  - Son 6020 nodos de animación, 5051 con pistas.
- La pista `TimeAct` lleva como userData el **id de TAE** (por ejemplo 120020), lo que enlaza oficialmente cada nodo con su TAE.

### Del evento al estado del personaje (`ChrEventTrackActionStateControl`, `FUN_14036a6e0`)
Cada userData se convierte en un índice de flag `i`. `FUN_14036afc0` suma o resta 1 al contador `chr.b8+0x4d0+i·4`
mientras el evento está activo. Los eventos de TAE usan el mismo array: `+0x558` (bloqueo de regen) es el flag `0x22`
y `+0x5e8` (i-frames) es el flag `0x46`.

| userData | Pista | Flag `i` | Contador |
|---|---|---|---|
| 30 | CancelControl_Avoid | 0x23 | `+0x55c` |
| 31 | CancelControl_Move | 0x24 | `+0x560` |
| 33 / 34 / 35 / 36 | CancelControl_Attack1L / 1H / 2L / 2H | 0x26 / 0x27 / 0x28 / 0x29 | `+0x568..+0x574` |
| 37 | InputControl | 0x02 | `+0x4d8` |
| 38 | CancelControl_Guard | 0x2a | `+0x578` |
| 39 | CancelControl_AttackAll | 0x26 a 0x29 | — |
| 42 | InputControl_Attack | 0x03 | — |
| 43 | CancelControl_Itemuse | 0x32 | — |

**Significado, confirmado para Avoid**: `FUN_14038e850` (¿puede esquivar?) solo deja si `+0x55c == 0`. Es decir,
**mientras `CancelControl_X` está activo, X está bloqueado**. Los demás comparten el mecanismo; cada uno queda por
confirmar con su lector.

### Ventanas (fracción de la animación; ×46 frames a 30 fps en el roll)
| Animación | Avoid (otro roll) | Ataque 1L / 2L | Ataque 1H / 2H | Guardia | InputControl |
|---|---|---|---|---|---|
| DodgeRollingShort/Long | 0–0.5217 (**0.8 s**) | 0–0.3261 (0.5 s) | 0–0.587 (0.9 s) | 0–0.4783 (0.733 s) | 0.3261–1 |
| StepBackRightShort | 0–0.2286 | AttackAll 0–0.2857 | — | — | 0.0857–1 |

Todas las ventanas del roll caen en frames enteros de 46: eso respalda la duración de 1.5333 s.

**Aplicado**: `Dodge.REROLL_LOCK = 0.8 s`. Ya no se espera al final de la animación para volver a rodar.

## Locomoción (implementada, `client/anim/Locomotion`)
Datos de DS2 (`c0001.nmb`, pistas `Leftsteps` / `Rightsteps` = ventana de apoyo de cada pie; duración = `.nsa +0x28`):

| Animación | Ciclo | Pie izq. apoya | Pie der. apoya |
|---|---|---|---|
| a00_03_0010 Walk | 1.0667 s (32 frames) | 0 | 0.503 |
| a00_04_0010 Jog | 0.7667 s (23 frames) | 0 | 0.478 |
| a00_05_0010 Dash | 0.6 s (18 frames) | 0 | 0.444 |
| a00_00_0000 Idle | 2.0 s | — | — |

- **Animaciones**: `ds2_idle/walk/jog/dash.json` tienen esos ciclos, y cada pierna está adelantada justo cuando su pie apoya.
  Las poses (amplitudes, flexión de rodilla y codo, inclinación del torso) son nuestras.
- **Capa**: PAL con prioridad 5, por debajo del roll (1500).
- **Correspondencia** (decisión de diseño): moverse = Jog (la marcha de DS2 con el stick a fondo), sprint = Dash, agacharse = Walk.
- **Limitaciones**:
  - Solo el jugador local, porque el cliente no sabe si los demás están en Modo Souls.
  - El ciclo no se escala con la velocidad real; puede haber deslizamiento de pies hasta tener las velocidades de `ChrMoveParam`.

## Espejo de Morpheme (postura izquierda)
Scripts y volcados: `D:\Reversing\DS2\out\mirror\` (`strs.txt`, `reg1.txt`, `reg2.txt`, `task.c`, `mt.c`, `mt2.c`).
La comprobación numérica se hizo con un script temporal sobre `tools/re/nsa.py`; no se tocó código del mod.

### Código del runtime
- Registro de tareas `FUN_1405f7010`: `TaskMirrorTransforms` = **`FUN_140611220`** (id de tarea 0x90). Las demás
  `TaskMirror*` (0x91–0x98) son trayectoria, eventos, sync y tiempo.
- Cola del nodo (`FUN_140606e10`): `nodeMirrorQueueTransformBuff` = **`FUN_14064dfd0`**. Pide 4 parámetros:
  - semántica 0x10 del nodo 0 (la red) = el rig;
  - semántica 0x20 del nodo 0 = el **mapa de espejo** (atributo tipo 0x42, abajo);
  - semántica 0x24 del propio nodo espejo = lista de huesos que **no** se espejan;
  - el búfer de transformadas del hijo (semántica 6).
- `FUN_140611220` llama a **`FUN_140613f00`(rig, mapa, lista, entrada, salida)**, que hace 4 pasos:
  1. `FUN_140666bf0`: de local a "espacio personaje". Para cada hueso con padre **> 0**: `Q = Q_padre·q`,
     `P = P_padre + rot(Q_padre, p)` (`FUN_140604110` = producto de cuaterniones, `FUN_140604330` = rotar vector,
     `FUN_140604250` = normalizar). Los huesos 0 (`CharacterWorldSpaceTM`) y 1 (`Character`, hijo de 0) se quedan en local:
     todo queda en el marco del hueso 0. Un canal que falta se pone en identidad y hereda la del padre.
  2. `FUN_1406140a0`(nHuesos, eje = `mapa+0x10`, mapa, salida):
     - **reflejo** de todos los huesos según el eje. Máscara de signo `DAT_1410ac6c0` = 0x80000000 (leída del exe). El
       canal 0 del búfer es la posición y el 1 el cuaternión (`DAT_141105990` = 0, `DAT_141105994` = 1):
       - eje 0: `P.x = −P.x`, `Q = (Qx, −Qy, −Qz, Qw)`;
       - eje 1: `P.y` y `Q = (−Qx, Qy, −Qz, Qw)`; eje 2: `P.z` y `Q = (−Qx, −Qy, Qz, Qw)`;
     - **pares** (`mapa+0x14` = número, `mapa+0x18` = entradas de 0x30 bytes: `+0x00` offset0, `+0x10` offset1,
       `+0x20` u32 hueso L, `+0x24` u32 hueso R). Ya reflejados:
       `Q'[R] = Q[L]·offset0`, `P'[R] = P[L]`; `Q'[L] = Q[R]·offset1`, `P'[L] = P[R]`. También intercambia los bits de
       "canal presente";
     - **invariantes** (`mapa+0x40` = número, `+0x48` = cuaterniones de 16 bytes, `+0x50` = índices u32):
       `Q'[i] = Q[i]·offset_i`. La posición solo se refleja.
  3. `FUN_140612c40`: vuelta a local, del último hueso al primero, solo si el padre es > 0:
     `p = rot(conj(Q_padre), P − P_padre)`, `q = conj(Q_padre)·Q`.
  4. Para cada hueso de la lista de semántica 0x24 que tenga canal en la entrada, se copia la transformada **sin espejo**.

### Datos de `c0001.nmb`
- **El mapa** es el atributo **tipo 0x42** del nodo 0 (red, tipo 9, en `0xb6940`). Hay dos copias, en `0xb6c30` y
  `0xb7a70` (base = cabecera − 8), una por cada conjunto de atributos del nodo 0 (0x14, 0x2b, 0x06, 0x42). Los primeros
  0x690 bytes son **idénticos**. Por qué hay dos copias: sin confirmar (el índice sale de `numSemánticas × animSet` en
  `FUN_14064dfd0`).
  - `+0x10` eje = **0 (X)**. En el rig, X es el eje izquierda-derecha del personaje.
  - `+0x14` = **25 pares**, entradas en `+0x60`.
  - `+0x40` = **9 invariantes**: cuaterniones en `+0x510`, índices en `+0x5a0` (= `0xb71d0`).
  - `+0x30` = 25 pares de userData de eventos, en `+0x5c4` (= `0xb71f4`): 10↔11, 9810↔9811, 280↔286 … 285↔291,
    803↔804, 813↔814, 823↔824, 830↔840, 831↔841, 832↔842, 833↔844, 834↔843, 2010↔2020, 891↔893, 892↔894, 52↔53,
    75↔76, 58↔59, 62↔63, 426↔427, 45↔46. Es la tabla de espejo de eventos que cita weapons.md. El campo `+0x20` (número
    0, puntero `+0x68c`) está vacío; significado sin confirmar.
- **Pares** (L, R) y offsets. Los dos offsets de cada par son iguales salvo el signo, que da la misma rotación:

  | Pares | offset (x, y, z, w) | Rotación |
  |---|---|---|
  | Clavicle, UpperArm, Forearm, Hand, Finger1x, Finger2x, Finger3x, Finger4x (con sus falanges) | (0, −1, 0, 0) | 180° en Y |
  | Finger0, Finger01, Finger02 | (0, 1, 0, 0) | 180° en Y |
  | Weapon | (0, 0, 1, 0) | 180° en Z |
  | Thigh, Calf, Foot, Toe0 | (0, 1, 0.00001, 0) | 180° en Y |
  | Toe0Nub | (0, 0, 0, 1) | identidad |

- **Invariantes**:
  - `CharacterWorldSpaceTM`, `Character` y `Master`: identidad;
  - `Root`: (0, 0, 1, 0), 180° en Z;
  - `Pelvis`, `Spine`, `Spine1`, `Neck` y `Head`: (0, −1, 0, 0), 180° en Y.

  25 × 2 + 9 = 59: **todos los huesos** del rig están en el mapa.
- **Comprobación**: aplicar los pasos 1–3 a la pose de reposo del rig (`nsa.Rig.bind_*`) devuelve la misma pose de
  reposo, con un error máximo de 4.7e-4 (asimetría del propio rig). Eso confirma el formato y el orden.
- **El nodo espejo (tipo 0x87)**: 423 nodos en total. En la postura: `0x3e9`, `0x3ea`, `0x3eb`, `0x3ec`, `0x3ed`, `0x3ee`, `0x3ef`, `0x3f0`,
  `0x3f3`, `0x3f6`, `0x3f9` y `0x3fe`.
  - Un hijo (la animación) y 4 huecos de atributo; uno está vacío. En el archivo: tabla en nodo+0x94, entradas de 0x18
    bytes.
  - Atributos: dos **tipo 0x08** (array de u32) con **0 elementos** cada uno, y un **tipo 0x02** (booleano).
  - Como los dos arrays están vacíos, la lista de huesos sin espejo del paso 4 está vacía, sea cual sea de los dos: **se
    espejan los 59 huesos**.
  - El booleano vale 0 en 415 nodos (entre ellos todos los de postura) y 1 en 8 (`0x199`, `0x1d5`, `0x1e3`, `0x336`,
    `0x1451`, `0x146b`, `0x1492`, `0x14ac`). **Significado sin confirmar** (no se buscó qué tarea lo lee). No hay
    ningún otro ajuste por nodo: el mapa de eventos es el global del atributo 0x42.

### Fórmula por hueso (pose local)
`S(x, y, z) = (−x, y, z)`. `refl(x, y, z, w) = (x, −y, −z, w)`. `m(b)` = la pareja de `b` (o `b` si es invariante).
`O_b` = el offset de `b` (de la tabla de pares, el que escribe sobre `b`, o el invariante). `O_padre = identidad` si el
padre es ≤ 0.

```
p'_b = rot(conj(O_padre(b)), S(p_m(b)))
q'_b = conj(O_padre(b)) · refl(q_m(b)) · O_b
```

Sale de los pasos 1–3 porque `S·R·S = refl(R)` y porque el rig cumple `padre(m(b)) = m(padre(b))`. Verificado contra la
versión de 3 pasos en las posturas a29/a21/a20/a25/a36/a45_43_0010: error ≤ 1.2e-5. Además, la `R_Hand` original
(−0.111, −0.196, 1.01) pasa a ser la `L_Hand` espejada (0.111, −0.196, 1.01).

Implementación de referencia para `tools/re/nsa.py`. **No está aplicada**; offsets leídos de `0xb6c30`:

```python
def mirror(rig, lp, lq, B=0xb6c30):
    d = open(NMB, 'rb').read(); ident = (0.0, 0.0, 0.0, 1.0)
    n, = struct.unpack_from('<I', d, B + 0x14)
    m = list(range(len(rig.names))); off = [ident] * len(rig.names)
    for k in range(n):
        e = B + 0x60 + k * 0x30
        l, r = struct.unpack_from('<II', d, e + 0x20)
        m[l], m[r] = r, l
        off[r] = struct.unpack_from('<4f', d, e); off[l] = struct.unpack_from('<4f', d, e + 16)
    ninv, = struct.unpack_from('<I', d, B + 0x40)
    for k in range(ninv):
        off[struct.unpack_from('<I', d, B + 0x5a0 + 4 * k)[0]] = struct.unpack_from('<4f', d, B + 0x510 + 16 * k)
    op, oq = [], []
    for b, p in enumerate(rig.parents):
        s = m[b]; c = qconj(off[p]) if p > 0 else ident
        x, y, z = lp[s]; qx, qy, qz, qw = lq[s]
        op.append(qrot(c, (-x, y, z)))
        oq.append(qmul(qmul(c, (qx, -qy, -qz, qw)), off[b]))
    return op, oq
```

- El espejo deja **rotaciones propias** en cada hueso: el modelo del arma no se refleja, solo cambia de mano y de
  orientación (`L_Weapon` = reflejo de `R_Weapon` · 180° en Z).
- En el juego, el espacio del reflejo es el marco del hueso 0. En las posturas, `CharacterWorldSpaceTM`, `Character` y
  `Master` son identidad, así que coincide con el espacio modelo de `nsa.world`.

### Postura a dos manos con la izquierda
- `TwoHandStance` = **−1** cuando se agarra a dos manos el arma izquierda (estado 2). `AttackStance_LR` = `WTP+0x10`
  del arma izquierda (`FUN_14035db00`, `DAT_1410ac6a4` = −1.0; ver weapons.md, "Postura"). El SM 0x3bc pasa al
  estado **0x3fb**.
- 0x3fb es un interruptor sobre `AttackStance_LR` (valores 364311 / 404310) con dos subinterruptores: 0x3fc (204310,
  214310, 254310, **294311**, 364310, 364311) y 0x3fa (404310 … 464310).
- Cada rama es **FeatherBlend2(locomoción 0x500, espejo 0x87(postura `_R`))**: solo se espeja la postura; la locomoción
  entra sin espejo.

| Arma (WTP `+0x10`) | Rama | Nodo espejo | Clip que se espeja |
|---|---|---|---|
| Longsword (294310) | 0x3fd (valor 294311) | 0x3fe | `a29_43_0010 StraightSwordStance2Hand_R` |
| Battle Axe, Mace, Club (214310) | 0x3df | 0x3ea | `a21_43_0010 KnifeStance2Hand_R` |
| Spear (254310) | 0x3e2 | 0x3eb | `a25_43_0010 SpearStance2Hand_R` |
| Pickaxe (364310) | 0x3d9 | 0x3ec | `a36_43_0010 LargeAxeStance2Hand_R` (a dos manos no hay `_L`) |
| Drangleic Shield (454310) | 0x3e5 (por 0x3fa) | 0x3f0 | `a45_43_0010 MiddleShieldStance2Hand_R` |
| Fists (204310) | 0x3da | 0x3e9 | `a20_43_0010 FistStance2Hand_R` |

- La espada recta: la rama espejada tiene el valor 294311, pero su clip es el mismo `a29_43_0010`. Que 294310 elija esa
  rama depende de la regla del interruptor ("valor más cercano"), **sin confirmar en código** (igual que en weapons.md).
- **Alfas**: las de cada FeatherBlend2 espejado son **idénticas** a las de la rama sin espejo (0x3be / 0x3d5):
  - en 11 de 12 ramas son las mismas;
  - todas son simétricas L↔R, así que da igual espejarlas o no;
  - excepción, sin arma del mod: LargeShield (0x3cf frente a 0x3f1), UpperArm 0.9 sin espejo y 0.8 con espejo.
- No se espejan las alfas: Morpheme aplica el espejo al hijo, antes de la mezcla.

## Condición 0x262 y pistas de duración en el runtime de Morpheme (2026-11-01)
Salidas: `D:\Reversing\DS2\out\cast_c262.c` (la condición) y `D:\Reversing\DS2\out\c262\*.c` (`ev1`..`ev3`, `init`,
`sync`, `tr1`..`tr3`, `mec`, `sc`). Tablas de colas y atributos con los scripts de
`C:\Users\Laptop\AppData\Local\Temp\claude\d--Proyectos-Minecraft-Dark-Souls-Mod\557f977c-a61e-4da3-a214-bcd2510dd3b2\scratchpad\c262\`
(`raw.py`, `all262.py`, `node.py`, `trk.py`, `up.py`, `loop.py`) y `scratchpad\hb\qt.py`.

### Valor muestreado de un evento de duración = fracción que le queda
- Inicialización (`TaskInitEventTrackDurationSetFromSource`, tarea 0x12, `FUN_1406195e0` → `FUN_1406016c0`): cada
  evento (inicio, largo en fracción del clip) se pasa a la región del clip (atributo 0x15 `+0x80` inicio, `+0x84`
  fin) y al espacio de eventos sync. Si el evento se sale por el final y el clip no hace bucle, **se corta en el final
  de la región** (con bucle, se envuelve).
- Muestreo (`FUN_140602120` → **`FUN_140601ce0`**), con la posición actual y la anterior de `FRACTION_POS`
  (`+0x10` / `+0x14`):
  - el evento está activo si la posición cae a menos de medio largo de su punto medio;
  - escribe en el búfer de curvas un `SampledCurveEvent` de 0x14 bytes: `+0x04` userData de la pista, `+0x08`
    userData del evento, **`+0x0c` valor = (fin − pos) / (fin − inicio)**, con las tres posiciones pasadas a fracción
    real del clip por la pista sync (`FUN_1405fcf00`, lineal por tramos), y `+0x10` peso = 1.0;
  - si el evento terminó entre la posición anterior y la actual, emite **valor 0** ese frame.
  - El valor va de 1.0 al empezar el evento a 0 al acabar. Lo confirma por otro lado el TAE: tiempo de TAE =
    (1 − valor del evento `TimeAct`) × duración del TAE (equipload.md).

### Condición 0x262 (`FUN_14065ad50`; creación `FUN_14065ac20`; dependencias `FUN_14065acc0`)
- Datos de la condición: `+0x48` u16 = nodo del que se lee (el estado de origen), `+0x4c` u16 = semántica **0xd**
  (`SAMPLED_EVENTS_BUFFER`), **`+0x54` userData de la pista**, **`+0x58` userData del evento**, **`+0x5c` umbral**.
- Lee el búfer del **frame anterior** (frame − 1) del nodo; si el nodo es una máquina de estados sin el atributo,
  baja al estado activo (`FUN_140636970`). Busca el evento con `FUN_1405fbd10` (pista y evento iguales).
- Estado propio: `+0x04` valor anterior, iniciado a −1.0 (`DAT_1410ac6a4`); si el evento no está, vuelve a −1.0.
- **Se cumple el frame en que el valor cruza el umbral hacia abajo**: anterior ≠ −1, anterior > umbral y actual ≤
  umbral.
- **Lectura: la transición empieza cuando queda una fracción ≤ umbral del evento**, es decir en
  `inicio + (1 − umbral) × largo` del evento. Umbral 0 = al terminar el evento (frame del valor 0).
- Como lee el frame anterior, empieza en la actualización siguiente a la que pasa ese punto (un frame de retraso).
- Todas las instancias de `c0001.nmb` (`all262.py`), explicadas por esta definición:

| Pista / evento | Umbral | Dónde | Punto de la transición |
|---|---|---|---|
| 500 / 520 `GuardDuration` | 0.1 | guardia: 0x2141, 0x21a2, 0x222e, 0x22c6 | evento 0.5–1.0 → **0.95 del clip** |
| 80 / 42 `ChangeStanceTwoWeaponPrepare` | 0.1 | powerstance: 0x469, 0x49f, 0x23a3, 0x23b6 | evento 0–1 → **0.9 del clip** |
| 100 / 120 `AttackDuration` | 0.2 | arco 0x1138 / 0x1163, ballestas 0x1414..0x1677 | evento 0–1 → 0.8 |
| 100 / 120 `AttackDuration` | 0.4 | hechizos 0x1210, 0x127d, 0x12ee..0x13a0 | evento 0–1 → 0.6 |
| 80 / 62 `WeaponChangeCtrl` | 0.1 | cambio de agarre 0x4dd | 90 % del evento |
| otras (80/50 0.5, 80/63 0.1, 80/70 0, 80/80 0.2, 80/95 0, 80/97 0.25, 100/120 0.5, 900/910 0.05) | | fuera de estos temas | mismo cálculo |

### La transición (`nodeTransit`, tabla de colas 7; atributo 0x3b de la transición)
- `+0x10` duración, `+0x24` modo de mezcla, `+0x28` congelar origen, `+0x29` congelar destino. En las de guardia y
  powerstance: modo 0, sin congelar. `+0x14` (3 en guardia, 0 en powerstance) = **modo de inicio del destino**
  (`FUN_14064fa60`, primer frame: 0 fijo en `+0x18`; 1 `+0x1c` + fracción sync del origen; 2 `+0x1c`; 3 índice +
  fracción sync del origen; 4 `+0x1c` + índice), escrito como `PLAYBACK_POS_INIT` (0x3b) en el destino; los clips
  solo lo leen de su propio bin, así que no llega a los clips de guardia. `FUN_140608700` registra **tipos de
  condición** (0x262 `FUN_14065ad50`, 0x26a `FUN_14065b030`, 0x273 `FUN_140659e80`), no de nodo.
- Tiempo (`TaskTransitUpdateTimePos`, tarea 0xac, `FUN_1406334f0`):
  - **origen y destino siguen recibiendo Δt**: el clip de origen sigue avanzando durante la mezcla (solo se para con
    `+0x28` ≠ 0);
  - tiempo transcurrido += Δt (el primer frame vale Δt);
  - **peso del destino = (1 − cos(π · t / duración)) / 2**: `0.5 · (1 + sin(π (f − 0.5)))` con la tabla de cuadrantes
    0x14110d078 / 0x14110d090 y la serie de seno 1/6, 1/120, 1/5040 (0x14110d0d4 / 0x14110d0b0 / 0x14110d0a4). **No
    es lineal**: entrada y salida suaves. Con f ≥ 1, peso 1 y transición terminada.
- Pose (`nodeTransitQueueTransformBuffs`, `FUN_1406517a0` → `FUN_140650f90`): modo 0 → tarea 0x1b, la misma
  interpolación del Blend2 (posición lineal, rotación fastSlerp de NMP; wall_bounce.md).
- Eventos durante la mezcla: `nodeTransitBlend2QueueSampledEventsBuffers` (`FUN_1406507f0`) junta los de los dos
  estados (tarea 0x58); el conjunto de duración (semántica 0xe) es el del hijo 0 (origen).

### Quién muestrea las pistas de duración y cómo se juntan en las mezclas
- La consulta es `findGeneratingNodeForSemantic` del padre con la semántica 1 (la posición sync). Índice de la función
  en el nodo: `+0x60` (lista de `FUN_1405f6440` en `FUN_140606e10`, base 1: 1 filtro, 3 SM, 6 animación, 7 Blend2,
  9 Blend2SyncEvents, 12 transición).
  - Si **ningún padre** genera la semántica 1, el nodo muestrea sus pistas de duración: animación con tarea 0x11
    (`TaskSampleEventsFromSourceTracksIncDurationEvents`, `FUN_140619b00`), mezclas sync con tarea 0x59
    (`TaskCombine2SampledEventsBuffersAndSampleDurationEvents`, `FUN_140618700`).
  - Si un padre la genera (una mezcla sync), el nodo no muestrea duraciones: las pasa en su conjunto (semántica 0xe)
    y las muestrea el padre.
  - `nodeBlend2SyncEventsFindGeneratingNodeForSemantic` (`FUN_14063bee0`) genera la 1. `nodeBlend2Find…`
    (`FUN_140636ea0`, mezclas no sync como la capa de guardia 0x2120 o la postura 0x449) no: bajo ellas cada clip
    muestrea las suyas.
- Mezcla de los búferes (`FUN_1405fbb20` curvas, `FUN_1405fc070` discretos): **unión** de los dos hijos, con el peso
  de cada evento × (1 − w) en la fuente 0 y × w en la fuente 1 (w = peso de la mezcla); luego se añaden las duraciones
  muestreadas con peso 1.0.
- Conjunto de duración (semántica 0xe):
  - Blend2 sync (tabla 5): `TaskBlend2DurationEventTrackSets` (`FUN_140617e10` → `FUN_1405fdd20`);
  - **FeatherBlend2 sync (tabla 37): `queuePassThroughChild1` → solo las pistas de la fuente 1**;
  - FeatherBlend2 no sync (tabla 9): `queuePassThroughChild0`.
- **El juego no mira el peso.** `MorphemeEventTrackCtrl` (`FUN_1404dc380`) lee el búfer del nodo raíz:
  - un evento (pista, evento) que aparece crea su acción y la llama con "empieza" (byte `+8`);
  - mientras siga, la llama sin banderas; el frame en que falta, con "termina" (byte `+9`) y la borra;
  - los discretos llaman con empieza y termina a la vez.
  - `ChrEventTrackActionStateControl` (`FUN_14036a6e0`, pista 80: `CancelControl_*`, `StateControl`,
    `InputControl`…) solo usa esas dos banderas. **Un bloqueo está activo mientras su evento esté en el búfer de la
    raíz, con cualquier peso, aunque sea 0.**

### Otras transiciones con la misma curva (`scratchpad\c262\trans.py`)
Todas son `nodeTransit` (tabla de colas 7, findGen `+0x60` = 12), así que pasan por `FUN_1406334f0`: peso
(1 − cos(π t / duración)) / 2, modo 0 (tarea 0x1b), sin congelar origen ni destino (`+0x28` / `+0x29` / `+0x2a` = 0).
- **SM 0x400 (powerstance)**, todas con condición 0x273:
  - 0.2 s (`+0x14` = 3): 0x4a1 0x401→0x442, 0x4a6 0x401→0x46a, 0x4a3 0x442→0x401, 0x4a4 0x46a→0x401, 0x4a7
    0x442→0x46a, 0x4a8 0x46a→0x442;
  - 0.001 s (`+0x14` = 0): 0x4a0 / 0x4a2 / 0x4a5, desde 0x441 a 0x401 / 0x442 / 0x46a.
- **SM 0x210e (capa de guardia)**:
  - entrada 0x210f→0x2110, 0.1 s: 0x232a / 0x232b / 0x232e / 0x232f (0x273 + 0x26a);
  - salida por `GuardCancelSpeedCategory` (0x273 + 0x271 + 0x273): 0x2329 0.3 s, 0x232c 0.5 s, 0x232d 0.8 s, 0x2330
    1.1 s;
  - otras salidas, condiciones 0x269 / 0x26a / 0x259 sin estudiar: 0x2333..0x2336 0.1 s, 0x2338 / 0x2339 0.2 s,
    0x2331 / 0x2332 0.001 s, 0x233d 0 s (con duración 0 el código pone peso 1 de golpe);
  - otras entradas, 0.001 s (`+0x14` = 2): 0x2337, 0x233a..0x233c.
