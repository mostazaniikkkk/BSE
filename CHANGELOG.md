# Changelog

## 0.5.0 (2026-11-18)

0.4.0 no se publicó por separado: todo lo que iba a ser 0.4.0 sale en 0.5.0 (decisión del usuario, 2026-11-18).

### Implementado
- **Corregido: los saqueadores disparaban una sola vez.** Su objetivo "mantener la distancia" tomaba el movimiento y
  apagaba el ataque de ballesta de Minecraft, que solo recarga a menos de 8 bloques. Ahora retroceden de lado como el
  esqueleto vanilla y siguen recargando y disparando.
- **Daño a zombis y esqueletos con paridad de DS2** (decisión del usuario): valen lo que un Hollow Soldier (320 PV,
  defensa física 317, absorciones 15/30/30/30). Una Longsword +0 los mata en 4 golpes, como al Hollow Soldier en DS2
  (antes 6); también el estallido de sangrado. Etiqueta `soulsmode:hollow_soldier`: zombi, husk, ahogado, aldeano
  zombi, esqueleto, stray y bogged. El resto de mobs queda como estaba.
- **Corregido: romper barcos era incómodo.** El golpe de DS2 solo daña a seres vivos; apuntando a un barco, una
  vagoneta, un marco, un cuadro o un cristal del End, el clic izquierdo vuelve a ser el golpe de Minecraft.
- **Corregido: los cofres, mesas y hornos se abrían sin apuntarles** (clic derecho cerca). Ahora solo apuntando; la
  hoguera sigue respondiendo de cerca.
- **Corregido: caída del servidor en combate** (2026-11-16 23:11, `ConcurrentModificationException` en
  `SoulsAttack.tick`): un golpe que iniciaba o cortaba la acción de otro jugador (parry, crítico, rotura de guardia,
  reacción) cambiaba la lista de ataques mientras se recorría. Ahora se recorre una copia.
- **Corregido: no se podía fijar a otros jugadores** en servidores que ponen a todos en un equipo para el nombre (SAM).
  Un jugador del mismo equipo solo cuenta como aliado si el equipo no permite fuego amigo.
- **Corregido: en agua baja volvía el moveset de survival.** Ahora solo nadando o con la cabeza bajo el agua se usan los
  ataques de Minecraft; con los pies en el agua siguen los del Modo Souls, un 10 % más lentos (factor 0.9 del
  usuario, `SoulsWater`).
- **Minerales incandescentes** (Modo Souls, diseño del usuario): los lingotes de hierro, oro y cobre y la chatarra de
  netherita salen del horno al rojo vivo y sin experiencia. En el inventario queman 1 de vida por segundo (salvo con
  Protección contra el fuego en la armadura); 5 segundos en el cursor o en el suelo y vuelven a ser mineral crudo;
  tirados en un caldero con agua se terminan y sueltan la experiencia (25 % de gastar un nivel de agua). Las tolvas
  sacan el lingote normal. Regla de juego `soulsIncandescentMinerals`.
- **Animación forzada** (pedido de DarkTwo): `/souls animate <jugadores> <animación> [segundos]` y `bse.animate` hacen que un
  jugador reproduzca un clip de c0001 (víctima de un agarre) quieto y sin poder actuar; no lo hace invulnerable, así que
  la función puede matarlo. Devuelve los ticks.
- **Para datapacks** (pedido de DarkTwo): etiqueta `soulsmode.souls_mode` en los jugadores del Modo Souls, condición de
  botín / predicado `soulsmode:souls_mode` y recetas solo del Modo Souls (`"type": "soulsmode:souls_mode"`).
- **Las armas de datapack leen todo lo que leen las armas de BSE** (`docs/datapacks.md` "Armas"): refuerzo e
  infusiones (antes quedaban en +0), material de refuerzo de cualquier ítem, postura doble, ataque con guardia, capa de
  guardia, estabilidad por slot, balas de los ataques y efecto propio del arma (almas en mano, hueco, cambio de fila en
  la mano izquierda).
- **Contraataque de DS2** (TAE 111600) en todas las armas: un jugador golpeado dentro de la ventana de su propio ataque
  recibe el daño × PDP +0x20 × WeaponTypeParam +0x6c del arma atacante (Longsword 1.1, Lanza 1.3, Pico 1.5).
