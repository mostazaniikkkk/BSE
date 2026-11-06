# Fijar objetivo (lock-on)

Salida de Ghidra en `D:\Reversing\DS2\out\lk_*` (`lk_ctrl.c`, `lk_score.c`, `lk_misc.c`, `lk_ctor.c`, `lk_cam1.c`,
`lk_camfuncs.c`). Exportado por `tools/re/lockon.py` a `data/soulsmode/ds2/lockon.json`.

## Controlador
- `PlayerLockTargetCtrl`:
  - vtable `0x1410e51e8`, ctor `FUN_140382510`; base `CharacterLockTargetCtrl` (`FUN_140319d90`);
  - está en `chr+0x3a0`;
  - update `FUN_140382630`; objetivo actual con `vfunc +0x20` (`FUN_140319f90`).
- Params: LockOnParam (índice 0x31), LockTargetParam (0x32), LockOnCameraOffsetParam (0x33), en `FUN_14048b620`.
  - **LockOnParam solo lo lee `FUN_140382630`**: es solo del jugador.
  - Fila por defecto: 0. El ctor `FUN_14031cbc0` pone a cero los ids de `chr+0xb0 +0xb8`.
- Del `CommonParam` del objetivo (`chr+0x40`):
  - `+0xbc` fila de LockTargetParam (siempre 0);
  - `+0xc0` fila de LockOnCameraOffsetParam;
  - `+0xc4` / `+0xc8` radio y altura de exclusión;
  - `+0x360` tiempo sin línea de visión.

| LockOnParam 0 | Valor | Uso |
|---|---|---|
| f0 | 22.5 m | radio de la lista de candidatos (× LockTargetParam f0 = 1) |
| f1 | 18 m | no lo lee el código del lock |
| f2 | 15 m | distancia para fijar (× LockTargetParam f1 = 1) |
| f3 / f4 | 90 / 50 | × 0.01: cajas de zona de pantalla |
| f5 / f6 | 75° / 45° | cono horizontal / vertical, **respecto a la cámara** |
| f7 | 5 m | a menos de esto vale aunque esté fuera del cono (si su zona anterior era 0) |

## Elegir objetivo (`FUN_140383c30` / `FUN_140383110` / `FUN_140382f40`)
- **Candidatos**: personajes del bando contrario, vivos, fuera de la zona de exclusión
  (|d| < `+0xc4` y |dy| < `+0xc8`) y a menos de 22.5 m.
- **Visibles**:
  - dentro del cono de la cámara y a ≤ 15 m, o a ≤ 5 m con zona anterior 0;
  - un rayo cámara → punto de fijado (`FUN_140bbfc10`, filtro 0x1f) que choca marca "ocluido".
- **Puntuación**: `(1000 − clamp(dist, 0, 1000))` + bonificación por zona: 1 → 3000, 2 → 2000, 3 → 1000, 0 → 0.
  - Zona 1: caja (0.5, 0.9); zona 2: (1.0, 0.9); zona 3: en pantalla. El orden de los ejes **no está confirmado**.
- Gana la mayor puntuación entre los visibles y no ocluidos; si no hay ninguno, no se fija.
- **Sin objetivo** el botón recentra la cámara (`FUN_14049cbf0`, `FUN_14049df40`):
  - duración = lerp(`+0x100` 1.0 s, `+0x104` 1.5 s) según |Δyaw|/π;
  - suavizado 1 − (1 − t/T)^`+0x108` (10);
  - yaw objetivo: probablemente el frente del personaje (`cam+0x630`); pitch objetivo (`cam+0x6f0`) sin rastrear.

## Perder el objetivo
- **Distancia**: el objetivo actual sale de la lista a más de 22.5 m. Pasa al mejor visible o se suelta.
  - Una vez fijado, ni el cono ni los 15 m ni el rayo cuentan para el actual (puntuación 3.37e38).
- **Sin línea de visión**: `ctrl+0x134` acumula dt mientras está ocluido. Al llegar a `+0x360` del objetivo
  (enemigos 7 s, jugadores 2 s) se vuelve a elegir. Se reinicia en cuanto deja de estar ocluido.
- **Muerte** (`+0x584`, supuesto "muerto"): se suelta (no cambia de objetivo).
- **Botón** con objetivo: suelta.

