# Llama de la adversidad (Bonfire Ascetic) — diseño del usuario

**Esta mecánica no es un port de DS2.** Es un diseño del usuario (2026-11-03) inspirado en el Bonfire Ascetic de DS2.
De DS2 salen solo el objeto (nombre, ícono, descripción) y los textos del menú de la hoguera; todos los números son
decisiones del usuario o propuestas por defecto que el usuario puede ajustar. Todos están en
`dev.soulsmode.ascetic.AsceticRules` (con el interruptor `ENABLED`, hoy `true`).

## Lo que sale de DS2
- **ItemParam 60527000**: `+0x00` ícono 60527000, `+0x04` SpEffect **0** (no tiene efecto de uso: en DS2 solo se quema en
  la hoguera). Lectura: `tools/re/params.py` (`load('ItemParam')`).
- **Ícono**: `gamedata:/menu/tex/icon/IC_0060527000.tpf` (8373 bytes), mismo método que la efigie
  ([hollowing.md](hollowing.md)) y las almas ([soul_items.md](soul_items.md)): `D:\Reversing\DS2\tools\extract_bdt.py`
  → TPF de una textura `IT_IC_0060527000` → DDS **DXT5 64×128** → PNG recortado al alfa
  (`out\souls_items\tpf2png.py`): `D:\Reversing\DS2\out\ascetic\bonfire_ascetic_icon.png` (64×82, recorte (0,22)-(64,104));
  completo y DDS en `out\ascetic\icon\`. El 16×16 del mod (`textures/item/bonfire_ascetic.png`) es ese PNG reducido con
  Lanczos y centrado (12×16), sin retoque a mano.
- **Textos** (`itemname` / `SimpleExplanation` / `DetailedExplanation`, id 60527000):
  - en: "Bonfire Ascetic" / "Burn in bonfire to strengthen nearby foes".
  - NeutralSpanish (= es_mx) y Spanish: "**Ascua** de la adversidad" / "Quémala en la hoguera y fortalece a los enemigos."
  - Largo (es): "Lánzala a una hoguera para fortalecer a los enemigos cercanos. / Cuando el ascua de la adversidad sea
    devorada por las llamas, sus efectos no se podrán invertir. Prepárate antes de usar esta peligrosa ascua. / El fuego
    está conectado con la maldición. A medida que las llamas se hacen más fuertes, se fortalece también la maldición."
  - **El nombre del mod es "Llama de la adversidad" por decisión del usuario** (DS2 en español dice "Ascua").
- **Menú de la hoguera** (`gamedata:/menu/Text/NeutralSpanish/bofire.fmg`, extraído en el scratch de la sesión):
  10104 "Quemar", 80100 "Intensidad de hoguera", **90100 "Quemar %s.\n¿Aceptar?"** (usado en la confirmación),
  **90200 "No tienes ningún objeto para quemar."** (tooltip del botón deshabilitado), **99620 "Lo hecho, hecho está."**
  (mensaje al quemar). En inglés: 10104 "Burn", 90100 "Burn %s.\nOkay?", 90200 "You have no items to burn",
  99620 "What is done cannot be undone".
- En DS2 quemarla sube la intensidad de la hoguera (`FUN_14017ffa0`, tope `FUN_14017f280` ≤ 99, ver
  [bonfire.md](bonfire.md)); lo que hace la intensidad (NG+ local) **no se porta**: aquí el efecto es el del usuario.

## Decisiones del usuario
| Qué | Valor | Origen |
|---|---|---|
| Obtención | como la efigie: cofres de templo del desierto, templo de la jungla, fortaleza del Nether (20 % por cofre, `SoulsHollow.LOOT_CHANCE`); comerciante errante 2 esmeraldas, 4 usos; **además** mazmorras (`chests/simple_dungeon`) y bóvedas de las cámaras de prueba (`chests/trial_chambers/reward` y `reward_ominous`, 20 % por tirada) | usuario (la tabla de cámaras elegida: las de recompensa de bóveda, no pasillos ni suministros) |
| Uso | en una hoguera activada, descansando, botón "Quemar Llama de la adversidad" del menú; gasta una | usuario |
| Efecto 1 | los chunks a ≤ 8 del chunk de la hoguera (17 × 17) vuelven a como los generó el mundo, salvo el bloque de la fogata (y su activación / imagen / nombre) | usuario |
| Efecto 2 | +1 ciclo a la zona de esa hoguera (mismo radio), máximo 5; zonas superpuestas: el ciclo más alto | usuario |
| Entidades del área | se quitan las que no son jugadores; se copian las que trae la generación (animales iniciales, aldeanos de estructuras); el spawn natural repuebla | usuario (pregunta abierta, ver abajo) |

## Efectos de la zona (por ciclo c)
| Efecto | Valor | Origen |
|---|---|---|
| Vida máxima de mobs hostiles (`Monster`) | +20 % × c | propuesta por defecto del usuario |
| Daño que hacen | +20 % × c (todo daño cuya fuente es el mob: golpe, flechas, explosión) | propuesta por defecto |
| Velocidad | +10 % × c ("mejor IA") | propuesta por defecto |
| Rango de seguimiento | +25 % × c ("mejor IA") | propuesta por defecto |
| Botín extra | +1 tirada de su propia tabla de botín por ciclo | propuesta por defecto |
| Vindicadores | de día, ~1 cada 2 min por zona mientras haya un jugador dentro, a 16–32 bloques del jugador, sobre suelo sólido, fuera de su vista si se puede, luz indiferente | usuario (distancia 16–32: elección de implementación) |
| Evocadores | desde c ≥ 2, de día, con su propio temporizador de ~2 min por zona (mismas reglas de aparición que los vindicadores) | usuario (2026-11-03); el intervalo es propuesta por defecto |
| Vetas de mineral al resetear | cada rasgo de mineral del chunk (`UNDERGROUND_ORES` con un bloque `c:ores`) se coloca 0,25 × c veces más en promedio, con sus propias reglas de colocación (cantidad, altura, bioma) | usuario (2026-11-03); 0,25 por ciclo es propuesta por defecto |
| Devastadores | desde c ≥ 4 (más de 3 quemas), 2 % por intento de vindicador, **además** del vindicador | usuario |
| Generadores (spawners) | aceptan lugares con luz ≤ 3 × c (c = 5 → 15 = cualquier luz) | usuario fijó "a 5 siempre"; "3 por ciclo" es la propuesta |
| Phantoms | desde c ≥ 1 se ignora el requisito de insomnio (vanilla: `TIME_SINCE_REST` ≥ 72000), el resto de las reglas vanilla siguen | usuario |
| Almas al matar (jugador) | probabilidad 5 % × c; tabla por ciclo (no muerto perdido / grande / soldado anónimo / grande): c1 100/0/0/0, c2 60/40/0/0, c3 30/40/30/0, c4 10/30/40/20, c5 0/20/40/40 | propuesta por defecto del usuario |

## Implementación
- `item/BonfireAsceticItem` (`soulsmode:bonfire_ascetic`, pila de 64 como la efigie, en la pestaña de comida y
  bebida junto a la efigie); botín y comerciante en `SoulsAscetic.init` / `WanderingTraderMixin`.
- Menú: `BonfireScreen` con "Quemar Llama de la adversidad" (activo si el jugador tiene una), confirmación con el texto
  90100; payload `BonfirePayloads.BurnAscetic`. El servidor (`SoulsAscetic.onBurnRequest`) valida que el jugador esté
  descansando en esa hoguera con el menú abierto (`SoulsBonfire.menuBonfire`) y que tenga la llama, y gasta una.
  Una sola regeneración a la vez en el servidor (si hay otra en curso: "Las llamas aún consumen otro lugar").
- API para servidores (usuario 2026-11-03): `dev.soulsmode.api.AsceticEvents.ALLOW_CHUNK_RESET`; un mod de
  protección devuelve `false` para un chunk y el reset no toca sus bloques, cofres ni entidades (ni le agrega vetas).
- Zonas: `AsceticZones` (SavedData `soulsmode_ascetic_zones` del overworld): dimensión, chunk de la hoguera, ciclo.
- Mobs: cada segundo (y al entrar al mundo, `ENTITY_LOAD`) cada `Monster` recibe modificadores de atributo
  (`ADD_MULTIPLIED_BASE`) según el ciclo de **donde está ahora**; al cambiar la vida máxima se conserva la proporción de
  vida. Daño: `AsceticLivingEntityMixin` multiplica en `LivingEntity.hurt` si `source.getEntity()` es un `Monster` en
  zona. Botín y almas: mismo mixin, al final de `dropFromLootTable` (solo cuando vanilla suelta botín); las almas solo si
  lo mató un jugador (`lastHurtByPlayer` reciente).
- Generadores: `AsceticSpawnerMixin` marca el límite de luz mientras un `BaseSpawner` hace su tick;
  `AsceticMonsterMixin` hace pasar `Monster.isDarkEnoughToSpawn` y `Monster.getWalkTargetValue` (los dos chequeos de luz
  que recorre un monstruo de generador) si la luz (`getMaxLocalRawBrightness`) es ≤ el límite. Por encima, vanilla
  decide. Los generadores con `custom_spawn_rules` y los generadores de prueba no cambian.
- Phantoms: `AsceticPhantomSpawnerMixin` cambia el valor de `Mth.clamp(TIME_SINCE_REST, 1, MAX)` por `Integer.MAX_VALUE`
  para jugadores en zona, así `nextInt(...) >= 72000` pasa (probabilidad 1 − 72000 / 2³¹).
- Vindicadores / devastadores: `SoulsAscetic.tickVindicators`, `spawnNear` (24 intentos; prefiere un lugar fuera del
  cono de 60° de la vista o tapado; `finalizeSpawn` con `MobSpawnType.EVENT`).
- Comandos de QA (op): `/souls ascetic burn <x> <y> <z>` (activa la fogata si hace falta y quema) y
  `/souls ascetic info <x> <z>` (ciclo y multiplicadores ahí).

### Regeneración (`ChunkRegenerator`)
Mismo enfoque que el `//regen` de WorldEdit en Fabric: se crea un `ServerLevel` temporal sobre un guardado vacío en una
carpeta temporal, con el mismo tipo de dimensión, el mismo generador y la misma semilla; un ticket de región en el
centro (nivel 33 − 9) hace que el área quede "block ticking" ahí, así que vanilla genera todo con sus vecinos
(estructuras, decoraciones que cruzan bordes de chunk, luz, mobs iniciales, post-proceso) en los hilos de worldgen. El
hilo del servidor solo atiende las tareas de hilo principal del nivel temporal, con 20 ms por tick como máximo. Luego,
chunk a chunk (20 ms por tick), cada bloque distinto se copia con `setBlock` (flags: clientes, sin actualizar formas
vecinas, sin soltar objetos): luz, mapas de altura, POI y paquetes a los clientes salen de ahí; los contenedores viejos
se vacían antes (como `/setblock`) para que no suelten nada; las entidades de bloque se reemplazan por las generadas
(cofres con su tabla de botín). Al final se quitan las entidades no jugador del área (salvo vehículos con un jugador) y
se copian las generadas. Durante todo el proceso el área real se mantiene cargada (FULL, sin tick) para que sus
entidades guardadas estén cargadas antes de reemplazarlas. Un jugador que queda dentro de bloques sube al primer hueco
libre. El nivel temporal se cierra sin guardar sus chunks y la carpeta se borra.