- La estabilidad (poise) de cada golpe usa las filas de **su** arma; antes un clip compartido tomaba las de la primera
  arma que lo tuviera.
- Falta (backlog "Armas de DS2 de DarkTwo"): pecado, contraataque contra mobs / de enemigos, subtipo de estocada en el
  daño, efectos propios de la Llama de piromancia, el Arco corto y la Ballesta ligera.
- **El Último Gigante aparece cada 3 hogueras** activas que rompe un jugador (la 3.ª, la 6.ª…), contadas por jugador
  (diseño del usuario). Antes salía en cada una.
- **El Último Gigante suelta el Alma del último gigante** (`soulsmode:soul_of_the_last_giant`, 6000 almas), como su
  lote de DS2 (ItemLotParam2_Other 309600). El Alma de un gigante sigue existiendo, pero ya no la suelta nada.
- **Calavera seductora** (`soulsmode:alluring_skull`, DS2 60530000): se lanza con la animación de DS2 y vuela como su
  bala (10 m/s, 2° arriba, gravedad 6 m/s², 5 s). Al romperse, todos los **no-muertos** de Minecraft
  (`#minecraft:undead`, regla del usuario) que te tenían de objetivo te sueltan y van hacia ella durante 5 s, sin
  atacar. Sin radio, como en DS2. Receta (usuario): 4 huesos en cruz alrededor de cualquier alma → 4 calaveras.
- **Hueso de regreso** (`soulsmode:homeward_bone`, DS2 60350000): animación de rezo de DS2 (8 s, se puede caminar); a
  los 2.3 s se gasta y te lleva a la última hoguera donde descansaste o a la que viajaste, conservando la experiencia.
  No se usa en escaleras. Sin hoguera, o si ya no está o está apagada: al spawn del mundo (decisión del usuario).
  Receta (usuario): un hueso y cualquier alma.
- **Parry y ataques más reactivos en servidores**: el ataque desde quieto (ligero o fuerte de cualquier mano, incluido
  el parry) arranca en tu pantalla al apretar, y el servidor cuenta sus ventanas de DS2 desde ese momento (descuenta
  medio ping). Antes salía un ping completo tarde. Los tiempos del parry son los de DS2 (escudo mediano: activo de
  0.24 a 0.4 s).
- **Roll con sus i-frames de DS2 también en servidores**: el roll y el paso atrás arrancan en tu pantalla al apretar y
  el servidor cuenta su invulnerabilidad desde ese momento. Antes, con lag, buena parte de los i-frames pasaba antes de
  que el roll se viera. Los i-frames siguen siendo los de DS2 según la Agilidad (AGL 85: 0.27 s; AGL 100: 0.41 s).
- **Búfer de entrada de DS2**: un ataque o una esquiva apretados un poco antes de que termine la acción anterior ya no
  se pierden; salen en cuanto se puede, como en DS2 (se guarda uno solo, por 1.5 s, y solo en las ventanas en que DS2
  lo guarda).
- **Sonidos de DS2 al consumir almas y usar la Efigie humana**, y de la Calavera seductora al romperse y mientras atrae.
- **"HAS MUERTO"** (o "YOU DIED" con el juego en inglés): al morir en Modo Souls, a los 3 s aparece el cartel de DS2
  con su animación (30 fps), su franja negra y su sonido, el HUD se oculta, y después sale la pantalla de muerte de
  Minecraft. Textura, tiempos y sonido salen de DS2.
- **Diálogo de confirmación de DS2**: el Hueso de regreso pregunta "¿Volver a la última hoguera?" y las almas de gigante
  "¿Usar …?", con el cursor en NO. Con NO no se gasta nada.
- **Hueso de regreso**: 5 s sin recibir daño desde que se usa, como en DS2.
- **Vista previa al subir de nivel como en DS2**: la rejilla de 3 columnas con "actual ⇒ previsto" de PS, energía,
  peso, ranuras, velocidad de hechizo, ataque y bonos por stat, defensas y resistencias, agilidad, estabilidad, el ataque
  de las armas de cada mano y la defensa física (golpe / corte / empujón) con la armadura puesta.
- **Objetos brillantes del suelo (almas, resinas, efigie) también en cuevas y estructuras**, con la misma frecuencia
  que en la superficie (1 de cada 32 chunks; en estructuras, 1 de cada 32 chunks que ocupan). Solo en chunks nuevos.
