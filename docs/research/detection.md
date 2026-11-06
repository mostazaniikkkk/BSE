# Percepción de los enemigos: vista y oído (2026-11-02)
Volcados: `scratchpad\detect\` (`rsearch.c` SearchEzStateCtrl 0x140430000–0x140440000, `r10d.c` gestor de sonido,
`d1..d27.c`, `dis_b790.txt`, `dis_bef0.txt`, `tae300.txt`, `lc.py`, `cp.py`, `tae300.py`).

## 1. Parámetros: `LogicComParam`, por la ESD superior
`ChrAi+0x2b8 = FUN_140422850(AiManager, idLógicaSuperior)` (`FUN_140425510`), cargado de `LogicComParam.param`.

| Campo | Lector | Significado | Hollow 102010 | Ogro 300010 / 30 / 40 |
|---|---|---|---|---|
| +0x04 f32 | `FUN_14043bef0` | alcance de vista con luz | 20 | 20 |
| +0x08 f32 | ″ | alcance de vista sin luz | 13 | 13 |
| +0x0c f32 | ″ | a esta distancia o menos se ve siempre (sin ángulo ni rayo) | 1.0 | 1.8 |
| +0x10 f32 | `FUN_14043b790` | semiángulo horizontal (rad) | 1.0472 (±60°) | igual |
| +0x14 f32 | ″ | semiángulo vertical (rad) | 0.7854 (±45°) | igual |
| +0x20 f32 | `FUN_140425820` → `FUN_14010df70` | escala del oído (d² se divide por ella) | 1.0 | 1.0 |
| +0x24 i32 | ″ | nivel mínimo de sonido percibido | 1 | 1 |
| +0x32 u8 | `FUN_14043bef0` | modo de la regla de luz | 0 | 0 |
| +0x43 u8 | `FUN_140425730` | regla "solo si se mueve" (lista sin identificar) | 1 | 1 |
| +0x50 f32 | SearchCtrl `+0x1f0` | segundos de memoria al perder al objetivo | 10 | 10 |

En las 347 filas: alcance 20 / 25 / 30 / 50 / 100…, semiángulo horizontal 60° (228 filas), 130° (43), 90°, 75°, 110°,
180°; oído 1.0 en 337; memoria 10 s (196) o 0 (145).

## 2. Vista
- Candidatos (SearchEzStateCtrl `+0xf8`, 5): lista rehecha cada 2–3 s; cada uno se revisa a < 20 m cada 0.15 s
  (0.05–0.15 s si ya se ve), entre 20 y 50 m cada 0.5 + (d − 20)/30 s, más allá 1.5 s.
- Prueba (`FUN_14043bef0`, f7310): d ≤ `+0x0c` → visto. Alcance = `+0x04`, o `+0x08` si el enemigo no está iluminado
  (`+0x32` = 0); si es < 27 y el objetivo lleva luz (`b8+0x6d4`), 27. Dentro del alcance, ángulos vertical y
  horizontal respecto del frente del enemigo. Con `FUN_14010d380` (estados sin identificar) la vista es de 360°.
- Línea de visión (`FUN_140428f50` → `FUN_140428fa0`): rayo del dummy 7 del enemigo al dummy 7 del objetivo (sin
  dummy, posición + 0.8 m); bloqueado si choca con algo que no sean los dos; se repite cada 1 s; más de 55 m cuenta como
  bloqueado.
- Luz del enemigo (`chr+0x380 +10`): puesta desde fuera, el área de culling del mapa con `+0x3c == 0`, o dentro del
  radio de luz (27 m) de un personaje con fuente de luz (la antorcha) y con rayo libre.
- "Visto" en la ESD = f7310(0,0) y no f7080. Con el objetivo ya adquirido se le sigue sin cono mientras haya rayo
  libre a menos de 55 m. Memoria (`FUN_140439c40`, cada 1 s): sin ver al objetivo cuenta hacia atrás `+0x50` (10 s); f7120
  = se olvidó.

## 3. Oído
- Gestor (`AiManager+0x1260`): 64 sonidos que duran 0.1 s. Nivel L = min(L, 10); radio r = L × factor / 10.
- Solo emiten el jugador anfitrión (tipo 0) y los tipos 18 / 19.
- Oír (f7320): para cada sonido con nivel ≥ `+0x24`: d'² = d²/escala; si d'² < r², percibido = (r² − d'²)/r² × L
  (redondeo de `FUN_140c356d8` sin confirmar) ≥ mínimo. **Sin rayo: las paredes no tapan el sonido.** Gana el más
  cercano; su posición queda en `ChrAi+0x3b0`.
- Emisión del jugador: eventos TAE 300 / 301 (`FUN_14016b280`). Evento 300: L = material del suelo (GroundMaterialParam
  `+0x10`: 0 casi siempre, 1–2 en algunos, 3 en uno) + ChrCommonParam 100 `+0x22c[idx]` = [0, 0, 2, 4, 10]; factor =
  AreaParam del mapa `+0x1c` (20 en la mayoría).

| Animación (c000100_pl.tae) | idx | L | Radio (factor 20) |
|---|---|---|---|
| Quieto / paso lateral | 0 | 0 | silencio |
| Caminar | 1 | 0 | **silencio** |
| Trotar, paso atrás, salto parado | 2 | 2 | **4 m** |
| Correr (dash), despegue del salto | 3 | 4 | **8 m** |
| Rodar, aterrizaje rodando, caída de escalera | 3 (sin anillo) | 4 | **8 m** |
| Rebote del arma en la pared | evento 301: L 8, factor 15 | 8 | **12 m** |

- Anillo del Dragón Durmiente: el bit 10 de `b8+0x4b8` descarta los sonidos con la marca "suprimible" (caminar,
  trotar, correr, paso atrás, salto), no rodar ni aterrizar. No hay un campo que escale el radio.

## 4. Alerta (ESD superiores)
- **Hollow Soldier** (ai102010, grupo 2147483634): pasa a alarma si **lo ve** (f7310 y no f7080), si **lo oye** con la
  orden 24 de la ESD inferior, si hay alguien a ≤ 3 m que se ve o se oye, si la región del generador tiene un hostil,
  o por alarma del grupo. Si **oye** y no está en alarma, tras 1.15–2.25 s **investiga**: gira hacia el sonido, va
  caminando hasta 3 m o 5–10 s, espera 2.2–3.3 s y vuelve. La alarma avisa a todo su grupo de colocación (sin límite de
  distancia).
- **Ogro** (ai300030, "reacción auditiva"): alarma si **oye y el jugador está a ≤ 10 m**, si **lo ve a ≤ 4 m**, por el
  grupo, o tras **25 s acumulados a menos de 10 m**.

## Sin establecer
- El redondeo del nivel percibido; `FUN_14010c7f0` (filtro del emisor); f5201, f5705, el operador de f5700(1), las
  órdenes f5900(3) de la ESD inferior; los estados de vista de 360°; la lista de la regla "solo si se mueve"; el flag de
  oscuridad del área (+0x3c); quién escribe la luz de la antorcha (`b8+0x6d4`); `+0x18` / `+0x1c`; el radio de la
  búsqueda de candidatos; otros emisores de sonido; lo que hace la ESD inferior al perder al objetivo; cómo elige el
  objetivo cmd 7000.

## En el mod (`SoulsPerception`, `ds2/Perception`, 2026-11-03; pendiente de QA)
Detrás de `SoulsPerception.ENABLED` (encendido para probar el game feel). Solo frente a jugadores en Modo Souls; contra
los demás todo sigue vanilla.

### Qué mobs
- **Como el Hollow (102010)**: todo `Monster` salvo los de abajo: zombis y variantes (husk, ahogado, aldeano zombi,
  piglin zombificado), esqueletos (esqueleto, stray, wither skeleton, bogged), arañas y arañas de cueva, creeper,
  bruja, illagers (vindicator, pillager, evoker, illusioner), devastador, endermite, silverfish, gigante, piglin,
  piglin bruto, zoglin, breeze.
- **Como el ogro (300030)**: el golem de hierro.
- **Fuera** (vanilla): enderman (su regla de la mirada), warden (sus sentidos), y los voladores o solo de agua: blaze,
  vex, guardianes, wither. Phantom, ghast, slimes, shulker y hoglin no son `Monster`. Los enemigos propios del mod
  (`SoulsEnemy`, `SoulsInvader`) tienen su IA y no entran.
- Lo que vuelve hostil a un mob sigue vanilla (la araña solo de noche, el piglin sin oro, el golem enojado o
  defendiendo la aldea, el piglin zombificado enojado); la percepción solo decide **si puede tomarlo de objetivo**.

### Decisiones del usuario (2026-11-02)
- Los valores de 102010 para los monstruos y de 300030 + las reglas auditivas del ogro para el golem.
- Luz: el mob está iluminado con nivel de luz ≥ 8 en sus pies (`getMaxLocalRawBrightness`: bloque o cielo con la
  hora); entonces 20 m, si no 13 m. Reglas de DS2 que se mantienen: sin luz, un objetivo con la antorcha
  (`SoulsTorch.isHeld`) se ve a 27 m; un mob a ≤ 27 m de un jugador con la antorcha y con rayo libre cuenta como
  iluminado (y entonces usa 20 m, como en `FUN_14043bef0`).
- Sin alarma de grupo: cada mob reacciona a lo que ve y oye.

### Cómo se engancha
- `TargetingConditionsMixin`: un mob que percibe solo encuentra a un jugador en Modo Souls (para atacarlo o para
  mirarlo, `LookAtPlayerGoal`) si ya lo **adquirió**. Para atacar, la percepción reemplaza el alcance, la línea de
  visión y la visibilidad de vanilla; el selector, `canAttack` y las alianzas siguen. Así funcionan igual los mobs con
  objetivos (`NearestAttackableTargetGoal`) y los de cerebro (sensores de piglin / zoglin / breeze).
- `TargetGoalMixin`: el objetivo se mantiene mientras el mob lo recuerda (en vez del alcance de seguimiento y los 3 s
  sin verlo de vanilla). `DefendVillageTargetGoalMixin`: el golem no defiende la aldea de quien no percibió.
- Ser golpeado por el jugador lo adquiere (el `HurtByTargetGoal` de vanilla sigue; f5201 de DS2 sin establecer).
  Un objetivo puesto fuera de esas comprobaciones (avisos de vanilla: zombis y piglins que alertan a los suyos al
  ser golpeados) también cuenta como adquirido.
- `MobReactionMixin`: cada paso de IA del mob corre su percepción; al construirlo agrega la investigación.

### Vista
- Candidatos: los 5 jugadores en Modo Souls más cercanos a ≤ 55 m, lista rehecha cada 2–3 s (aleatorio). El radio de
  búsqueda de DS2 no está establecido; más allá de 55 m nada se puede ver, así que no cambia el resultado.
- Cada candidato se revisa con los tiempos de 14043ea20 (constantes en `c1.txt`): < 20 m 0.15 s; 20–50 m
  0.5 + (d − 20)/30; más allá 1.5 s. Visto en la revisión anterior: aleatorio 0.05–0.15 s, entre 0.45·f + 0.05 y
  f + 0.5 (f = (d − 20)/30; `DAT_1410edfa4` = 0.45, `DAT_1410b0cf8` = 0.05, `DAT_1410edfa0` = 0.1), 0.5–1.5 s. DS2
  pide además el flag 8 del candidato, sin identificar: se toma "visto".
- Prueba: d ≤ 1.0 m (golem 1.8 m) → visto; si no, d ≤ alcance, dentro de ±60° horizontal del frente del cuerpo
  (`yBodyRot`) y ±45° de elevación, y rayo libre. Distancia y ángulos entre las posiciones (pies); el rayo, de ojos a
  ojos (el dummy 7 no existe en Minecraft), contra la colisión de los bloques (las entidades no tapan), repetido cada
  1 s; más de 55 m cuenta como bloqueado.
- Adquirido (Hollow: visto; golem: visto a ≤ 4 m): se lo sigue sin cono mientras esté a ≤ 1.0 / 1.8 m o con rayo libre
  a ≤ 55 m; cada 1 s, sin eso, cuenta; a los 10 s lo olvida y suelta el objetivo (también el `ATTACK_TARGET` del
  cerebro).

### Oído
- Sonidos del jugador (64 como máximo, viven 0.1 s, en su posición), con el tiempo del evento 300/301 de cada clip de
  `c000100_pl.tae` (`tae300.txt`):

| Acción (Minecraft) | Clip de DS2 | Tiempo(s) del evento | L | Radio |
|---|---|---|---|---|
| Moverse normal | a00_04_0010 Jog, ciclo 0.7667 s | 0.367, 0.733 | 2 | 4 m |
| Sprint | a00_05_0010 Dash, ciclo 0.6 s | 0.267, 0.467 | 4 | 8 m |
| Fijado: adelante / atrás / izq. / der. | a00_07_0011..0014, ciclos 0.7667 / 0.7333 | 0.4 / 0.267 / 0.367 / 0.333 | 2 | 4 m |
| Agachado, fijado agachado, quieto | Walk / SideWalk / Idle | — | 0 | silencio |
| Rodar (normal / cansado / fallido) | a00_12_0020 / 0030 / 0040 | 0.233 / 0.333 / 0.333 (reloj de la animación) | 4 | 8 m |
| Paso atrás (normal / cansado / fallido) | a00_11_0011 / 0051 / 0081 | 0.333 / 0.667 / 0.5 | 2 | 4 m |
| Salto | a00_13_0020 JumpTakeoff | 0 | 4 | 8 m |
| Aterrizaje rodando | a00_14_0020 RollLanding | 0.233 | 4 | 8 m |
| Rebote del arma en la pared | a20 / a22 / a25 / a29_0x_0100 / 0110 (evento 301) | 0 | 8 (factor 15) | 12 m |

- La locomoción la elige el servidor como el cliente (`Locomotion`): sprint = Dash, agachado = Walk, si no Jog; fijado,
  el clip lateral de la dirección dominante. Solo en el suelo, moviéndose, fuera de una esquiva, una acción
  (ataque / guardia / ítem) y del RollLanding (hasta 0.5667 s). El ciclo empieza al empezar ese paso.
- Salto y RollLanding: el servidor corre las mismas reglas de `AirClient` (despega subiendo; aterriza desde 0.4333 s
  del despegue o desde la caída; rueda con ≥ 4.0 m/s tras un salto, o caída ≥ 5 m y ≥ 4.5 m/s). El salto no suena
  durante un ataque en salto (tiene su propio clip).
- Oír: el sonido más cercano con nivel ≥ 1 y (r² − d²)/r² × L ≥ 1 (escala 1.0). **Redondeo de `FUN_140c356d8` sin
  establecer: comparación continua.** Las paredes no tapan. Así, el trote se oye hasta 2.83 m, el sprint / rodar /
  saltar hasta 6.93 m y el rebote hasta 11.2 m.
- **Hollow**: oír a un jugador que está a ≤ 3 m lo adquiere. Si no, sin objetivo, tras 1.15–2.25 s **investiga**: va
  hacia la posición del sonido (último oído, `ChrAi+0x3b0`) hasta 3 m o 5–10 s, espera 2.2–3.3 s y vuelve a donde
  estaba (hasta 10 s). Verlo durante eso lo adquiere y corta la investigación. Lo que oiga mientras investiga no la
  reinicia.
- **Golem (ogro)**: alarma si oye con el jugador a ≤ 10 m, si lo ve a ≤ 4 m, o tras 25 s acumulados a ≤ 10 m (por
  jugador; se ponen a 0 al adquirirlo). Sin investigación.

### Aproximaciones (mías, a revisar en QA)
- El paso de la locomoción no está sincronizado con la animación del cliente: el ciclo empieza cuando el servidor ve
  que cambia el paso. "Moviéndose" = se movió en los últimos dos ticks (los movimientos llegan como paquetes).
- El giro hacia el sonido es el de la navegación de Minecraft, y caminar es la velocidad vanilla del mob (factor 1.0);
  la velocidad de caminar de DS2 no está portada. La vuelta termina cuando la navegación llega.
- Lo que oiga mientras investiga no la reinicia, y los 25 s del golem se ponen a 0 al adquirir: DS2 no lo establece.
- Los mobs de cerebro (piglin, piglin bruto, zoglin, breeze) ven y oyen para adquirir, pero no investigan.
- Las reducciones de visibilidad de vanilla (invisibilidad, agacharse, cabezas de mob) no se aplican: la vista es la
  de DS2. Contra un jugador en Modo Souls la poción de invisibilidad deja de servir con estos mobs.
- Perdido el objetivo, el mob queda como lo deje vanilla (un zombi sin rayo deja de recalcular el camino).
- Varios jugadores: cada mob puede tener adquiridos varios a la vez (DS2 tiene un objetivo); vanilla elige el más
  cercano.

### No portado
- Material del suelo (GroundMaterialParam `+0x10`, suma niveles): sin equivalencia en Minecraft, se toma 0.
- Anillo del Dragón Durmiente (no existe en el mod).
- La regla "solo si se mueve" (`+0x43`, lista sin identificar), la vista de 360° (`FUN_14010d380`), el flag de
  oscuridad del área (`+0x3c`), la alarma por grupo de colocación y por la región del generador, la orden 24 de la ESD
  inferior, f5201 (lo adquiere el golpe vanilla), f7080, qué hace la ESD inferior al perder al objetivo.
- Los eventos 300 de las animaciones de ataque y de ítems (están en los TAE de armas, no escaneados).

- **Invisibilidad** (regla del usuario, 2026-11-03): la poción de invisibilidad sigue funcionando. El alcance de vista se
  multiplica por el factor de Minecraft (0.7 × la parte del cuerpo cubierta por armadura, mínimo 0.1): sin armadura,
  20 m pasan a 1.4 m. Solo afecta a la detección; un mob que ya te tiene te sigue (como en Minecraft). Agacharse y las
  cabezas de mob no cambian nada (manda la vista de DS2).
