# Pactos (juramentos) de DS2 SotFS

Pedido del usuario (2026-11-10): los pactos son uno de los dos requisitos de 0.4.0 (el otro, las invasiones PvP).
**Solo investigación**; el diseño del port va aparte. Tres agentes de solo lectura (2026-11-10); archivos en
`D:\Reversing\DS2\out\covenant_core\`, `covenant_pvp\`, `covenant_pve\` (ESD volcados, decompilados, lotes, textos).

## 1. Sistema común
- **`VowParam`** (regulation, `VOW_PARAM`, slot `+0x7a0` del gestor; `FUN_1403590c0` / `FUN_140204190`): 10 filas de 8
  bytes, fila = id del pacto; `u16` `+0x00/+0x02/+0x04` = puntos para los rangos 1 / 2 / 3.
- **Estado** (objeto de estado del jugador `chr+0x490`):
  - `+0x1ad` s8 = **pacto activo** (0 = ninguno): **un solo pacto a la vez** (`FUN_14038bd80`; ESD `f130361`).
  - `+0x1ae + id` = ya se unió alguna vez.
  - `+0x1c2 + 2·id` u16 = **puntos de cada pacto** (se guardan los de todos).
  - `+0x1b8 + id` = rango, **recalculado** (`FUN_140204270`) = cuántos umbrales de VowParam alcanzan los puntos: **0..3**.
- **Ganar puntos**: `FUN_140203af0(chr, pacto, n)` (ESD `130382(pacto, n)`) **solo si `pacto` es el activo**; si el
  rango sube y llega a 3 → logro 27. Con n < 0 resta (piso 0) y **el rango puede bajar**. En red,
  `ChargeVowContributeJob` (`FUN_1401910a0`) suma o resta el byte con signo de `ResultEventParam +0x02` al pacto activo.
- **Ofrendas** (talk ESD): cada objeto ofrecido = **1 punto** (`131401(objeto, 1)` + `130382(pacto, 1)`); hay que
  pertenecer al pacto (1314); tope "No hay nada más que ofrecer" (1309) en rango 3 en algunos.
- **Recompensas de rango**: el NPC/altar entrega el lote del rango alcanzado una vez (una bandera por lote) y muestra
  1308. **Logros**: 18–26 al hablar con quien te une (bandera de ingreso), 27 al llegar a rango 3 en cualquiera.
- **Unirse** (`130381(id)` → `FUN_140202f10`): confirmación (1330+id−1; si ya hay otro pacto 1340+id−1 "¿abandonar y
  unirte?"), no con un fantasma presente (1311), da el sello/anillo del pacto (lote de ingreso) la primera vez.
- **Cambiar o abandonar** (`130381(0)` con el NPC de abandonar, 1302): pone la bandera **200900 + (pacto anterior − 1)**
  (los NPC reaccionan a ella). **No se pierden ni rango ni puntos** (ningún ESD resta; ni el comando los toca).
- **Textos**: nombre `common` 40000000+id ("Herederos del Sol"…); `MapEvent` 1300–1348 (entrar, ofrecer, errores);
  `npcmenu` 10105 / 10106 / 10110 / 90101 / 90202; no hay descripción por pacto en el menú (solo la de sus sellos,
  `DetailedExplanation` 40700000–40780000).

## 2. Los nueve pactos
| id | Pacto (es_mx) | Rangos | Se une | Puntos con | Recompensas r1 / r2 / r3 |
|---|---|---|---|---|---|
| 1 | Herederos del Sol | 10/20/30 | Altar de la luz solar (sin requisito) → Sello del sol | Medalla de luz solar ofrecida | Parma de luz solar / Espada del sol / Lanza de luz solar |
| 2 | Centinelas Azules | 50/150/500 | NPC (m10_31) → Sello del guardián | Símbolo de fidelidad (vencer espíritus oscuros como guardián; arena) | Escudo del árbol espiritual / Ira de los dioses / Luz solar abundante |
| 3 | Hermandad de Sangre | 50/150/500 | NPC (m10_23) → Emblema de sangre | Símbolo de rencor (vencer anfitriones / espíritus azules; arena) | Espada doble curva / Hoz creciente / Gran bola de fuego del caos |
| 4 | Senda Azul | 1/5/10 | NPC → Sello azul | +1 en red (condición no trazada) | Anillo mordisco de sangre / Silencio / Anillo de lágrima azul |
| 5 | Rey Rata | 10/20/35 | NPC (m10_33/34) → Emblema de la rata | Cola de rata ofrecida | Piedra suave pequeña ×10 / Piedra suave ×10 / Anillo de dragón dormido |
| 6 | Protector de la campana | 10/30/100 | NPC (m10_16/19) → Sello del protector | +1 en m10_19 y vencer intrusos (lote de titanita) | Losa de titanita / Arma oculta / set del Protector |
| 7 | Restos del dragón | 10/20/30 | dar un Huevo petrificado a Magerold, luego unirse → Sello del dragón antiguo + Ojo de dragón | Escama de dragón ofrecida | Piedra cabeza de dragón / Piedra torso de dragón / Gran espada del dragón negro |
| 8 | Compañía de Campeones | 10/25/50 | Piedra del Vencedor (doble confirmación) → Tablilla del campeón | Piedra maravillosa ofrecida | Gran arma mágica / Primer anillo del dragón / Sello del vencedor |
| 9 | Peregrinos de la Oscuridad | 1/2/3 | hablar con Grandahl en 3 lugares → Sello del abismo | progreso en el Abismo Oscuro | Alma resonante / Gran alma resonante / Clímax + set Xanthous |

## 3. Lo que cada pacto cambia en el juego
- **Compañía de Campeones (8)** — código: mientras es el pacto activo, **los enemigos te pegan ×1.5**
  (`FUN_14031f840`: `DamageAdjustParam` fila 0 `+0x148`), **tú pegas ×0.8** a los enemigos (`FUN_140381980`, `+0x144`;
  personajes de tipo 9–15) y **no se puede recibir cooperación** (`FUN_1402a1980` rechaza las señales blancas y doradas,
  igual que con pecado).
- **Herederos del Sol (1)**: la señal de invocación pasa a dorada (tipo de fantasma 1→3, 2→4). La medalla se gana al
  completar la tarea como fantasma invocado (`ResultEventParam` 303) o la sueltan algunos enemigos (1 %). Enseña el gesto
  "Alabado sea el sol" (sin pedir pacto).
- **Peregrinos de la Oscuridad (9)**: solo con este pacto se entra al **Abismo Oscuro** (m40_03) por tres entradas
  (Black Gulch, Shaded Woods, Castillo de Drangleic). Rango: +1 al entrar, +1 al hacer las tres salidas, +1 al vencer al
  Darklurker (`BossBattleParam` 4003000, 35000 almas).
- **Restos del dragón (7)**: el Ojo de dragón deja una "señal" que, al tocarla, trae un espíritu de dragón (PvP).
- **Pactos de PvP** (2, 3, 4, 5, 6): invasiones, guardianes, arenas y mapas propios (campanario, Rey Rata). **El
  emparejamiento lo decide el servidor de FromSoftware** (`Frpg2RequestMessage.*`), así que a quién invades o a qué
  mundo llega un centinela no está en los datos.
- **Pecado** (lo cazan los Centinelas; `CriminalAndDeceasedParam` fila 0): puntos `estado+0x1d7`, estado `+0x1d6`;
  pecador con ≥ 10, grave con ≥ 100 (el piso de vida de hueco baja a 5 %); +1 al matar ciertos personajes (tipo 3,
  NPC) y en red; absolución con un NPC (`talk_m10_14`, comando 130310, costo en almas).

## Diseño en BSE (decisiones del usuario, 2026-11-10)
- **Cambio final (2026-11-10, después de ver las estructuras): en BSE queda solo la Senda Azul (4)**, con su lugar de
  DS2 como tótem pequeño (la plataforma de Saulden en Majula). Los otros 5 pactos de invasiones y sus estructuras
  completas van a DarkTwo. Lo que sigue abajo es el diseño previo a ese cambio.
- **Senda Azul con defensores jugadores** (usuario, 2026-11-10): cuando invaden a un miembro, otro jugador conectado
  puede acudir como defensor (el rol de los Centinelas Azules, sin estructura ni NPC). Nada de enemigos de DS2 (cada
  modelo 3D lleva un mes).
- **Entran en BSE los 6 pactos de invasiones PvP**: Centinelas Azules (2), Hermandad de Sangre (3), Senda Azul (4),
  Protector de la campana (6), Rey Rata (5) y Restos del dragón (7). Herederos del Sol, Compañía de Campeones y
  Peregrinos de la Oscuridad van a DarkTwo u otros proyectos con Lua.
- **Un altar por pacto** (bloque) que hace lo del NPC de DS2: unirse, ofrecer, recompensas por rango, abandonar.
- **Los altares aparecen en estructuras del mundo** (no se fabrican).
- **Emparejamiento de invasiones** (no está en DS2: lo decidía el servidor de FromSoftware): aleatorio entre los
  jugadores conectados; un invasor puede invadir a cualquiera de **nivel de alma** mayor o igual, y a uno de nivel menor
  solo si la diferencia es de **10 niveles o menos**.
- **Una estructura por pacto**, hecha a partir de los **modelos 3D originales de DS2** del lugar del pacto (convertidos a
  bloques), con su altar; aparecen en la generación del mundo.
- **Zona** del Protector de la campana y del Rey Rata: **128 bloques** alrededor de su altar (quien entra puede ser
  invadido por miembros de ese pacto).
- **Recompensas de rango: se porta todo** (armas, armaduras, anillos). **Excepción a la regla de hechizos** (autorizada por
  el usuario, 2026-11-10): los 4 hechizos de recompensa (Ira de los dioses, Luz solar abundante, Gran bola de fuego del
  caos, Silencio) vienen en el jar de BSE, exportados de SpellParam, solo como recompensa de pacto.
- **Objetos de invasión como en DS2**: los orbes y símbolos salen de ganar invasiones/duelos (ResultEventParam) y del
  altar al unirse.
- Pendiente: el resto de las invasiones PvP (el otro requisito de 0.4.0).

## Estructuras de los altares (2026-11-10; `tools/re/covenant_structures.py`, `tools/re/ds2map/`)
- Lugares de unión (talk ESD → `130381(id)`; NPC por `GeneratorRegistParam` → `GeneratorParam` → `GeneratorLocation`, más
  el desplazamiento del mapa = posición de sus partes de colisión): Targray m10_31 (2), Titchy Gren m10_23 (3), Saulden
  m10_04 (4), Rey Rata m10_33 **y** m10_34 (5), Protector de la campana m10_16 **y** m10_19 (6), Magerold m10_19 (7).
  En cada lugar hay un objeto **o00_9050** (losa de piedra 1.10 × 1.78 × 0.32 m) donde está el NPC.
- Geometría: piezas de mapa y objetos del MSB a ≤ 30 m del NPC (`gamedata:/map/<m>/<m>.msb`, modelos FLVER en
  `model/map/<m>.mapbhd/.mapbdt`, objetos `model/obj/<o>.bnd`, texturas `model/map/t<m>.tpfbhd/.tpfbdt`).
- Conversión: 1 m = 1 bloque, cubo de 61 × 61 × 61 centrado en el NPC; muestreo de superficie; bloque = la textura que
  más lo cubre → su color medio → el bloque vanilla de color más cercano; sin paisaje lejano (NoLit, enkei), calcos,
  cielo ni recortes alfa; agua → agua, magma → lava; Z espejado (DS2 es de mano izquierda); aire solo donde hay
  geometría arriba y abajo (salas); el altar en los pies del NPC. 8 estructuras (jigsaw de una pieza), una por lugar;
  el conjunto de cada pacto elige uno de sus lugares (`random_spread`, spacing 64 / separation 24 chunks, diseño), con el
  piso del altar a la altura del terreno.
- Altares (`CovenantAltarBlock`, 6 bloques): forma de o00_9050 recortada a un bloque, irrompibles; nombre = el del pacto
  (`common` 40000000+id). Textura provisoria (ladrillo cincelado vanilla). **Todavía no hacen nada** (unirse, ofrecer,
  rangos, abandonar: pendiente).

## Sin confirmar
- El operador ESD 0x94 (`esd.py` lo muestra como `>`; la lógica pide `>=`): afecta a la lectura de umbrales en los ESD
  (no a VowParam).
- Qué disparan algunos +1 (Senda Azul en red, `event_m10_19` del pacto 6, `event_m40_03`), el formato exacto de
  ItemLotParam2 (sacado de los datos), los efectos de los sellos (índices de `1000[01]`/`[09]` sin identificar).
- Todo el emparejamiento en red, los tipos de fantasma 0..19 y los params `ChrNetworkPhantomParam`, `NetworkAreaParam`.
- Qué es la "tarea completada" que da la medalla, la forma de dragón, los comandos de campana y el disparo del Rey Rata.
- Si DS2 pone tope a los puntos (u16 sin límite visto).

## Invasiones PvP en BSE (diseño del usuario, 2026-11-10; no es de DS2: DS2 lo decide su servidor)
- **Emparejamiento**: aleatorio entre los jugadores conectados en Modo Souls; un invasor puede invadir a cualquiera de
  nivel de alma mayor o igual, y a uno menor solo si la diferencia es de 10 niveles o menos.
- **Invadir**: con el **Orbe del ojo rojo roto** (DS2 62060000, se gasta), que se consigue **como botín de cofres de
  estructuras, 10 %**. (En DS2 ganar una invasión da el Símbolo de rencor de la Hermandad, filas 407–410, que no está en
  BSE; el orbe lo dan los duelos de arena, filas x17. Ganar en BSE da solo la experiencia.)
- **Senda Azul, puntos y recompensas**: +1 al defensor que vence al invasor. Los anillos de rango 1 y 3 (Mordisco de
  sangre, Lágrima azul) son **encantamientos de armadura con los mismos efectos**; el hechizo Silencio (rango 2) viene en
  BSE (excepción autorizada).
- **Entrada**: el invasor se teletransporta cerca del anfitrión como espíritu oscuro; al terminar vuelve exactamente a
  donde estaba, con su inventario.
- **Fin**: muere uno de los dos; el anfitrión descansa en una hoguera (el invasor es expulsado); el invasor usa el
  **Cristal negro de separación** (DS2 62160000).
- **Muerte**: el perdedor pierde sus almas (experiencia) y **conserva sus objetos**; el ganador gana **el doble** de la
  experiencia que tenía el perdedor.
- **Víctimas**: cualquier jugador en Modo Souls.
- **Senda Azul**: cuando invaden a un miembro, **los demás miembros de la Senda Azul** reciben un aviso; el que acepta
  se teletransporta como espíritu azul y vuelve al terminar.

## Implementado (0.5.0; hecho el 2026-11-10)
- `SoulsCovenants` (estado: pacto activo, puntos por pacto, rango por VowParam), `SoulsCovenantAltar` + `CovenantAltarBlock`
  (tótem de la Senda Azul: recompensas pendientes al usarlo, menú Hacer juramento / Abandonar juramento con los textos
  de DS2, Sello azul al unirse), `SoulsPvp` (invasiones), `SoulsMultiplayItems` (Orbe del ojo rojo roto, Cristal negro
  de separación: rezo `a00_18_0080`, efecto a 2.333 s, diálogo de confirmación de DS2), `SoulsRings` (anillos como
  encantamientos de armadura: Sello azul vida ×1.03, Mordisco sangriento +0.15 / +0.30 resistencia al sangrado, Lágrima
  azul daño recibido ×0.65 con PS ≤ 30 %), el hechizo Silencio (`soulsmode:hush`, SpellParam 31230000; `statusFlag`
  bit 10 quita los sonidos suprimibles).
- Textos de DS2: `IngameSystem` (invasiones), `MapEvent` 1301–1343 (pacto), `npcMenu` 10105 / 10110 / 90202, `common`
  30000003 / 30000004 (diálogos), `talk/m10_04_00_00` 74101700 (recompensa).