## QA en el servidor QA (2026-11-03, por RCON, sin jugadores)
Hoguera en (200, 71, 200), mundo `qa`. Antes de quemar: bloque de diamante 5×5×5, un hueco de 4×4×4, un cofre con
5 diamantes, oro a (300, 64, 300) (dentro, chunk 18,18), esmeralda a (360, ·, 200) (chunk 22, fuera), un cerdo.

| Prueba | Resultado |
|---|---|
| Fogata sigue | sí (tras 6 quemas) |
| Diamantes, oro, cofre colocado | desaparecen; ningún objeto soltado en el área (los 20 objetos que había eran viejos, cerca de (0, 0)) |
| Hueco | vuelve a estar lleno |
| Esmeralda fuera del radio | sigue |
| Entidades | 1ª quema: 1 quitada (cerdo), 27 generadas añadidas; con el arreglo de carga de entidades, las quemas siguientes quitan las 27 (+ zombis de prueba) y añaden 27 (sin duplicados) |
| Ciclo | 1, 2, 3, 4, 5, 5 (tope); segunda quema mientras corre otra: rechazada |
| `/souls ascetic info` | ciclo 1 en x = 335, 0 en x = 336 (borde del chunk 20) |
| Mobs en ciclo 3 | zombi: vida 20 → 32 (llena), velocidad 0.23 → 0.299, seguimiento 35 → 61.25; esqueleto 20 → 32; husk fuera: 20 |
| Daño en ciclo 3 | `/damage` 5 "por" un zombi en zona a un gólem: 8; por uno fuera: 5 |
| Botín extra en ciclo 3 | 10 zombis matados con `/kill`: 43 carnes podridas (vanilla ≈ 10; 4 tiradas ≈ 40) |
| Cofres / bóvedas | 50 tiradas de cada tabla: mazmorra 8, recompensa de bóveda 11, ominosa 7, templo del desierto 7 (≈ 20 %) |
| Comerciante errante | última oferta: Llama de la adversidad por 2 esmeraldas; la anterior, la efigie |
| Carpetas temporales | ninguna queda en `%TEMP%` |
| Errores en el log | ninguno |

