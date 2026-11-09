# Contenido de BSE por datapack

Guía para servidores y creadores de packs. Con esto se agregan armas, piezas de armadura, consumibles y pesos a BSE
sin escribir Java. El jugador solo instala BSE (más Fabric API); el servidor reparte el resto:

- **Datapack** (en la carpeta `datapacks` del mundo): las definiciones. BSE las envía solas a cada jugador al entrar y
  después de `/reload`.
- **Resource pack del servidor** (`resource-pack=` en `server.properties`): modelos, texturas, nombres y animaciones.

Ejemplo completo y probado: [`tools/qa/testpack/`](../tools/qa/testpack/).

## Dónde van los archivos

```
data/<tu_namespace>/soulsmode/weapon/<nombre>.json      -> arma      <tu_namespace>:<nombre>
data/<tu_namespace>/soulsmode/armor/<nombre>.json       -> armadura
data/<tu_namespace>/soulsmode/consumable/<nombre>.json  -> consumible
data/<tu_namespace>/soulsmode/weight/<nombre>.json      -> regla de peso
```

Un archivo con errores se salta y queda en el log con `[BSE] skipped ...`; los demás cargan igual. Al cargar, el log
dice cuántas definiciones entraron: `[BSE] datapack content: N weapons, ...`.

## Cómo se asigna el contenido a un ítem

Dos formas, combinables:

1. **Componente** en cualquier stack: `soulsmode:weapon`, `soulsmode:armor` o `soulsmode:consumable` con el id.
   Para contenido nuevo usa los ítems base de BSE, `soulsmode:weapon` y `soulsmode:consumable`:
   ```
   /give @s soulsmode:weapon[soulsmode:weapon="mipack:katana",custom_model_data=1,item_name='"Katana"']
   ```
   El componente funciona en recetas, loot tables y tradeos como cualquier otro.
2. **`"items"`** en la definición: lista de ítems (`"minecraft:blaze_rod"`) o tags (`"#minecraft:swords"`). Esos
   ítems pasan a ser ese contenido sin componente. Lo del datapack gana sobre lo que BSE trae.

## Armas

El formato es el mismo de las armas de BSE (`data/soulsmode/ds2/weapons.json`, exportado de DS2). Lo práctico es
**heredar** de un arma existente y cambiar solo lo necesario:

```json
{
  "extends": "soulsmode:battle_axe",
  "items": ["minecraft:blaze_rod"],
  "weight": 9.5
}
```

- `extends`: un arma de BSE (`soulsmode:longsword`, `battle_axe`, `mace`, `club`, `spear`, `pickaxe`, `fists`,
  `shield`) u otra arma de datapack. Los objetos se mezclan clave por clave; las listas se reemplazan enteras.
- Campos más usados: `weight` (peso), `attack` (daño base `base0` / `baseMax`, `coef` de escalado, `requirements`
  Fue/Des/Int/Fe), `guardRates`, `guardStability`, `parryRate`, y los movesets `right`, `left`, `two_handed`,
  `critical`, `kick`.
- **Animaciones propias:** en un slot de ataque, `taeId` puede ser el id de una animación de tu resource pack
  (`"taeId": "mipack:corte_largo"`, archivo en `assets/mipack/player_animations/`). Sus tiempos van en
  `"animations"` del mismo archivo, con las claves del formato de BSE: `duration`, `hits` (`[inicio, fin, slot]`),
  `avoidLock`, `moveLock`, `lightLock`, `heavyLock`, `leftLightLock`, `leftHeavyLock`, `combo`, `parry`,
  `regenBlock`, `leftAuthored`.
- Limitación: las animaciones de víctima de los críticos (backstab, riposte) usan las de BSE.

Las armas de datapack sin material no tienen nivel de mejora (+0); si se asignan a ítems vanilla con material, usan
la tabla de mejora por material de BSE.

## Armadura