- **Pacto de la Senda Azul**: su tótem (la plataforma de Saulden en Majula, convertida desde el modelo de DS2) aparece
  en el mundo con su altar. En el altar: Hacer juramento / Abandonar juramento con los textos de DS2, el Sello azul al
  unirse y las recompensas de rango (umbrales de DS2 1 / 5 / 10): rango 1 Anillo de mordisco sangriento, rango 2 el
  hechizo Silencio, rango 3 Anillo de lágrima de piedra azul. Los anillos son **encantamientos de armadura** con los
  efectos de DS2 (Sello azul: vida ×1.03; Mordisco sangriento: +15 % resistencia al sangrado; Lágrima azul: daño
  recibido ×0.65 con 30 % de vida o menos). Silencio quita los sonidos que alertan a los enemigos (caminar, correr,
  paso atrás, salto).
- **Invasiones PvP entre jugadores**: el **Orbe del ojo rojo roto** (cofres de estructuras, 10 %) invade a un jugador al
  azar en Modo Souls (de nivel mayor o igual, o hasta 10 niveles menor). El invasor aparece cerca como espíritu oscuro y
  vuelve a su lugar al terminar. Termina al morir uno de los dos, si el anfitrión descansa en una hoguera, o con el
  **Cristal negro de separación** (crafteo: fragmento de amatista + un alma; no se gasta). El que muere pierde su
  experiencia y conserva sus objetos; quien lo mató gana el doble. A un miembro de la Senda Azul invadido lo pueden
  defender los demás miembros (aviso con [Acudir]); el defensor que vence al invasor gana un punto de pacto.
- **Arco y ballesta: la flecha ahora es la flecha de Minecraft** (antes era un proyectil de DS2 sin modelo: no se veía,
  no quedaba clavada y no se podía recoger). Se ve en vuelo, queda clavada donde cae y se recoge como en Minecraft; al
  golpear hace el daño de DS2 de siempre. Comprobado en el juego con capturas (QA `-Dsoulsmode.qa=arrow`).
- **Mira del arco de DS2 al apuntar** (arco y ballesta): círculo con cuatro trazos en diagonal, en pixel art a partir
  de la mira de DS2 (`l01_18_bow_sights.flo`); reemplaza la cruz de Minecraft mientras se apunta.
- Etiqueta de ítems `soulsmode:souls` con las seis almas.
- Sprites en pixel art de la calavera y el hueso; el alma del último gigante usa el ícono de DS2 reducido (regla de
  las almas).

- **Scripts Lua de datapacks más seguros** (auditoría del sandbox): un script ya no puede esquivar el corte de 50 ms
  con `pcall` o una corrutina y colgar el servidor; un script que falla ya no queda roto hasta el siguiente `/reload`;
  `bse.emit` rechaza posiciones en chunks sin cargar y números inválidos; el log avisa si un hechizo o un enemigo
  nombra un script que no existe.
- **Petrificación y maldición de DS2** (docs/research/petrification.md): dos medidores del jugador en Modo Souls que
  llenan los datapacks (`petrify` / `curse` en la fila de daño de un enemigo, `/souls status`, `bse.status`) con la
  resistencia de DS2 (stats y armadura) y su bajada (45 s / 22 s). Petrificación llena: muerte instantánea, el cuerpo
  queda en piedra con una de las cuatro animaciones DieCurse de DS2 y la muerte suma 5 de Hueco. Maldición llena: +1 de
  Hueco y "¡Sufres maldición!". La hoguera y reaparecer los vacían. Barras de estado en el HUD con las piezas de DS2.
- **Estatuas petrificadas** (diseño del usuario): aldeanos, aldeanos zombi, pillagers y vendedores ambulantes en piedra,
  sueltos por el Overworld. La **Rama de antaño fragante** (pixel art; cofres de puestos de saqueadores, mazmorras,
  minas, portales en ruinas y templos) los libera: el jugador reza (clip de DS2), se gasta una rama y el mob vuelve,
  sin desaparecer nunca solo.
- **Objetivos de los enemigos de datapack** (`"targets"`, pedido de DarkTwo para el Caballero de Heide): qué jugadores
  persigue (todos, ninguno o los que cumplen un predicado del datapack) y qué mobs también (tipos o etiquetas, como
  zombis y esqueletos). Suelta al objetivo que deja de ser válido, y no golpea a los jugadores que no son objetivo.

