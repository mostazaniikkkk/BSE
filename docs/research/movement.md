# Movimiento del jugador (`ChrMoveParam` fila 100, `chr+0x48`)

## Velocidad de giro del cuerpo — confirmada
- **Origen**: `PlayerMovementCtrl::vfunc+0x68` (`FUN_140387f40`). La usa el movimiento base (`FUN_140335fd0`): calcula el
  ángulo entre la orientación actual y la dirección deseada y gira hacia ella a esa velocidad (rad/s).
- **Modo normal**: `lerp(+0x2c, +0x30)` según la velocidad entre `+0x34` y `+0x38`.
  = **720°/s a ≤1.5 m/s → 1080°/s a ≥4.0 m/s**.
- **Modo 3**: `lerp(+0x54, +0x58)` entre `+0x5c` y `+0x60` = **520°/s a ≤5.35 m/s → 640°/s a ≥6.5 m/s**.
  Probablemente el dash: es la única franja de velocidad por encima del trote.
- **Otros modos**:
  - con el flag `ctrl+0x88 & 1`: `+0x9c..+0xa0` entre `+0xa4..+0xa8`, o fijo `+0x78` (1600°/s);
  - modo 6: `ctrl+0x14c`;
  - con `chr.c0+0x54 ≠ 0`: valores de `ChrParam/ChrCommonParam +0x1c0..+0x1f0`.
- **Pendiente**: el factor que depende del ángulo restante (`vfunc+0x88`) y `DAT_14157f3c0`.

## Velocidades de referencia
- **Bandas de velocidad**: las del giro dan 1.5 m/s (lento), 4.0 m/s (trote) y 5.35–6.5 m/s (dash).
- **`ChrMoveParam+0x48` = 6.15 m/s y `+0x6c` = 4.2 m/s**: velocidades máximas que `FUN_14031d490` usa para normalizar
  la velocidad física, por ejemplo para los pasos.
- **La velocidad real** en DS2 sale del movimiento raíz de las animaciones (`.nsa`, sin descomprimir).
- **Comparación con Minecraft**: agachado 1.3, andar 4.3 y sprint 5.6 m/s caen dentro de esas bandas. Por eso
  se mantienen las velocidades de Minecraft.

## Aplicado
`ds2/Movement.turnRate` → `SoulsCamera`: cada tick el cuerpo gira como máximo `turnRate(velocidad, sprint) × 0.05 s`.
Esto reemplaza los 30°/tick provisionales.

## Salto y caída: animación (2026-11-02; volcados `scratchpad\fall\`)
Estados de `StateMachine_Main` 0xd2 en c0001.nmb y quién escribe sus CP.

### Decodificación
- SM (atributo 0x26): `+0x28` = evaluar primero las globales (la raíz 0xd2 sí); por estado, `+0x22` / `+0x28` = sus
  salidas en orden de prioridad (`FUN_1405faa80` toma la primera que pasa). Condiciones: 0x26f / 0x270 (entero > / <),
  0x271 (entero en rango), 0x25e / 0x260 (float > / <), 0x273 (float en rango), 0x25a (evento discreto), 0x269 (nodo
  activo), 0x259 (mensaje). Mensajes: 8 Jump, 9 StandJump, 25 JumpAttack, 16 DamageFullBody.
- CP (`ChrPassiveActionMotionCtrl`, `FUN_140362c40`): **IsFall** = `b8+0x4c8` bit 12; **IsLanding** = contacto con el
  suelo (`phys+0xc5`); **LandingCategory** (al aterrizar de una caída marcada): 1 si la caída desde el ápice ≥ H y la
  velocidad horizontal ≥ V; si no, 0. Salto: H = 0, V = 4.0 m/s (ChrMoveParam 100 `+0x15c` / `+0x160`); otras: H =
  5.0 m, V = 4.5 m/s (`+0x154` / `+0x158`).
- Controlador de caída `FUN_140372620`: el ápice sigue al personaje mientras sube o está en el suelo; **IsFall** cuando
  baja al menos 0.5 m por debajo del ápice (ChrMoveParam `+0x1f0`) o con SM_Jump activo.

### SM_Jump 0x17ec
- Entra con el mensaje Jump o StandJump (0x2080 / 0x2093) desde la locomoción o la esquiva: 0.1 s, desde 0.
- Siempre **JumpTakeoff** (a00_13_0020, 1.6667 s): **StandJumpTakeoff es inalcanzable** en c0001.
- Salidas por prioridad: ataque / ítem; al final del clip (StateControl1 termina, 1.6667 s) → SM_Fall 0.2 s; en el
  suelo con LandingCategory 1 y StateControl1 activo (desde 0.4333 s) → RollLanding; con LandingCategory 0 →
  locomoción en 0.0001 s. Aterrizar antes de 0.4333 s espera a ese momento.

### SM_Fall 0xd3
- 0x11d = FallLoop (a00_14_0010, 0.6667 s, bucle) (una FeatherBlend2 con una reacción en el aire en la parte de arriba,
  solo con DmgCategory 200 / 210).
- Entra desde la locomoción con IsFall > 0, sin AttackDuration ni ataque en caída: **0.7 s**, empezando en la fase de
  la locomoción (método 3). Desde la esquiva / paso / daño: 0.3 s.
- Aterrizaje: LandingCategory 1 → **RollLanding** (a00_14_0020, 0.7333 s), y de ahí a la locomoción a 0.5667 s en
  0.2 s si se mueve o a 0.6333 s en 0.3 s; LandingCategory 0 → locomoción en 0.0001 s (**no hay clip de aterrizaje
  normal**).