```json
{
  "slot": "head",
  "items": ["minecraft:carved_pumpkin"],
  "factor": 0.1,
  "defence": [20, 20, 20, 20],
  "absorption": [0.01, 0.01, 0.01, 0.01],
  "weight": 2.0
}
```

- `slot`: `head`, `chest`, `arms` (botas) o `legs`. Cuenta en la defensa del jugador cuando se lleva puesta.
- `factor`: aporte a la defensa por stats; `defence`: defensa física por subtipo; `absorption`: magia, rayo, fuego,
  oscuridad (0–1).
- En 1.21.1 un ítem no se puede poner en el cuerpo solo con componentes: usa ítems vestibles (armaduras vanilla,
  calabaza tallada, cabezas), por componente o por `"items"`.

## Consumibles

```json
{
  "use": "effigy",
  "items": ["minecraft:sweet_berries"],
  "consume": true,
  "weight": 0.3,
  "effects": [
    {"type": "mob_effect", "id": "minecraft:regeneration", "duration": 100, "amplifier": 0},
    {"type": "function", "id": "mipack:al_usar"}
  ]
}
```

- Solo funcionan en Modo Souls. Se usan con clic derecho, con los mismos bloqueos que la Efigie humana; si el uso se
  corta (una rodada...) antes del efecto, no pasa nada y el ítem se conserva.
- `use`: animación de BSE (`"effigy"` o `"resin"`) o una propia:
  `{"animation": "mipack:beber", "duration": 2.0, "avoidLock": [0, 1.5], "attackLock": 1.5, "moveLock": 0, "effectAt": 0.8}`.
- `effects`:
  - `mob_effect`: efecto vanilla (`id`, `duration` en ticks, `amplifier` opcional).
  - `function`: ejecuta una función del datapack como el jugador (permiso 2). Sirve para cualquier cosa que los
    comandos permitan.
  - `restore_humanity`: anula el estado de hueco, como la efigie.
  - `weapon_buff`: el buff de resina sobre el arma (`"element"`: `lightning`, `fire`, `dark`, `poison`).
- `consume`: si se gasta un ítem al aplicar el efecto (por defecto `true`).

## Pesos

```json
{"items": ["#minecraft:logs"], "weight": 0.1}
```

Peso por stack (como todo el peso de BSE). Gana sobre la tabla de BSE. Orden: armadura, consumible, regla de peso, arma
de datapack.

## Resource pack

- Modelos: los ítems base son `soulsmode:weapon` (espada de hierro por defecto) y `soulsmode:consumable` (papel por
  defecto). Para darles otro aspecto, reemplaza `assets/soulsmode/models/item/weapon.json` con `overrides` por
  `custom_model_data`:
  ```json
  {
    "parent": "minecraft:item/iron_sword",
    "overrides": [{"predicate": {"custom_model_data": 1}, "model": "mipack:item/katana"}]
  }
  ```
- Nombres: el componente `item_name` en el stack, o claves de traducción en tu `lang`.
- Animaciones: `assets/<ns>/player_animations/*.json` (formato de Player Animation Library).

## Hechizos (`soulsmode/spell/`)

El motor es el de DS2 (sintonía, usos, lanzamiento, proyectiles y daño salen de la decompilación; ver
`docs/research/spells.md`, `catalysts.md`, `casting.md`, `dummypoly.md`). Un hechizo es una fila de `SpellParam` con sus
`BulletParam` / `PlayerDamageParam` en JSON. Para sacar uno de los params de DS2:
`python tools/re/spell_export.py <id de SpellParam> <salida.json>`.