### No portado / sin confirmar (en el backlog)
- Petrificación y estatuas: guardia, falta de stats en la armadura, anillos y objetos de resistencia, material de piedra
  y salida de la estatua de DS2, guía "Examinar", SFX de la maldición, medidores en mobs (lista completa en el backlog).
- Objetivos de enemigos: campo propio del mod (DS2 lo decide por facción en el ESD; no se trazó). Probado solo con
  la compilación y los tests, no en el juego.
- Scripts Lua: sin tope de memoria ni de balas por llamada, un script que agota su tiempo en cada llamada no se
  desactiva solo, y el presupuesto es tiempo de reloj. Los ganchos no se volvieron a probar en el juego tras el cambio.
- Calavera: qué hace en DS2 el enemigo con el señuelo (el mod: camina hacia él) y a quién vuelve a apuntar.
- Hueso: el diálogo "¿Volver a la última hoguera?", el fundido y la carga, el efecto `1000[03]` (sin identificar), y
  si DS2 lo permite en jefes o en línea.
- Las almas de gigante no tienen el diálogo "¿Usar…?" de DS2.

## 0.3.1 (2026-11-09)

### Implementado
- **Catalizadores como ítems**: Bastón de hechicero (`soulsmode:sorcerers_staff`), Campana sagrada del clérigo
  (`soulsmode:clerics_chime`) y Llama de piromancia (`soulsmode:pyromancy_flame`). Usan los datos de DS2 que BSE ya
  tenía (WeaponParam 3800000 / 4010000 / 5400000: moveset, lanzamiento, requisitos), el nombre de DS2, sprites en pixel
  art y su peso exacto (2.0 / 0.5 / 0.0). Sin hechizos de datapack no lanzan nada.
- **Recetas de los catalizadores** (diseño del usuario, no DS2), en columna en la mesa de crafteo:
  - Bastón de hechicero: pepita de hierro sobre un palo.
  - Campana sagrada del clérigo: lingote de cobre, lingote de cobre y una campana abajo.
  - Llama de piromancia: Llama de la adversidad sobre un cuero.
- **No portado**: la durabilidad de DS2 (30 / 30 / 50); queda en el backlog.
- **Sprites en pixel art** para la efigie humana, la Llama de la adversidad, las cuatro resinas, el talismán de Lloyd y la
  bomba incendiaria (antes eran el ícono de DS2 reducido). Regla del usuario: solo las almas usan el ícono de DS2
  reducido. Los dibuja `tools/item_sprites.py` y van en el repositorio.

## 0.3.0 (2026-11-09)

Primer parche después de la subida inicial a GitHub.

### Hogueras y arreglos (antes de la titanita)
- **Encender una hoguera** pide una espada de piedra en la mano, que se consume, más niveles de experiencia
  incrementales: cada hoguera encendida sube en uno el costo de la siguiente. Usar una hoguera ya encendida es
  gratis.
- **Las hogueras activas** muestran la espada clavada en el modelo y emiten partículas, para distinguirlas a simple
  vista.
- **Descansar ya no repara armas ni armaduras** (regla del usuario: Minecraft ya tiene el afilador y el yunque).
  Sigue recargando los hechizos y quitando el veneno y el fuego.
- **Arreglado** el cierre del servidor por un zombi en investigación sin paso (`SoulsPerception.Investigate`).
- El texto "Niveles de experiencia necesarios para encender esta hoguera" mostraba `{0}` en vez del número.

### Implementado
- **Palo: tier propio.** Sigue usando el moveset del Garrote (Club) de DS2, pero con el ataque ×0.5. También se ve
  así en la página de stats del inventario y aplica a los invasores con palo. Es una regla del usuario, no de DS2
  (`SoulsUpgrade.materialFactor`). Con stats 20, el ataque ligero a una mano baja de 4.53 a 2.27.
- **Azadas con moveset de combate.** Todas las azadas usan la Hoz Platinegra de DS2 (Silverblack Sickle, 2100000),
  elegida por el usuario. En los datos de DS2 esa arma se mueve como hacha (WeaponTypeParam 151, animaciones de
  hacha), así que no se agregaron animaciones nuevas. Requisitos: Fue 15 / Des 10 / Int 8 / Fe 8. Daño en +0: 80
  físico + 60 oscuro. Investigación en `docs/research/scythes.md`.