- Temblor al aterrizar: capa aditiva en BT_Move con peso `LandingRate` r (receta de c0001.nmb y DarkSoulsII.exe,
  verificada):
  - **Pulso**: empieza en un aterrizaje marcado (tras un salto o una caída ≥ 0.5 m; con RollLanding o sin él), desde el
    frame siguiente. Sube 0.1 s con r = A·sin(φ·π/2) y baja en D s con r = A·(1 − sin(φ·π/2)).
    A = max(base, min(R, 1)), R = lerp(0.8, 1.25, t)·clamp(caída/4.0, 0, 1), t = clamp(carga %/200, 0, 1).
    Caída: base 0.10, D = lerp(0.5, 1.5, t). Salto: base 0.70, D = lerp(0.7, 1.0, t). Caída = ápice − altura al tocar.
  - **Capa**: activa mientras r > 0; entra en 0.001 s y sale en 0.1 s cuando r vuelve a 0 (peso (1 − cos)/2).
  - **Por hueso**, en orden: (1) aditivo de agachado: AdditiveStand (a00_50_0220) → AdditiveCrouch (a00_50_0210) a
    r/10 (BlendN de pesos 0 y 10), delta C·Stand⁻¹ a la izquierda y posición C.p − Stand.p; solo Pelvis, Spine,
    Spine1, Neck, Head, clavículas, brazos y antebrazos. (2) HipsIK (tarea 0x47, mismos atributos que la 0x1924 del
    daño): pelvis r·(0, 0.1, −0.4) m en espacio del modelo y rotación slerp(I, Euler(25°, 0, 0), r), con el IK de
    piernas que deja los pies. (3) Aditivo de temblor AdditiveShakeLanding (a00_50_0240, 0.7333 s, sin bucle):
    slerp(I, S·Stand⁻¹, α) a la izquierda, sin posiciones. α lento (velocidad horizontal ≤ 3.4 m/s): Spine/Spine1
    0.65, Neck/Head 0.5, Clavicle/UpperArm 0.1, Forearm 0.2; rápido: Spine/Spine1 0.85, Neck/Head/UpperArm/Forearm
    1.0, Clavicle 0.5; el resto 0. Lento → rápido en 0.1 s, rápido → lento en 0.3 s.
  - Sin establecer: en qué tiempo empieza el clip del temblor al entrar (se toma 0) y qué hace pasado su final (se
    toma el último frame).

### En el mod (`AirClient`, `Locomotion`)
- La locomoción sigue sonando en el aire (no se reinicia al aterrizar) y la capa del aire pone JumpTakeoff / FallLoop /
  RollLanding con las reglas de arriba y fundidos (1 − cos)/2.
- Lo detecta en el cliente por el movimiento: salto = deja el suelo subiendo; caída = baja ≥ 0.5 m bajo el ápice.
- FallLoop empieza en la fase de Footsteps de la locomoción (método 3, `AirClient.fallStart`).
- Corregido (2026-11-02): la caída desde el ápice se lee antes de que el ápice siga al personaje al tocar el suelo
  (antes valía siempre 0, así que una caída nunca daba RollLanding).
- Sin establecer: quién elige Jump o StandJump (`b8+4`) y el impulso vertical del salto de DS2.

### En el mod: temblor al aterrizar (`LandingShakeClient`)
- `AirClient` avisa al tocar el suelo desde JumpTakeoff o FallLoop, con la caída y si venía de un salto. La capa va
  sobre la locomoción (5) y el aire (6), debajo del cambio de agarre (20).
- `tools/re/ds2_anims.py` (`landing_clips`) hornea con el retarget de siempre, a r = 1 sobre AdditiveStand:
  `ds2_landing_stand`, `ds2_landing_crouch` (Stand → Crouch a 0.1 en los huesos del filtro), `ds2_landing_hips` (pelvis
  y todo lo que cuelga girados 25° y movidos (0, 0.1, −0.4) m; muslos con la pelvis; pantorrillas y pies quietos) y
  `ds2_landing_shake_slow` / `_fast` (el temblor con cada juego de α). Los α van horneados en esos dos clips; en el
  juego solo se mezclan entre sí. El pulso empieza en el tick en que se detecta el contacto.
- En el juego, por hueso de Minecraft: delta = pose · Stand⁻¹ en cuaterniones (R = Rz·Ry·Rx de los ángulos de PAL);
  agachado y HipsIK elevados a r, temblor = slerp(lento, rápido, peso del cambio); salida = temblor · hips · agachado ·
  locomoción, y slerp desde la locomoción con el peso de la capa. La posición del cuerpo suma r·(Δ agachado + Δ hips).
  La carga es la del jugador local (`InventoryStats.loadRatio`).
- Aproximaciones impuestas por Minecraft:
  - Los deltas se calculan sobre AdditiveStand, no sobre la pose de locomoción a la que se suman: cada hueso de
    Minecraft junta varios de DS2 (cuerpo = Spine1 en el mundo, brazo = hombro → muñeca), así que el aditivo local de
    DS2 no se puede aplicar hueso por hueso. Es exacto solo cuando la locomoción está cerca de Stand.
  - Piernas rígidas: el IK de DS2 dobla la rodilla para dejar el pie; en Minecraft la pierna apunta de la cadera nueva
    al pie viejo pero no se acorta, así que con la pelvis 0.4·r m más abajo el pie se hunde en el suelo (pasa igual en
    los demás clips con la pelvis baja). La bisagra de la rodilla se toma la de Stand (el interior del IK,
    `FUN_140c62640`, no está decodificado).
  - Al volver a aterrizar con r > 0 el pulso se reinicia y el clip del temblor sigue; con la capa saliendo, vuelve a
    entrar desde 0. Un cambio lento ↔ rápido que interrumpe otro sigue desde el peso que había. Ninguno de los dos está
    trazado en DS2.
- No portado: la carga de los otros jugadores (el cliente no la conoce; se toma t = 0).