```json
{
  "school": 0,                 // 0 hechicería, 1 milagro, 2 piromancia, 3 / 4 hex
  "requirements": [10, 0],     // Int, Fe
  "slots": 1,                  // ranuras de sintonía
  "uses": [30, 31, 32, 33, 34, 35, 36, 37, 38, 40],  // por nivel de usos 1..10 (sale de la Sintonía)
  "target": 0,                 // solo 0 (siempre) por ahora
  "castTime": 0.25,            // bloque +0x00: factor de la espera antes del lanzamiento
  "prepare": "38110010", "cast": "38110020",         // animaciones de c0001 (TAE) o ids propios
  "staminaFactor": 0.8,        // bloque +0x10: multiplica el costo del catalizador
  "rangeFactor": 1.0,          // +0xb4: alcance del rastreo
  "bullet": "31010000",
  "bullets": { "31010000": { ... } },                // ver spell_export.py; los hijos van en el mismo mapa
  "particle": "minecraft:enchant"                    // aspecto del proyectil (los SFX de DS2 no se pueden portar)
}
```

- Se obtienen como ítem `soulsmode:spell[soulsmode:spell="mipack:flecha"]`; cada copia es un hechizo con sus usos.
- Se sintonizan en la hoguera (botón **Sintonizar hechizos**); se recargan al descansar y al reaparecer. Se cambia el
  activo con la tecla **Cambiar hechizo** (B por defecto).
- Se lanzan con el ataque ligero de la mano que tiene el catalizador. Los catalizadores de BSE son
  `soulsmode:sorcerers_staff`, `soulsmode:clerics_chime` y `soulsmode:pyromancy_flame` (ningún ítem vanilla los usa:
  asígnalos con el componente `soulsmode:weapon` o con un arma de datapack que los extienda).
- Nombre: clave `spell.<namespace>.<nombre>` en el `lang` del resource pack.
- Animaciones propias: `prepare` / `cast` pueden ser ids del resource pack, con sus tiempos en
  `"castAnimations"` del mismo archivo (formato de `castAnimations` de `weapons.json`, incluido `<prepare>_hold`).
- Reacción del mob golpeado: `reactionSpeed` (PDP +0x2c) y `reaction` (la de DS2 tras la tabla de reacciones) de cada
  fila de daño; con `reaction` 2 o 3 el mob queda quieto 0.9 / `reactionSpeed` s, como con un golpe de arma. Sin
  esos campos, no reacciona.
- `"spEffects"`: los SpEffects que aplican las filas de daño (`spell_export.py` los incluye). Se ejecutan
  `heal`, `statMultiplier`, `statusSelectBullet` y `durationScales`; el resto queda listado en `"unsupported"`.
  Así funcionan los milagros de curación (Curar, Gran curación, Luz solar…). A quién afecta lo decide `teams` de cada
  fila de daño: 4 = el lanzador, 8 = aliados (jugadores a los que no puede dañar, mascotas propias), 0x10 = hostiles.
- Buffs soportados: `split` y `attack` (arma de una mano según la ranura `effectSlot` 26 izquierda / 27 derecha),
  `defence`, `statRate` índice 26 (daño recibido), `statAdd` índice 1 (peso), `movementRate`.
- Objetivos soportados: 0 y 1/2 (arma / escudo de la otra mano, encantable). Aún no soportado: Homeward / Guidance (3/4), emisores sin límite de disparos y hechizos
  que gastan almas.

## Scripts Lua (`soulsmode/script/`)

Para lo que los datos de DS2 no cubren, un hechizo puede llevar un script: `"script": "mipack:nombre"` en su JSON y el
archivo en `data/mipack/soulsmode/script/nombre.lua`. Corre **solo en el servidor** (los jugadores no instalan nada),
en Lua 5.2 (Cobalt) con `string`, `table`, `math`, `coroutine` y funciones base; no hay `load`, `io`, `os` ni acceso a
Java. Cada llamada tiene 50 ms; si se pasa, se corta y queda en el log. Los errores salen en el log como
`[BSE] script …`.

Funciones que el script puede definir (todas opcionales):
- `on_cast()`: en cada evento de disparo de la animación de lanzamiento (después de la bala propia del hechizo, si su
  JSON tiene `"bullet"`; sin `"bullet"`, solo corre el script). Gasta el uso igual que siempre.