- **Las herramientas con moveset siguen cumpliendo su función** (`SoulsTools`):
  - Hacha, click derecho sobre un bloque: descorteza troncos, raspa el cobre y le quita la cera. En el aire o
    contra un mob sigue siendo el ataque fuerte.
  - Azada, click derecho: ara la tierra.
  - Azada, click izquierdo: cosecha lo que se rompe al instante (cultivos, flores, pasto) y rompe hojas o paja. En
    lo demás ataca.
  - Probado en el servidor QA con un cliente de desarrollo (modo `tools` de QaDriver, `tools/qa/tools_setup.js`):
    el hacha descorteza, la azada ara y cosecha trigo, y la espada sigue sin actuar como herramienta.

- **Ataque lento por no cumplir requisitos** (`SwingSpeed`, `docs/research/lack_of_stats.md`). Como en DS2, es el
  mismo movimiento más lento, sin animación distinta. Dentro de la ventana 151 de la pista `AttackSpeedControl`, el
  golpe corre a la velocidad propia del ataque (`WeaponAttackMotionParam +0x0c`) por `1 − penalización`. La
  penalización (fila 8 de `PlayerLackOfStatsParam`) va de −10 % justo bajo el requisito a −30 % con falta total de
  Fuerza o Destreza (Int y Fe no influyen), y suma hasta −20 % si no alcanza la estamina para el ataque. Aplica en el
  servidor (tiempos de golpe y bloqueos) y en la animación de todos los jugadores.
  - **También cambia los ataques que cumplen los requisitos.** Esa ventana ahora corre a la velocidad propia de cada
    ataque en DS2, entre ×0.5 y ×1.8 según el ataque; antes iba siempre a ×1.
  - **Sin portar:** la ventana 150 (inicio del golpe, fila 7), cuyo multiplicador no tiene escritor confirmado en
    DS2; hace falta leer memoria con el juego abierto. Tampoco lo usan los invasores NPC.
  - Probado en el servidor QA: con el Explorador, la espada de madera va a ×1.0 y el hacha de netherita a ×0.966
    (base 1.3 × 0.743).