Tiempos (17 × 17 chunks, servidor de 2 GB en esta laptop):
| Quema | Generación (asíncrona) | Copia | Hilo del servidor (total / peor tick) | Bloques cambiados |
|---|---|---|---|---|
| 1ª (área con cambios a mano y chunks sin cargar) | 28.0 s | 7.6 s | 8.7 s en 681 ticks / — | 18 915 |
| 3ª | 36.5 s | 0.8 s | 2.1 s en 744 ticks / 78 ms | 3 467 |
| 4ª–6ª | 27.9–30.9 s | 0.56–0.83 s | 1.5–1.9 s en ~570–630 ticks / 30–57 ms (abrir el nivel temporal) | 3 400–4 300 |

El peor tick es el de abrir el nivel temporal (20–57 ms); el resto respeta los 20 ms por tick. En total cada quema
tarda ~30–40 s en terminar.

**Sin probar** (necesitan un jugador conectado; quedan para el QA en el juego): botón y confirmación del menú, gasto
del objeto, vindicadores / devastadores, generadores con luz, phantoms, almas al matar, desatascar a un jugador, que el
cliente reciba bien los cambios. La zona de ciclo 5 en (200, 71, 200) del mundo `qa` queda para eso.

## Limitaciones
- **Límite de vanilla**: dos generaciones del mismo lugar no son idénticas bloque a bloque: las decoraciones que se pisan
  entre chunks vecinos (manchas de granito / diorita / andesita, menas, kelp, discos de grava) dependen del orden en que
  los hilos decoran. Medido: ~3 500 bloques de diferencia entre dos regeneraciones seguidas del mismo 17 × 17.
- Cofres regenerados traen su tabla de botín con una semilla nueva (no el mismo botín de la primera vez).
- Biomas cambiados con `/fillbiome` no se restauran (solo bloques, entidades de bloque y entidades).
- Si la fogata estaba sobre bloques puestos por el jugador, queda flotando.
- Otras hogueras activadas dentro del área desaparecen; su registro se borra al detectarlo (como cuando se rompe una
  fogata).
- La mancha de sangre (bloque) dentro del área se borra como cualquier bloque (tema cerrado: no se tocó su lógica).
- Los mobs hostiles toman el ciclo del lugar donde están (se recalcula cada segundo), no del lugar donde nacieron.
- El nivel temporal dispara los eventos de carga de chunks/entidades de Fabric para otros mods.

## Pendiente de decisión del usuario
- ¿Quitar **todas** las entidades no jugador del área (también mascotas, caballos, aldeanos, gólems, soportes de
  armadura, marcos)? Hoy sí (salvo vehículos con un jugador montado), como se pidió.
- Valores marcados como "propuesta por defecto" arriba.
- `ENABLED = true`: la mecánica está activa (solo actúa cuando alguien quema una llama).