- `on_hit(target, bullet)`: cuando una bala del hechizo golpea a un personaje (`target` = id de entidad).
- `on_expire(bullet, x, y, z)`: cuando una bala termina (choque o fin de vida).

La API es la tabla `bse` (las piezas del motor de DS2, nunca Minecraft directo):
- `bse.caster()` / `bse.entity(id)`: `{id, x, y, z, yaw, player, caster, hp, maxHp}` (PS de DS2).
- `bse.target()`: id del objetivo fijado del lanzador, o `nil`.
- `bse.stat("intelligence")`: atributo del lanzador.
- `bse.near(radio)`: ids de las entidades vivas cerca del lanzador (radio máx. 32).
- `bse.emit(bala [, x, y, z [, dx, dy, dz]])`: emite una de las balas del hechizo (con su emisor de `BulletParam`:
  abanico, disparos, rastreo al objetivo fijado), con el poder de ataque del lanzamiento.
- `bse.effect(id, speffect)`: aplica uno de los SpEffects del hechizo a una entidad.
- `print(...)`: al log del servidor.

Ejemplo (`tools/qa/testpack`): `triple_arrow.lua` lanza tres Flechas del alma en abanico.

## Enemigos (`soulsmode/enemy/`)

Un enemigo es una entidad genérica `soulsmode:enemy` definida por un JSON en `data/mipack/soulsmode/enemy/nombre.json`.
Se crea con `/souls enemy mipack:nombre` (operador) o con
`/summon soulsmode:enemy ~ ~ ~ {BseEnemy:"mipack:nombre"}` (también sirve en spawners y funciones). Los números siguen
los params de DS2 (`docs/research/enemies.md`, `docs/research/enemy_ai.md`):

| Campo | DS2 | Uso |
|---|---|---|
| `hp`, `stamina` | EnemyParam +0x28 / +0x2c | PS de DS2 (40 PS = 1 corazón) |
| `poise`, `poiseRegen` | +0x118 / +0x11c | con poise > 0 el golpe no interrumpe; regen por segundo |
| `defence`, `absorption` [magia, rayo, fuego, oscuro] | +0x9c / +0xa0..+0xac | daño del jugador: `max(piso, 6·AR − def)/6`, elementos `·(1 − abs%)` |
| `guard` `{stability, blocked[5]}` | +0x74 / +0x78..+0x88 | opcional |
| `attackMultiplier` | +0xd8 | multiplica los valores de ataque |
| `bleedResistance` (opcional, 0..1) | +0xb4 × 0.01 | resistencia al sangrado (infusión Hemorragia, `docs/research/bleed.md`); por defecto 0.30, lo más común en DS2; 1 = inmune |
| `souls` | +0xd0 | experiencia que recibe quien lo mata |
| `rolls`, `drops` `[{item, count, chance}]` | ItemLotParam2_Chr modo 0 | cada tirada: `chance` % sobre `max(suma, 100 %)` |
| `walkSpeed`, `runSpeed` | (movimiento raíz del paquete) | bloques por segundo |
| `size` [ancho, alto], `followRange` | | caja y distancia de persecución |
| `model`, `texture`, `animationFile` | | archivos GeckoLib del resource pack |
| `animations` | | `idle`, `walk`, `run` (nombres) y `hurt`, `death` (`{name, duration}`, obligatorios) |
| `script` | ESD superior | Lua opcional (abajo) |
| `boss` | | `true`: barra de vida de jefe (con su nombre) para los jugadores dentro de `followRange` |
| `turn` [lento, rápido, velocidad] | EnemyMoveParam +0x2c / +0x30 / +0x20 | giro en rad/s fuera de ataques: interpola de lento a rápido según velocidad / `velocidad` (`FUN_1403a28b0`). Sin él, el giro de Minecraft |

Cada ataque (`attacks`) es una entrada de `EnemyBehavior*Param` con su fila `EnemyDamageParam`:
- `name`, `animation`, `duration` (s), `hits`: ventanas `[inicio, fin]` en segundos (los eventos de golpe del TAE), o
  `{"window": [inicio, fin], "damage": {...}}` cuando cada golpe tiene su propia fila EDP (en DS2 el evento nombra la fila).