- **Titanita y mejora de equipo** (`SoulsUpgrade`, `ReinforceRecipe`, `SoulsTitanite`, `SoulsInfusions`;
  `docs/research/titanite.md`). Diseño del usuario sobre los datos de DS2:
  - **Nivel guardado en el objeto** (componente `soulsmode:reinforcement`). Se sube de a uno en la **mesa de
    herrería**, sin plantilla: arma o armadura más la titanita que pide ese nivel. Reforzar a +N cuesta N niveles de
    experiencia (en DS2 son almas); la mesa muestra el costo como el yunque (verde, o rojo si no alcanza).
  - **Tiers de nivel máximo de las armas:** +5 madera, piedra, oro y el palo; +10 hierro, diamante y las armas sin
    material (maza, tridente, arco, ballesta, escudo); +15 netherita. (El oro y el palo estaban en +15; el usuario los
    bajó a +5 el 2026-11-09: con 15 niveles el palo escalaba más lento que la espada de madera.)
  - **Reescalado a DS2:** el máximo del tier equivale al máximo del arma en DS2 (+10, o +5 en las de titanita
    centelleante). Un nivel n del tier T ataca como DS2 n × máx / T; entre dos niveles de DS2 se interpolan la base
    (DS2 ya la interpola) y los coeficientes de escalado (DS2 tiene uno por nivel).
  - **Multiplicador por material** sobre el ataque (aprobado por el usuario): madera ×0.67, piedra ×0.83, hierro ×1,
    oro ×0.67, diamante ×1.17, netherita ×1.33, palo ×0.5, sin material ×1. Reemplaza la tabla vieja material → nivel.
  - **Material por nivel:** la ruta de DS2 (`ReinforceCostParam`) reescalada: el nivel n usa la fila del nivel DS2
    ⌈n × máx / T⌉, así que el último nivel del tier siempre pide la losa (o centelleante ×5).
  - **Armadura:** máximos y costos de DS2 (normal +10 con titanita; los sets del Rey y de Drangleic +5 con
    centelleante). La defensa física y las absorciones suben de su valor +0 al del máximo
    (`ArmorReinforceParam`). Aplica a la defensa DS2 de los jugadores en Modo Souls; la armadura vanilla no cambia.
  - **Escudo:** su estabilidad al bloquear sube con el nivel (`WRP +0x74 + 4·nivel`; Escudo de Drangleic 55 → 63).
  - **Yunque:** combinar dos objetos reforzados deja el nivel más alto, sin sumar ni promediar.
  - **Netherita:** pasar de diamante a netherita conserva el nivel.
  - **Página del arma** (click con la rueda del mouse): muestra `+N/+máx` y el ataque con nivel, material e
    infusión.
  - **Ítems:** fragmento, fragmento grande, trozo, losa y titanita centelleante, con nombres y descripciones de DS2 e
    íconos en pixel art propio (nunca íconos de DS2 reducidos). La centelleante es una tabla grande con brillo de
    encantamiento.
  - **Losa colocable** como losa de bloque (el ítem se ve como el bloque); 2 losas = **bloque de titanita**
    decorativo, y 1 bloque = 2 losas. Textura: la del bloque de netherita, más clara y con 4 grietas en zigzag (generada desde el jar
    de Minecraft en el build, paso `blocks`).
  - **Crafteo:** 9 → 1 hacia arriba (fragmento → grande → trozo → losa) y 1 → 9 hacia abajo; la centelleante no se
    craftea.
  - **Obtención:**
    - Drops al matarlos un jugador (probabilidades de DS2): murciélago, fragmento 20 %; phantom, grande 15 %; vex,
      trozo 2 %; devastador, losa 2 % y centelleante 5 %.
    - Cofres (25 % de los cofres, 1 a 3 unidades): mazmorras y minas, fragmento o grande; puestos de saqueadores y
      mansiones, fragmento a trozo; fortalezas y bastiones, trozo o losa; ciudades antiguas, losa o centelleante.
    - Herrero de armas y armero, siempre, por nivel (500 almas = 1 esmeralda): novato fragmento 2, aprendiz grande
      5, oficial trozo 12, experto losa 26. El comerciante errante vende 1 centelleante a 30.
  - **Infusiones como encantamientos** (raros, como Multidisparo; mesa de encantamientos, libros en cofres y
    bibliotecario; una por arma): Fuego, Magia, Electricidad, Oscuridad, Veneno, Hemorragia, Básico, Encantado y Mundano, con los datos
    de DS2 (fila de escalado propia, reparto de daño movido al elemento). Cada una solo en los objetos cuya arma DS2 la
    permite (`CustomAttrSpecParam`). Veneno aplica Veneno I 5 s, como la resina podrida (decisión del usuario).
    Mundano escala con el stat más bajo (PSPL `+0x8c` × WeaponTypeParam `+0x13a`, `docs/research/bleed.md` §8).
  - **Probado en el servidor QA** (modo `titanite` de QaDriver, `tools/qa/titanite_setup.js`): espada de madera a +1
    en la mesa de herrería (cobra 2 fragmentos y 1 nivel; ataca como DS2 +2); yunque +1 con +3 da +3; Infusión de
    fuego en la espada de hierro mueve el ataque a 149 físico + 109 fuego; herrero de armas experto ofrece la losa a
    26; comerciante errante, la centelleante a 30; 100 cofres de ciudad antigua y de mazmorra dan titanita en ~25 %;
    murciélagos y phantoms la sueltan solo si los mata un jugador; losa, losa doble y bloque se colocan y se ven.