## Cambiar de objetivo (`FUN_140383640` / `FUN_140383760`)
- Stick > 0.75 con enfriamiento 0.5 s; el enfriamiento se reinicia con el stick por debajo de 0.5.
- Elige, en pantalla y del lado del stick, el candidato con mayor (d·s)/|d|².
- **Portado con el ratón** (elección del usuario): el giro pedido por el ratón en un tick ÷ 30° (decisión de diseño) hace
  de stick; > 0.75 cambia de objetivo con la regla de `FUN_140383760`, espera 0.5 s, se rearma por debajo de 0.5.
- Sprint fijado: el cliente avisa al servidor del fijado y el drenaje A pasa a 232.75/s.

## Cámara fijada (`FUN_14049df40`, CameraExFollowParam fila 0)
- Apunta del pivote al objetivo.
  - El pitch suma `+0xc0` (0.55) × FOV × 0.5 = 12.1°, más un término asin sin rastrear. **Signo sin confirmar.**
  - El pitch se limita a lerp(`+0x80/+0x84` ±40°, `+0x78/+0x7c` −40°/+70°), según |dy| entre `+0x88` (0.5 m) y
    `+0x8c` (2 m).
- Cada frame se mueve el exceso sobre la zona muerta `+0xbc` (3°) × un factor que sube de 0 a `+0xb8` (0.1) en
  `+0x118` (0.8 s).
- El stick no mueve la cámara (`+0xd0 = 0`).
- LockOnCameraOffsetParam del objetivo: la fila 10030 (113 enemigos y el jugador) no cambia nada; se mezcla en 3 s.
- Seguimiento de posición fijado (`FUN_14049c120`, `+0x54` / `+0x58` / `+0x114`): **sin confirmar**.

## Personaje fijado
- Morpheme: IsLockOn (10), IsSideWalk (8), IsBattleWalk (9); FB_Speed / LR_Speed en el marco local
  (`FUN_140363a20`). No está rastreado quién activa `chr.b8+0x119`.
- Animaciones: `a00_06_0010..0018` SideWalk, `a00_07_0010..0018` SideWalkJog, `_Battle`. El árbol de mezcla
  **no está decodificado**.
- Velocidad fijado: ChrMoveParam `+0x84/+0x90` (5.0 / 6.5 m/s) en vez de `+0x3c/+0x48`. No portado: Minecraft
  usa su propia velocidad.
- Giro del cuerpo específico del lock: **sin confirmar**.

## Implementado (`ds2/LockOnData`, `client/camera/LockOn`, `SoulsCamera`, `Locomotion`)
- Tecla "Fijar objetivo" (Y por defecto, libre en vanilla; la tecla es una elección): fija, suelta o recentra.
- Elección, mantenimiento, pérdida por distancia, por línea de visión y por muerte, como arriba.
- Cámara Souls fijada:
  - sigue al objetivo con la zona muerta, el factor y la rampa;
  - el factor de DS2 es por frame; se aplica como a 60 fps (supuesto);
  - el ratón no mueve la cámara;
  - recentrado sin objetivo (solo yaw; el pitch se mantiene).
- Cuerpo: mira al objetivo y las teclas lo mueven de lado o hacia atrás.
- Animaciones: SideWalk (agachado) y SideWalkJog (normal), cuatro direcciones; suena el clip de la dirección dominante.
- **Supuestos o elecciones**:
  - qué entidades son "enemigos": cualquier ser vivo salvo aliados y soportes de armadura;
  - los mobs usan los valores más comunes de EnemyCommonParam: 7 s y exclusión 0.25 m / 5 m;
  - el punto de fijado es el centro de la caja del objetivo (DS2 usa `vfunc +0x50`, sin rastrear);
  - signo del desfase de pitch: mirar más hacia abajo (mismo convenio provisional que `SoulsCamera`);
  - en primera persona la vista sigue al objetivo sin el desfase (DS2 no tiene primera persona);
  - el giro del cuerpo usa la velocidad de `Movement.turnRate`;
  - al esprintar no se camina de lado (no confirmado en DS2).
- **Barra de vida del objetivo fijado** (`client/camera/LockOnHud`, pedido del usuario): barra roja sobre la cabeza,
  proyectada en pantalla y acotada a sus bordes. Aspecto y tamaño son propios, no salen de DS2.
- **No portado**: cambio de objetivo, seguimiento de posición de la cámara fijada, velocidades fijado y término asin.