- `damage` (la fila por defecto de los golpes): `physical`, `magic`, `lightning`, `fire`, `dark`, `floor`, `rate`, `staminaDamage` (EDP +0x0c..+0x24).
  Contra un jugador en modo Souls: físico `max(piso, 10·V − def)/12·rate`, elementos `max(piso, 6·V)/6·(1 − abs)·rate`
  con la defensa y absorciones de su equipo; contra otros, defensa 0.
- `reach`, `angle`: argumentos de la subrutina de ataque de DS2 (`S2147483595`): dentro del alcance y del ángulo el
  ataque sale; dentro del alcance y fuera del ángulo gira hasta 1.5 s; fuera del alcance camina si está a menos de
  `reach + 1.5`, si no corre, y se rinde a los 3 s caminando o 10 s corriendo.
- `range` [mín, máx] (opcional) y `weight`: para la tabla declarativa, como la cascada de `ai102000`: entre los ataques
  cuya distancia cae en `range` se elige uno al azar con probabilidad proporcional a `weight`. DS2 no tiene
  enfriamientos: las pausas se escriben en el script.

Golpe al enemigo: con poise 0 cada golpe corta su acción y reproduce `hurt` (qué animación de reacción usa DS2 por
categoría de daño aún no está trazado; las duraciones del Hollow Soldier están en enemy_ai.md). Al morir paga las almas, tira el drop y reproduce `death`.

Nombre: la clave `enemy.mipack.nombre` del resource pack (si falta, "Enemigo").

### IA por Lua
Con `"script"`, el script hace de ESD superior (DS2 escribe la IA en máquinas EzState, no en Lua; el mod expone las
mismas piezas). Mientras el enemigo tiene objetivo y no está acercándose ni atacando, se llama
`on_think(self)`; el script pide un ataque y el motor hace el acercamiento y el giro de la subrutina. Funciones
(solo en ganchos de enemigo):
- `bse.distance()` (f7001, 9999 sin objetivo), `bse.angle()` (f7002, grados absolutos), `bse.height()` (f7007).
- `bse.attack(nombre)` (orden 5204): `false` si no existe o ya está atacando. `bse.busy()` (f5001).
- `bse.var(i)` / `bse.set_var(i, v)` (f5900 / 5300): 8 variables por enemigo, para estados y fases.
- `bse.time()`: segundos desde que apareció (tiempos de estado y temporizadores, f3 / f2).
- `bse.self()`, `bse.target()`, `bse.entity(id)`, `bse.near(r)`; `math.random` para el `RandInt` de la tabla.

Si el script no define `on_think`, decide la tabla declarativa. Ejemplo (`tools/qa/testpack`): `hollow_lua.lua` con la
forma de la tabla de `ai102000` (cerca: 50 % `slash`; si no, `combo`).

## Etiquetas y datos que un datapack puede cambiar

Además del contenido de arriba, BSE lee estos archivos de datapack. Un datapack del servidor puede reemplazarlos con
el mismo nombre (y `"replace": true` en las etiquetas para quitar valores en vez de sumarlos).

### Etiquetas de entidades (`data/soulsmode/tags/entity_type/`)
| Etiqueta | Qué hace | Por defecto |
|---|---|---|
| `soulsmode:no_hit_reaction` | Estos mobs no se aturden ni hacen animación de daño al recibir golpes del modo Souls | devastador, warden, wither, dragón, gigante |
| `soulsmode:parry_animation` | Al ser parreados hacen la animación de "parreado" de DS2; el resto solo queda aturdido | zombi, husk, ahogado, aldeano zombi, enderman, esqueleto wither, golem de hierro, vindicador |
| `soulsmode:boss_bar` | Muestran barra de jefe a los jugadores del modo Souls cercanos | devastador, warden |
| `soulsmode:winged` | Reciben el doble de daño de la Resina de pino dorada (rayo) | murciélago, abeja, loro, phantom, gallina, allay, vex, dragón |
| `soulsmode:dark_weak` | Reciben el doble de daño de la Resina de pino oscura | enderman, endermite, shulker, dragón, vex, allay |