- **Infusión Hemorragia con el sangrado de DS2** (`SoulsBleed`, `docs/research/bleed.md`). Cada golpe suma su
  ataque de sangrado (escala con Destreza y Fe) en puntos a un medidor de 100, por `1 − resistencia`. Al llegar a 100
  estalla: 200 de daño fijo de DS2 (5 de vida en el mod, sin defensa), partículas rojas (decisión del usuario) y 10 s
  de sangrado sin acumular; si es un jugador, su estamina máxima baja a ×0.75 esos 10 s. El medidor espera 0.5 s y
  baja: en 25 s para jugadores y en 120 s para mobs. Descansar en la hoguera lo vacía.
  - **Resistencia:** jugadores en Modo Souls, la de DS2 (armadura por nivel + Adaptabilidad/Fe). Mobs (tabla del
    usuario): zombis 15 % (Soldado hueco de DS2); esqueletos, golems, slimes, cubos de magma, blazes, ghasts, vex,
    shulkers, breezes, Wither y dragón inmunes; el resto 30 % (lo más común en DS2). Los enemigos de datapack, su
    `bleedResistance` (0.30 por defecto). Etiquetas `soulsmode:bleed_immune` y `soulsmode:bleed_resist_15`.
  - Probado en el servidor QA: espada de hierro con Hemorragia, Explorador, contra un zombi: 6.93 puntos por golpe
    (ataque 204 × 100/2505 × 0.85), el medidor baja 0.83 por segundo y estalla al llegar a 100. Test `InfusionTest`:
    espada larga con Destreza y Fe 20 = 662 de ataque de sangrado, como DS2.
  - **No portado:** la ralentización del estallido (sin confirmar en DS2); la reducción del sangrado al bloquear con
    escudo (el campo del escudo no está identificado: un golpe bloqueado no acumula); el sangrado que causan los
    enemigos al jugador.
- **La durabilidad sube con el nivel de titanita** (regla del usuario por equilibrio en Minecraft; **no es de
  DS2**): +10 % de la durabilidad base por nivel, en todo lo que se refuerza (armas, herramientas con moveset,
  escudo, arco, ballesta, tridente, palo y armadura). Un +15 de netherita queda en ×2.5; un +5 de madera, oro o palo, en ×1.5. Se
  calcula al leer la durabilidad (`ItemStackDurabilityMixin`), así que el paso a netherita y el yunque parten de la
  base del objeto.
  - Probado en el servidor QA: espada de madera +1, 59 → 65; espada de hierro +3 del yunque, 250 → 325.
- **El palo funciona con Irrompibilidad** (pedido del usuario). Ya la aceptaba con libro en el yunque; ahora también
  la mesa de encantamientos encanta el palo de Modo Souls como una herramienta de madera (encantabilidad 15,
  `StickEnchantabilityMixin`). Su desgaste usa el `hurtAndBreak` de vanilla, que aplica Irrompibilidad.
  - Probado en el servidor QA (modo `stick` de QaDriver): el yunque le pone Irrompibilidad III con libro, y la mesa
    de encantamientos ofrece Irrompibilidad y se la aplica.
  - **Sin medir en el juego:** cuánto menos se gasta. En la prueba, los golpes contra el zombi de prueba casi no
    conectaron, así que no se pudo contar el desgaste golpe a golpe.

### Investigado (sin implementar)
- **Titanita en DS2** (`docs/research/titanite.md`):
  - Rutas de mejora:
    - titanita normal hasta +10;
    - titanita centelleante hasta +5;
    - hueso de dragón petrificado hasta +5;
    - semillas de fuego para la llama de piromancia.
  - Costos en almas y materiales por nivel.
  - Infusiones.
  - Mejora de armadura.
  - Herreros, tiendas y drops.
  - Qué sube con el nivel: el daño y el escalado. La estabilidad al bloquear solo sube en los escudos, y la
    absorción al bloquear no cambia con el nivel.
- **Guadañas de DS2** (`docs/research/scythes.md`):
  - Son 8 guadañas reales, con solo 3 movesets distintos.
  - Hay dos con nombre engañoso: la Hoz Platinegra se mueve como hacha, y la "Guadaña" (3210000) es una alabarda.
- **Vista previa de daño con titanita** (`UpgradePreviewDump`, `CurrentDamageDump`). Compara el daño actual, donde
  el nivel sale del material, con la propuesta: el jugador sube el nivel DS2 y el material aplica un multiplicador.
  - La comparación se pidió con netherita contra garrote, palo contra espada de madera, y palas.
  - Encantamientos probados: Filo III y Castigo V.

### En análisis (decisiones o investigación pendiente)
- **Requisitos por material en picos y azadas.** Usan una sola arma DS2 para todos los materiales, así que subir
  de material no cambia sus requisitos. En espadas y hachas sí cambian, porque cada material es otra arma DS2.
- **Peso de la azada.** Por ahora conserva el peso de su receta. Falta decidir si toma el peso DS2 de la Hoz
  Platinegra.

### Descartado
- **La pala como garrote.** El usuario la descartó: la pala pegando se ve chistosa.
