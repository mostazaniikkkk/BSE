# Changelog

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