### Etiquetas de biomas (`data/soulsmode/tags/worldgen/biome/`): probabilidad de invasión
| Etiqueta | Probabilidad por tirada (cada 5 min) | Por defecto |
|---|---|---|
| `soulsmode:invasion_high` | 3 % | bosque oscuro |
| `soulsmode:invasion_cave` | 2 % | cuevas de goteo, cuevas frondosas, oscuridad profunda |
| `soulsmode:invasion_very_low` | 0,25 % | llanuras, llanuras de girasoles, pradera |
| `soulsmode:invasion_none` | nunca | campos de champiñones, arboleda de cerezos |
| (resto) | 1 % | |

Dentro de una cámara de pruebas es 5 %, en una mina abandonada 3 % y en una aldea 0,25 %, sin importar el bioma
(estructuras, no etiquetas). Cada día sin dormir multiplica por 1,5 (hasta ×5); haber dormido en el último día la anula.

### Etiquetas de ítems
| Etiqueta | Qué hace en BSE |
|---|---|
| `minecraft:enchantable/fire_aspect`, `minecraft:enchantable/durability` | BSE agrega el palo (`minecraft:stick`) para que acepte Aspecto ígneo, Irrompibilidad y Reparación |
| `c:ores` (bloques) | Llama de la adversidad: solo los rasgos de mineral cuyo bloque está en esta etiqueta reciben vetas extra al reiniciar una zona |

### Objetos tirados en el mundo (almas y resinas)
- `data/soulsmode/worldgen/placed_feature/ground_item.json`: cuántos aparecen. `rarity_filter` `"chance": 32` = uno
  cada 32 chunks en promedio. Subir el número los hace más raros.
- `data/soulsmode/loot_table/gameplay/ground_item.json`: qué objeto sale y con qué peso.

### Recetas (`data/soulsmode/recipe/`)
Las resinas, la resina sólida, la bolsa (`bundle`) y la bomba incendiaria. Se reemplazan o se quitan como cualquier
receta.

### Botín y comerciante que BSE agrega por código
BSE suma objetos a tablas de botín de Minecraft desde el código (no hay JSON que editar):

| Objeto | Dónde | Probabilidad |
|---|---|---|
| Efigie humana | templo del desierto, templo de la jungla, fortaleza del Nether | 20 % |
| Llama de la adversidad | las de la efigie, más mazmorras y bóvedas de las cámaras de prueba (normal y ominosa) | 20 % |
| Talismán de Lloyd | puestos de saqueadores, mansiones del bosque | 25 % |
| Talismán de Lloyd | brujas matadas por un jugador | 10 % |
| Bomba incendiaria (1–3) | mazmorras, minas abandonadas, puestos de saqueadores | 30 % |

El comerciante errante vende la efigie, la llama y el talismán (2 esmeraldas, 4 usos) y bombas incendiarias
(1 esmeralda por 4, 8 usos).

Estas inyecciones solo se aplican a las tablas originales de Minecraft: si un datapack reemplaza una de esas tablas,
la tabla del datapack queda tal cual, sin lo que agrega BSE.

### Lo que no es configurable por datapack
- La **Llama de la adversidad** no tiene parámetros de datapack: el radio (8 chunks), el máximo de ciclos (5) y los
  efectos por ciclo están fijos en el código (`dev.soulsmode.ascetic.AsceticRules`). Para proteger zonas del reinicio
  hay un evento para mods: [api.md](api.md).
- Los datos de DS2 que trae el jar (`data/soulsmode/ds2/*.json`, `data/soulsmode/mob_guard_stamina.json`) se leen
  del propio jar: un datapack no los cambia. Para contenido nuevo usa las carpetas de arriba.
