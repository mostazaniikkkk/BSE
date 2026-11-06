# Rebote del arma contra la pared ("HitBack")

Salidas de Ghidra y scripts: `D:\Reversing\DS2\out\hitback\` (`hb.c`, `h2.c`…`h10.c` decompilados; `ee80.txt`,
`ac0.txt`, `c95d0.txt`, `pl.txt` desensamblados; `hb1.py`…`hb13.py` lectores de nmb / TAE / params / FLVER; `pe.py`
lee constantes del exe). Modelos de arma extraídos con `extract_bdt.py` + `unpack_bnd4_any.py` (scratchpad, no en el
repo). En DS2 el nombre interno es **HitBack**: `ChrHitBackMotionCtrl`, `HitBackParam`, `HitBackMatrixParam`,
`SM_HitBack`, pista `AttackHitBackControl`. (`MapHitBackreadComponent` **no** tiene relación: es la lectura en segundo
plano de la colisión del mapa, vtable `0x1410ebd58`.)

## 1. Cadena completa (confirmada por código salvo lo marcado)

1. **El golpe toca el mapa.** El volumen del ataque es el del golpe (`PlayerDamageParam`, §4), el mismo que se prueba
   contra personajes. El componente de daño del mapa (`MapHitDamageComponent`, vtable `0x1410c5628`, entrada `+0x38` =
   `FUN_1401c95d0`) rellena el resultado del impacto:
   - pone el resultado a 0 y busca la fila del golpe (`thunk_FUN_141b0fe63(golpe+0x1c)` = fila PDP);
   - `FUN_1401c99a0`: exige **PDP `+0x40` bit 6 (0x40)** (el golpe choca con el mapa); si el atacante es un bullet
     (handle tipo 3) hace pruebas extra;
   - si `golpe+0x20` ∈ {0x1a, 0x1b, 0x2d}: resultado 1 (no rebota). Qué es `golpe+0x20`: **sin confirmar**;
   - si no, elige un bit de **PDP `+0x65`** y, **si ese bit vale 0, el resultado es 3 = rebote**:
     - atacante personaje (handle tipo 2) con `golpe+0x168` bit 0, y el punto de contacto (`golpe+0x134`, la y del
       punto `+0x130`) más de **`PlayerCommonParam +0x134` = 1.0** por encima del origen del atacante (`+0x34` de su
       matriz): bit **0x08**;
     - si no, `FUN_140170d60` decide si es **pared**: el objeto tocado (`golpe+0x1a0`) es de tipo 7 y la normal del
       contacto (`golpe+0x140`, o (0, 1, 0) sin el bit 1 de `+0x168`) tiene **|n.y| ≤ sin/cos(π/4) = 0.7071** (constante
       `0x1410bc2dc` = 0.7854 rad; la función trigonométrica `0x1408326d0` no se identificó, pero sin y cos dan lo mismo
       a 45°). Pared → bit **0x08**; no pared (suelo, rampa < 45°, u objeto que no es tipo 7) → bit **0x04**.
   - En resumen: **`0x04` = no rebota en el suelo / superficies planas; `0x08` = no rebota en paredes ni en contactos
     altos.**
   - Qué es el tipo 7 de handle: por el contexto (`casting.md` §7.4), un objeto del mundo; **sin confirmar** que sea
     solo la colisión del mapa.
   - Cómo llega el bucle de impactos (`FUN_1403b1870`) a `FUN_1401c95d0`: **sin trazar** (el gestor llama a
     `víctima vt+0x28`; el puente hasta el componente no se leyó).
2. **El atacante recibe el resultado 3.** `CharacterActionCtrl vt+0x140` = `FUN_14030e630` (jugador: `FUN_14037d0a0`,
   enemigo: `FUN_14039e2e0`, que solo añaden una llamada antes), caso 3:
   - `DCP = FUN_140170be0(PDP+0x00)` (fila de `DamageCtrlParam`); si existe:
   - `chr.b8+0x104 = 1.0` (uso **sin confirmar**);
   - `FUN_140140ac0(fila 0, columna = DCP+0x0c, byte = PDP+0x66 & 3)`.
   - Que el listener del atacante (`vt+0x30` en `FUN_1403b1870`) sea el que llama a `vt+0x140`: **sin trazar**; encaja
     por el resultado 3 y por la misma fila PDP (`golpe+0x1c`).
3. **`FUN_140140ac0`** (nivel → parámetros):
   - `nivel = HitBackMatrixParam[fila].int[columna]` (`FUN_140140c50`; columnas 0..5, otra → 3).
   - `chr.b8+0x278 = HitBackParam.f[2·nivel]`, `chr.b8+0x27c = HitBackParam.f[2·nivel+1]` (`FUN_140140b60` /
     `FUN_140140bd0`; nivel ≥ 5 → 1.0, constante `0x1410ac698`).
   - `chr.b8+0x275 = byte`; activa **`chr.b8+0x4c0` bit 33** (`0x200000000`).
   - Params: `HitBackMatrixParam` = índice 0x3e (gestor `+0x6f0`, `FUN_140358ad0`), `HitBackParam` = 0x3f (`+0x700`,
     `FUN_140358b00`); nombres en la lista de `FUN_14048b620` (`0x1410f1ea0`, `0x1410f1ec8`).
4. **`ChrHitBackMotionCtrl`** (vtable `0x1410c12f8`; actualización `+0x18` = `FUN_14015ee80`), si el bit 33 está
   activo, escribe los CP de Morpheme (lista de `FUN_14015f000`) y manda el mensaje `HitBack` (`FUN_14015f0f0`):
   - `DmgHitBackDistance` (CP 0x66) = `b8+0x278`;
   - `DmgHitBackDuration` (CP 0x67) = `b8+0x27c`;
   - `DmgHitBackHold` (CP 0x64) = `b8+0x275`, pero en el jugador se sobrescribe: **1.0 si `handCtrl+0x10` ∈ {2, 3}
     (arma a dos manos), si no 0.0** (`chr+0x378 vt+0x120 → vt+0x70`, `+0x10`, `FUN_14034f1c0`; estados en
     weapons.md "Estado de las manos");
   - `DmgHitBackEventID` (CP 0x65) = `b8+0x270` (entero → float);
   - luego borra el bit 33.
5. **`b8+0x270` lo pone la animación del ataque**: pista Morpheme `AttackHitBackControl` (canal 200 →
   `ChrEventTrackActionDamage`, `FUN_140369890`): para userData 280..291, `b8+0x270 = userData`, **+6 si
   `b8+0x188 == 0`** (mano del ataque: 0 = izquierda, weapons.md). La tabla de espejo de `c0001.nmb` empareja 280↔286 …
   285↔291 (animations.md).

Otras dos entradas a `FUN_140140ac0` (fuera del tema, mismo efecto):
- **Golpe parado por un escudo** (`FUN_14013b010` → `FUN_14032ea50`): el **atacante** rebota con la **fila 1** si
  `GuardLevelMotionParam` (índice 0x34, `+0x650`, `FUN_14032efe0`) devuelve 3 o 5.
- **Red** (`FUN_14013bb50`): copia `b8+0x270` y llama con la fila 1 desde un paquete (mensaje 0xd).

## 2. Qué ataques rebotan (datos de la regulation)

Campos (fila PDP de cada ranura del ataque, `WeaponAttackMotionParam +0x2c..+0x34`):

| Campo | Efecto |
|---|---|
| PDP `+0x40` & 0x40 | 0 → el golpe no choca con el mapa |
| PDP `+0x65` & 0x04 | 1 → no rebota en suelo / superficies < 45° / objetos no tipo 7 |
| PDP `+0x65` & 0x08 | 1 → no rebota en paredes (≥ 45°) ni en contactos > 1.0 m sobre los pies |
| DCP `+0x0c` (DCP = PDP `+0x00`) | columna de `HitBackMatrixParam` → nivel |
| PDP `+0x66` & 3 | `DmgHitBackHold` inicial; vale 0 en las 3082 filas y el jugador lo sobrescribe |

En la regulation: `+0x65 & 0x0c` = 0x04 en 1700 filas, 0x0c en 746, 0 en 636; `+0x40` bit 6 en 2234 de 3082.
No hay ningún flag en `WeaponParam`, `WeaponTypeParam` ni `WeaponAttackMotionParam`: todo va por golpe (PDP).
**Las estocadas no son distintas**: la lanza tiene los mismos valores que las espadas.

Armas del mod (`hb11.py`; todas las ranuras 0–3, 10–13, 4–6, 40/42 comparten fila):

| Arma | Ataques normales (ligero, fuerte, 2 manos, dash, rodando, paso atrás, izquierda) | Columna DCP+0x0c |
|---|---|---|
| Longsword, Broken Straight Sword, Puzzling Stone Sword, Shortsword, Drakekeeper's Sword, Red Rust Sword | `+0x40` 0xf0, `+0x65` 0x04 | 3 |
| Hand Axe, Infantry Axe, Bandit Axe, Dragonslayer's Crescent Axe, Battle Axe | 0xf0 / 0x04 | 3 |
| Gyrm Axe | 0xf0 / 0x04 | **4** |
| Mace, Club, Spear, Pickaxe | 0xf0 / 0x04 | 3 |
| Fists | 0xf0 / 0x04 | **2** |

- **Rebotan en paredes y contactos altos; no en el suelo.**
- **No rebotan nunca** (`+0x65` = 0x1c, bit 0x08 activo): ataque con salto (slot +9), en caída (+8), los de la otra
  mano con el arma a dos manos (50/51), y riposte / guard break / backstab (61–63; además `+0x40` = 0x32, sin choque
  con el mapa).
- La patada (55/56): `+0x40` 0xf2, `+0x65` 0x04 → rebota en paredes como un ataque normal.
- Las animaciones de salto, caída, agarres (`…Catch…`) y escalera no tienen pista `AttackHitBackControl` (13 de 339
  nodos con 2200); las demás la tienen en toda su duración con userData 280 (208 nodos) o 281 (117); solo
  `a20_05_0030_FistAttack2WeaponHeavy1` usa 286.

## 3. Reacción

### Nivel → distancia y velocidad
`HitBackMatrixParam` (2 filas de 8 enteros; `FUN_140140c50` lee las 6 primeras):
fila 0 (pared) = `0 0 0 1 2 3`, fila 1 (escudo / red) = `0 0 1 2 3 4`.
`HitBackParam` fila 0 = pares (Distance, Duration): nivel 0 (0.25, 1.1), 1 (0.5, 1.0), 2 (0.75, 0.9), 3 (1.0, 0.6),
4 (1.25, 0.6).

| Armas del mod | Columna | Nivel (fila 0) | `DmgHitBackDistance` | `DmgHitBackDuration` |
|---|---|---|---|---|
| todas salvo las dos de abajo | 3 | 1 | 0.5 | 1.0 |
| Gyrm Axe | 4 | 2 | 0.75 | 0.9 |
| Fists | 2 | 0 | 0.25 | 1.1 |

### Animación (`c0001.nmb`, `SM_HitBack` = nodo 0x1a93, hijo de `SM_Damage` 0x18c2 en `BT_Damage`)
- Entrada por `DmgHitBackHold`: 0 → estado 0x1ab3, 1 → estado 0x1abc (condiciones 0x273 sobre CP 0x64).
- Cada estado es un nodo **0x7d** (velocidad de reproducción, como los de la esquiva en equipload.md) con
  **`DmgHitBackDuration` como multiplicador de velocidad**: 1.0 = normal, 0.6 = más lento. Las ventanas de pistas y TAE
  siguen el reloj de la animación, así que se estiran por 1 / velocidad.
- Debajo, un FeatherBlend2 (0x72, peso `BlendWeight_1`) junta:
  - un interruptor por `DmgHitBackEventID` (280 → `…0100_HitBack_Upper_R`, 281 → `…0110_HitBack_Upper_L`,
    286 / 287 → las mismas **en espejo**, nodos 0x87): versión 1 mano (0x1aad) o 2 manos (0x1ab4) según el estado;
  - un sub-SM (0x1a95 / 0x1abd) con las mismas cuatro entradas por EventID, cada una un Blend2 (0x6b) entre la
    versión 1 mano y la 2 manos con peso **`DmgHitBackDistance` en el rango [0, 2.5]**.
  - Pesos, huesos y tiempo de la mezcla: ver "Mezcla de `SM_HitBack`" abajo.
- **Solo existen las animaciones de espada recta**: los 24 nodos usan `a29_01_0100/0110` y `a29_03_0100/0110`. El TAE
  tiene también 2001/2003/2201/2203/2205/2501/2503_0100/0110 (con evento 301), pero `c0001.nmb` no los usa.
  **Todas las clases de arma usan la reacción de espada recta.**

| TAE | Animación | Duración (`.nsa`) | Avoid / Move / Guard / AttackAll bloqueados | InputControl | TAE 101100 (sin regen) |
|---|---|---|---|---|---|
| 29010100 | `a29_01_0100_StraightSwordAttack1Hand_R_HitBack_Upper_R` | 1.1667 s | 0–0.767 s | 0.4–1.167 | −0.067–1.0 |
| 29010110 | `a29_01_0110_…1Hand_R_HitBack_Upper_L` | 1.1667 s | 0–0.767 s | 0.4–1.167 | −0.067–1.0 |
| 29030100 | `a29_03_0100_…2Hand_R_HitBack_Upper_R` | 1.3333 s | 0–0.967 s | 0.567–1.333 | −0.067–1.0 |
| 29030110 | `a29_03_0110_…2Hand_R_HitBack_Upper_L` | 1.3333 s | 0–0.967 s | 0.567–1.333 | −0.067–1.0 |

Otras pistas (iguales en las 4): `ChangeStanceForced` y `UsingArm` (76 / 77) toda la animación, `StateControl`
(80) desde 0.9 / 1.033 s, `StateControl_1` (81) desde 0.767 / 0.967 s, `AttackStance` (130) al final,
`HitBackSpeedControl` (230) toda (sin lector en `FUN_140369890`), `IK`. TAE 301 de 0 a 0.167 s con parámetros
(8, 7 u 8, 15.0): **sin identificar** (no está en la fábrica `FUN_140324eb0`).

Con la velocidad del nivel: 1 mano 1.167 s (nivel 1), 1.296 s (Gyrm Axe), 1.061 s (Fists); 2 manos 1.333 / 1.481 /
1.212 s.

### Mezcla de `SM_HitBack` (decodificada)
Lectores: `out/grip/grip_alpha.py`, `grip_sm.py`; árbol, tablas de colas y atributos con scripts de scratchpad
(`tree.py`, `qt.py`, `q.py`); el código con `tools/re/disasm.py`. Implementación: `tools/re/hitback.py`.

**Árbol** (iguales en los dos estados salvo la rama pura):

| Estado | FeatherBlend2 (0x72) | Fuente 0 | Fuente 1 |
|---|---|---|---|
| `Hold` 0 → 0x1ab3 | 0x1ab2 | sub-SM 0x1a95 | interruptor 0x1aad: `a29_01` (280 / 281; 286 / 287 por espejo 0x87) |
| `Hold` 1 → 0x1abc | 0x1abb | sub-SM 0x1abd | interruptor 0x1ab4: `a29_03` (ídem) |

- Sub-SM 0x1a95 / 0x1abd: pseudoestado 0x1aa6 / 0x1ace → interruptor por `DmgHitBackEventID` (condiciones 0x273 = 280,
  281 → 0x1a9a / 0x1ac2; 286, 287 → 0x1aa1 / 0x1ac9 con espejo 0x87), mezcla 0.001 s.
- Cada entrada es un Blend2 (0x6b: 0x1a96, 0x1a99, 0x1a9d, 0x1aa0; 0x1abe, 0x1ac1, 0x1ac5, 0x1ac8) con **fuente 0 =
  `a29_01` (una mano) y fuente 1 = `a29_03` (dos manos) en los dos estados**. Pesos del atributo 0x09: **(0, 2.5)**.
- Interruptores (atributo 0x09): 0x1a9a / 0x1ac2 = (280, 281); 0x1aa1 / 0x1ac9 = (286, 287); 0x1aad / 0x1ab4 =
  (280, 281, 286, 287), en el orden de los hijos (Upper_R, Upper_L, espejo de R, espejo de L).

**Peso → mezcla** (exe):
- Las funciones de conexión del nodo (índice en `nodo +0x58`; lista de `FUN_1405f6850` en `FUN_140606e10`) son
  `nodeBlend2SyncEventsUpdateConnections` (`FUN_14063d320`, Blend2) y `nodeBlend2SyncEventsAdditiveUpdateConnections`
  (`FUN_14063bd30`, FeatherBlend2). Las dos pasan el CP por **`FUN_140636cc0`**: `(cp − w0) / (w1 − w0)`, acotado a
  [0, 1] (con los pesos del atributo 0x09).
  - Blend2: `DmgHitBackDistance` 0.25 / 0.5 / 0.75 → **0.1 / 0.2 / 0.3** hacia `a29_03`.
  - FeatherBlend2: `BlendWeight_1` → 1.0. La selección de fuentes del FeatherBlend2 (`FUN_140636a50`) solo descarta la
    fuente 1 con peso < 1e-4; con 1.0 mezcla las dos (la del Blend2, `FUN_140636c30`, se queda con una sola por
    encima de 0.9999).
- **`BlendWeight_1`** = CP 0x32, valor por defecto **1.0** (nodo CP 0x32, atributo 0x03 `+0x10`; el mismo lector que
  da 0.95 en `BlenWeight_095`, nodo 0x30). La cadena `BlendWeight_1` **no está en el exe** (ni ASCII ni UTF-16; sí
  están `DmgHitBackDistance` y `GuardStance`), así que el juego no la escribe por nombre. Que no se escriba por
  índice: **sin confirmar**.
- **Pesos por hueso** (atributo 0x57, los dos iguales en 0x1ab2 y 0x1abb): **1.0 en `Spine` y todo lo que cuelga de él**
  (44 huesos: columna, cuello, cabeza, clavículas, brazos, manos, dedos, `L_Weapon`, `R_Weapon`) y **0 en
  `CharacterWorldSpaceTM`, `Character`, `Master`, `Root`, `Pelvis` y las piernas** (15 huesos).
  - Peso efectivo por hueso = peso × alfa (`FUN_14066d450` multiplica el vector de alfas por el peso; huesos fuera del
    vector, solo el peso).
- **Resultado**: torso, cabeza y brazos = la animación pura del estado; pelvis y piernas = el Blend2 1 mano / 2 manos.
  En el estado de dos manos las piernas son 70–90 % la versión de una mano.

**Matemática de la mezcla** (exe):
- Colas del nodo (tabla en `nodo +0x38`; cabecera de tablas en `c0001.nmb` 0xe72f8): Blend2 tabla 5, FeatherBlend2
  tabla 37.
  - Transformaciones: `nodeBlend2QueueBlend2TransformBuffsInterpAttInterpPos` → tarea 0x1b (`FUN_14061b5d0` →
    `FUN_14066c6e0`); `nodeFeatherBlend2Queue…InterpAttInterpPos` → tarea 0x3b (`FUN_14061e3a0` → `FUN_14066d450`).
  - Posición: `p0 + (p1 − p0) · w`.
  - Rotación: **fastSlerp de NMP**. Signo del producto escalar aplicado a q1 (camino corto), `rcp = 1 / (1 + |d|)`,
    `C1, C3, C5, C7` = polinomios cúbicos en `|d|` con coeficientes en 0x14110d2c0 / 0x14110d2b0 / 0x14110d2d0 /
    0x14110d290, pesos `((C1 + (C3 + (C5 + C7 T²) T²) T²) T · rcp` con T = 1 − w y t = w. No renormaliza (error de
    norma ≤ 4·10⁻⁵ en estas mezclas; frente a un slerp exacto, ≤ 0.002°).
- **Tiempo**: las dos colas son de eventos sync (`nodeBlend2SyncEventsQueueTimePos`, `FUN_14063cde0`).
  - El FeatherBlend2 pasa la pista sync de su **fuente 1** (tabla 37, semántica 0xc = `queuePassThroughChild1`): el
    reloj es la animación pura; el Blend2 y sus dos clips se muestrean en la misma posición sync (evento + fracción).
  - Pista sync (`TaskInitSyncEventTrackFromDiscreteEventTrack`, `FUN_140610c40` → `FUN_1405fd130`), del atributo
    0x15 de cada clip: pista discreta `+0x84` = 0 = **`Footsteps`**; región del clip `+0x78` / `+0x7c` (0.0016 / 1.0
    en `a29_01`, 0.0014 / 1.0 en `a29_03`); duración `+0x80`. Inicios normalizados a la región; duración de cada
    evento = hasta el siguiente; el último hasta 1.0 (byte `+0x88` = 0).
  - Los cuatro clips tienen 4 eventos, así que el evento i de uno va con el i de los otros (`FUN_1405fc820`, mezcla de
    pistas sync, con igual número de eventos). Inicios normalizados: `a29_01` 0 / 0.170 / 0.227 / 0.771; `a29_03_0100`
    0 / 0.199 / 0.249 / 0.825; `a29_03_0110` 0 / 0.274 / 0.324 / 0.825.
  - Duración del resultado = la de la región del clip puro: **1.1648 s** (una mano) / **1.3315 s** (dos manos), a
    velocidad 1. Las ventanas de §3 (TAE) usan la duración entera del `.nsa` (1.1667 / 1.3333); la diferencia
    (≤ 2 ms) es el inicio de la región.
- **Espejo** (286 / 287): los nodos 0x87 están en cada rama por separado. Como el espejo es lineal por hueso y los alfas
  son simétricos (izquierda = derecha), espejar la mezcla da lo mismo; el mod espeja la mano izquierda él mismo.

**Pistas de eventos de la mezcla** (código en animations.md, "Condición 0x262 y pistas de duración"):
- Las pistas de duración (`CancelControl_*`, `InputControl`, `StateControl*`, `AttackStance`, `TimeAct`…) **salen
  solo de la fuente 1, el clip puro**:
  - el FeatherBlend2 0x1ab2 / 0x1abb (tabla de colas 37) pasa su conjunto de duración (semántica 0xe) con
    `queuePassThroughChild1`;
  - el Blend2 1 mano / 2 manos (fuente 0) y los clips de debajo no muestrean las suyas: su padre es una mezcla sync
    que genera la semántica 1 (`FUN_14063bee0`; índice `+0x60` = 9 en 0x1ab2 y 0x1a96), así que solo combinan sin
    duraciones (tarea 0x58). El conjunto que mezcla el Blend2 (`TaskBlend2DurationEventTrackSets`) se descarta en el
    FeatherBlend2.
  - Las muestrea el FeatherBlend2 (tarea 0x59) o un antecesor por el que pasan sin cambios (0x7d y las SM las pasan
    con `queuePassThroughChild0`). En los dos casos son las del clip puro, con su posición: la pista sync también es la
    de la fuente 1 (semántica 0xc).
- Los eventos discretos y de curva sí se juntan: unión, con peso × (1 − w) los de la fuente 0 y × w los de la 1
  (w = `BlendWeight_1` = 1.0 → los de la fuente 0 quedan con peso 0). Ningún lector de estos clips depende de ellos.
- **El juego no mira el peso** (`MorphemeEventTrackCtrl` `FUN_1404dc380`, `ChrEventTrackActionStateControl`
  `FUN_14036a6e0`): un bloqueo está activo mientras su evento esté en el búfer de la raíz.
- **Resultado: las ventanas son las del clip puro para las tres distancias**, en el reloj del clip puro y ÷ velocidad.
  Coinciden con las que ya exporta `weapons.json` (`hitBack.<agarre>.<lado>`: `avoidLock`, `moveLock`, `attackLock`,
  `guardLock`). No hace falta un bloque `locks` por distancia: la distancia solo cambia las piernas.
  - Medidas sobre la región del clip (inicio 0.0016 / 0.0014), los finales quedan ≤ 2 ms antes que en la duración
    entera del `.nsa` que usa el exportador (p. ej. 0.765 s en vez de 0.767 s en una mano). Es la misma diferencia de
    la duración de la región, arriba.
- **TAE (`regenBlock` = 101100)**: el TAE no es una pista de Morpheme. Lo lanza la pista `TimeAct` (canal 1000, userData
  = id de TAE) que llega a la raíz, en el tiempo (1 − valor) × duración del TAE (equipload.md). Como `TimeAct` también
  es una pista de duración, **corre solo el TAE del clip puro**: 29010100 / 29010110 / 29030100 / 29030110. Es el que
  exporta `weapons.json`.
- Espejo (286 / 287): no cambia nada; los userData de estas pistas no están en la tabla de espejo de eventos.

**Exportado** (`ds2_anims.py`): `ds2_hitback_<1h|2h>_<r|l>_<25|50|75>` (12 clips), sobre la duración de la región
del clip puro, con la cadencia de muestreo del clip puro. Rutas en `weapons.json` → `hitBack.<agarre>.<lado>.clips`.

**Sin confirmar**:
- Si la red empieza la animación pura en el inicio de la región (0.0016) o en 0: se tomó la región (atributo 0x15),
  que es lo que usa la pista sync.
- La mezcla 0.001 s desde el pseudoestado del sub-SM y la de entrada a `SM_HitBack` (0.001 s) no se hornean.

### Otros cambios del exportador (misma tanda)
- **Guardia, pose fija**: la `_hold` de las guardias se toma ahora en la fracción **0.95** del clip
  (`guardLayer.holdFrame`), no en el último frame.
  - Una mano: SingleFrame 0x2113 (CP `BlenWeight_095`, weapon_arts.md §8.1).
  - Dos manos: **también**. `BT_Guard2Hand_R` (SM 0x2210, estado 0x2217, SingleFrame **0x21fa**) y `BT_Guard2Hand_L`
    (SM 0x22a8, estado 0x22af, SingleFrame **0x2286**), los dos con CP 0x30 `BlenWeight_095` (0.95), tras la
    condición 0x262 con mezcla 0.1 s (transiciones 0x222e / 0x22c6), igual que 0x2141 en una mano.
  - Cuándo empieza esa mezcla y cómo es: weapon_arts.md §8.4 (al 95 % del clip, peso (1 − cos(π t / 0.1)) / 2, el
    clip sigue avanzando).
  - El SingleFrame (tipo 0x6d, tabla 21: `nodeSingleFrameQueueUpdateTimePos` → tarea 0x88
    `TaskSetUpdateTimeViaControlParam`, `FUN_1406126d0`) acota el CP a [0, 1]: es una fracción. Las seis guardias
    del mod tienen región [0, 1], así que 0.95 de la región = 0.95 de la duración.
  - Afecta a `ds2_20320010`, `ds2_45320010`, `ds2_29330010`, `ds2_36330010`, `ds2_25330010` y `ds2_45340010`
    (`_hold`).
  - Sin cambiar: la capa aditiva de respiración del estado fijo (Blend2 0x2124 / 0x2215 / 0x22ad con
    `AdditiveStand` / `AdditiveBreathe`, nodos 0x69 / 0xaa) no se exporta.
- **Postura doble (powerstance)**: nuevo `ds2_<21450010|25450010|36450010>[_l]_hold`, la misma pose que hornean los
  `_2w_` (último frame, `SingleFrame_1` = 1.0; espejada en `_l`).

### Por mano y agarre
- Mano izquierda → EventID +6 → animación espejada. Dos manos → `Hold` = 1 → versión `a29_03`.
- 280 / 281 (`Upper_R` / `Upper_L`) los fija cada animación de ataque; el lado no depende del punto de contacto.

### Efectos
- **El ataque se corta**: el mensaje lleva Morpheme a `BT_Damage`, la animación del ataque deja de correr y con ella
  su TAE 2200. Si el final del 2200 (`FUN_140374790`) se ejecuta al interrumpirse: **sin confirmar**.
- **Cadena**: la animación de rebote no tiene `AttackComboControl` → `b8+0x4d4` = 0 → el siguiente ataque empieza la
  cadena desde el principio (regla de combo de weapons.md). Se puede atacar, esquivar, moverse o bloquear desde
  0.767 s (1 mano) / 0.967 s (2 manos), divididos por la velocidad.
- **Estamina**: nada extra en el camino trazado; la estamina del ataque ya se cobró al empezar el 2200. La animación
  bloquea la regeneración de −0.067 a 1.0 s (TAE 101100).
- **Durabilidad**: no aparece en el camino trazado (`FUN_1401c95d0`, `FUN_14030e630` caso 3, `FUN_140140ac0`).
  **Sin confirmar** que no se gaste.
- **Daño a un enemigo en el mismo golpe**: lo que el golpe tocó antes del rebote ya recibió daño (los contactos se
  procesan por separado). En un mismo frame los contactos se ordenan por distancia (`FUN_1403b1870`); si el bucle sigue
  tras un resultado 3: **sin confirmar**.
- **Efecto visual / sonido**: si `golpe+0x1a4` ≠ 0, `FUN_1401c9c60` busca el material del suelo en el punto de contacto
  (rayo de `FUN_1401c97d0`, filtro 0x1f) → `GroundMaterialParam` (índice 0x30, `+0x610`; `+0x00`, `+0x04`, byte
  `+0x10`) → `FUN_1403c0bb0`, petición de efecto en el punto `golpe+0x130`, con tipo 2 cuando el resultado es 3. Los
  ids finales de SFX / sonido: **sin resolver**.

## 4. Volumen del golpe y trayectoria

- Forma (PDP; enemy_ai.md §8.4, casting.md §7.4): `+0x30` origen (1 = parte equipada), `+0x31` dummy, `+0x33` 1 =
  cápsula, `+0x34` radio, `+0x38` largo.

| Armas | Dummy | Radio | Largo (ligero / fuerte) |
|---|---|---|---|
| Longsword, Drakekeeper's, Broken Straight, Red Rust | 100 | 0.3 | 0.75 (Longsword 2 manos fuerte 0.65) |
| Shortsword | 100 | 0.3 | 0.75 / 0.65 |
| Puzzling Stone Sword | 100 | 0.3 | 0.75 / 1.0 |
| Hachas (Hand, Infantry, Bandit, Crescent, Battle), Mace, Club | 100 | 0.3 | 0.55 |
| Gyrm Axe | 101 | 0.35 | 0.4 |
| Spear | 100 | 0.3 | 1.55 (salto: dummy 110, 0.2, 2.3) |
| Pickaxe | 101 | 0.3 | 0.8 |
| Fists | 100 | 0.3 | 0.1 |
| Caída (todas) | 9 de `c0001` (`+0x30` = 0) | 0.8 | 0.8–1.1 |

- Dummies de los modelos (`WP_<WeaponParam+0x04>_M.flv`, espacio del modelo, padre `Model_Dmy`):
  - Longsword `WP_1220_M`: 100 en (0, −0.0735, 0.0026), adelante (0, −1, 0); hoja hasta y = −0.897 (bbox).
  - Spear `WP_2800_M`: 100 en (0, −0.0769, 0.0026), punta 101 en y = −0.987, bbox hasta −1.371.
  - Gyrm Axe `WP_2070_M`: 101 en (0, −0.553, −0.283) adelante (0, −1, 0). Pickaxe `WP_2610_M`: 101 en (0, −0.676,
    0.428) adelante (0, 0, −1).
  - El resto, en `hb12.py`.
  - **Eje de la cápsula: sin confirmar.** casting.md lee el segundo extremo en (0, 0, −largo) del espacio local, pero
    con Z = adelante eso iría hacia el pomo. Los datos (hoja y dummy 101 hacia −Y del modelo, que es el "adelante" de
    la dummy 100) encajan con **dummy 100 + adelante × largo**.
- **Trayectoria desde los `.nsa`: sí se puede.** Mismo método que `cast_origins.py`:
  `punto_modelo = R_Weapon(t) · inversa(dummy 1 del arma) · dummy 20 de c0001 · dummy 100 (± adelante × largo)`.
  Prueba (`hb13.py`, Longsword, `29010012`): en `R_Weapon` (rig) base (−0.015, −0.004, 0.073) y punta (−0.033, −0.004,
  0.823); de 0.467 a 0.633 s la punta baja de z = 1.98 a z ≈ 0 delante del personaje. Se puede exportar por
  animación y por frame, igual que `bulletBones`.

## 5. Exportado a `weapons.json` (`tools/re/weapons.py` + `cast_origins.py`)
- Por ranura de ataque (`weapons[w][mano][tipo]`):
  - `hitBack` = `{map, noFloor, noWall, distance, speed}`;
  - `hitVolume` = `{dummy, radius, length, source, capsule}`, con `source` = `"weapon"` (PDP `+0x30` = 1), `"body"` (0,
    dummy de c0001) u `"owner"` (otro valor).
  - Es un solo objeto si todos los golpes de la animación usan la misma fila, o una lista alineada con `hits` (en la
    caída: `airHits` y luego `landingHits`). `null` = golpe con la ranura PDP vacía, o ranura de hechizo / sin golpes.
  - `map` es falso también si no hay fila de `DamageCtrlParam` (sin ella `FUN_14030e630` no rebota).
- `animations[tae].hitBackEvent`: userData de `AttackHitBackControl` (280 / 281 / 286), o `null` si la animación no
  tiene esa pista. `animations[tae].weaponBones`: `R_Weapon` cada 1/30 s entre el primer inicio y el último fin de los
  golpes. Ninguna animación de ataque exportada es de la mano izquierda, así que nunca se usa `L_Weapon`. La mano
  izquierda del mod espeja la derecha: el espejo de estos puntos **lo hace el mod** (no se exporta). La caída
  (`fall`) no lleva `weaponBones`: usa la dummy 9 del cuerpo.
- `weapons[w].hitDummies[id]` = `{pos, fwd}` en el marco de `R_Weapon` (ejes locales del rig, metros).
  `bodyHitDummies[id]` = `{bone, pos, fwd}` en el marco local del hueso padre de c0001 (9 → `Master`, 7 → `Head`).
- `hitBack` (raíz) = `{oneHanded, twoHanded}` × `{upperR, upperL}` → `{taeId, nsa, xmd, duration, avoidLock, moveLock,
  attackLock, guardLock, regenBlock}`. Animaciones `ds2_29010100`, `ds2_29010110`, `ds2_29030100` y `ds2_29030110`.
  Además, `clips` = `{"<distance>": "soulsmode:ds2_hitback_<1h|2h>_<r|l>_<distance×100>"}` para cada `distance` de
  las ranuras que rebotan (`map` = true: 0.25, 0.5, 0.75): la mezcla real de la red (§3, "Mezcla de `SM_HitBack`"),
  horneada por `ds2_anims.py` con `tools/re/hitback.py`.
- **Eje de la cápsula elegido: `dummy.fwd`** (de la dummy hacia la hoja). Lo respaldan los modelos: en las 16 armas del
  mod la hoja, la cabeza o la punta (dummy 101 / 110, bbox) están hacia el "adelante" de la dummy 100. El eje que usa
  el código: **sin confirmar**.
- Modelo de cada arma: `WP_<WeaponParam+0x04>_M`. Para el escudo (`WeaponParam +0x15` = 3) se usa `SD_1230_M`, porque
  `WP_1230_M` es otra arma. El código que elige el prefijo: **sin confirmar**.
- **Sin confirmar**: si el hueso `Master` (dummy 9) se mueve con la animación (no se muestrea).

## Sin confirmar / pendiente
- El enlace entre el bucle de impactos (`FUN_1403b1870`, `víctima vt+0x28`, listener `vt+0x30`) y
  `MapHitDamageComponent +0x38` / `CharacterActionCtrl +0x140`.
- Que el golpe del jugador se registre en el mismo gestor y solo mientras dura el 2200 (`FUN_140373a40` guarda la
  ranura activa; la llamada a `FUN_1403186a0` no se trazó).
- Qué es el handle tipo 7, `golpe+0x20` (0x1a / 0x1b / 0x2d), `golpe+0x168` bits 0/1 y `golpe+0x1a4`.
- El eje de la cápsula (§4) y qué parte equipada se usa con `+0x30 = 1` (se supone el arma del ataque).
- La función trigonométrica de `FUN_140170d60` (seno o coseno; a 45° da igual).
- La salida del SM `SM_HitBack`. (Cómo mezcla el FeatherBlend2 las pistas de eventos: resuelto en §3, "Pistas de
  eventos de la mezcla".)
- `chr.b8+0x104 = 1.0`, `HitBackSpeedControl` (230) y el TAE 301.
- Durabilidad, y si el 2200 interrumpido libera la ranura.
- Ids de SFX / sonido (`FUN_1403c0bb0`).

## Implementación en el mod (2026-11-01)
- `SoulsAttack.hitBack`: la cápsula (dummy, radio, largo de `hitVolume`) se prueba contra las cajas de colisión de los
  bloques en cada muestra de `weaponBones` (1/30 s) desde el tick anterior, dentro de la ventana de golpe. Pared =
  |n.y| ≤ cos 45° de la normal del contacto (`FUN_140170d60`), o contacto > 1.0 m sobre los pies.
- El rebote se reproduce a `DmgHitBackDuration` (`hitBack.speed`): duración y ventanas ÷ velocidad, en servidor y
  cliente. La animación es el clip horneado de `hitBack.<agarre>.<lado>.clips[distancia]`.
- Límite del motor: la prueba es por muestra (1/30 s), no el volumen barrido entre muestras.
