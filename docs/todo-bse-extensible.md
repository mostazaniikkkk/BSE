# To-do: BSE extensible por terceros

Aprobado por el usuario el 2026-10-31.

## Requisito para publicar 0.4.0 (usuario, 2026-11-10) — publicado como 0.5.0 por pedido del usuario (2026-11-18)
El salto a 0.4.0 es por dos features que el usuario dejó a propósito para el final. **No se publica 0.4.0 hasta que
estén las dos** (ni commit-push ni release antes de que el usuario lo diga):
- [ ] **Pactos** (covenants de DS2): **en BSE solo la Senda Azul** (usuario, 2026-11-10: tótem pequeño aprobado; los
      otros 5 pactos de invasiones y sus estructuras completas van a DarkTwo). Investigados (`docs/research/covenants.md`). Entran los 6 de invasiones, un
      altar por pacto en estructuras del mundo; emparejamiento aleatorio por nivel de alma (menor → cualquiera mayor;
      mayor → hasta 10 niveles menos). Resto del diseño pendiente con el usuario.
  - [x] Estructura de la Senda Azul desde el modelo de DS2 (tótem aprobado por el usuario, 2026-11-10); genera en el
        mundo. Las otras 7 se sacaron de BSE (DarkTwo).
  - [x] Senda Azul: defensores jugadores (el rol de los Centinelas, sin NPC), decisión del usuario 2026-11-10. Se
        implementa junto con las invasiones PvP.
  - [ ] Notas de la conversión (por si se reusa). Simplificaciones: recorte cúbico de 61 bloques (corta edificios), color
        medio por textura (sin detalle), sin recortes alfa (rejas, follaje), sin escaleras/losas (todo bloque entero),
        mano del espacio de DS2 sin confirmar (podrían salir espejadas), espaciado de generación de diseño.
  - [x] Altar de la Senda Azul: unirse, abandonar (regla del usuario), recompensas de rango, Sello azul (2026-11-10).
  - [ ] Altar: textura y modelo provisorios (ladrillo cincelado vanilla con la forma de o00_9050).
  - [x] Invasiones PvP entre jugadores (`SoulsPvp`) y defensores de la Senda Azul, 2026-11-10. Falta probar con dos
        jugadores en el servidor.
  - [ ] Diseño mío (no DS2): el invasor y el defensor llegan a 24–48 bloques del anfitrión (bajo techo, al lado); el
        aviso al defensor es un mensaje de chat con [Acudir]; el orbe se gasta solo si se encontró un mundo.
  - [ ] No portado: el aspecto de espíritu oscuro / azul (`ChrPhantomParam`, colores sin interpretar); el efecto
        visual de Silencio (5182) y de la Lágrima azul (932); el gesto "Bienvenida" de Saulden (BSE no tiene gestos);
        peso y durabilidad de los anillos (son encantamientos); el Mordisco sangriento +1 como recompensa (DS2 da el +0;
        el +1 existe como nivel 2 del encantamiento, sin fuente).
  - [ ] Sin confirmar: qué da +1 al anfitrión de la Senda Azul en DS2 (el mod: +1 al defensor que gana, decisión del
        usuario); si la protección exige llevar el sello; los operadores ESD de los rangos.
- [ ] **Invasiones PvP** (entre jugadores; las invasiones NPC ya existen, `SoulsInvasions`).

**Objetivo:** que terceros (servidores) puedan agregar contenido a BSE (bendiciones, hechicería, piromancias,
armas, enemigos, jefes) y distribuirlo sin que el jugador instale un modpack. El jugador instala solo BSE + Fabric API.

**Regla de diseño:** el contenido de terceros no trae código Java. Todo es datos, assets o scripts Lua que el jar de
BSE ya sabe interpretar. El contenido propio del mod se queda en el jar como está; lo de terceros se suma encima.

| Qué | Cómo llega al jugador |
|---|---|
| Definiciones (hechizos, armas, enemigos, jefes) | Datapack del servidor; BSE las sincroniza al cliente al entrar |
| Modelos, texturas, sonidos, textos, animaciones | Resource pack del servidor (PAL lee `assets/`) |
| Lógica a medida | Scripts Lua en el datapack, ejecutados solo en el servidor |
| Librerías (PAL, GeckoLib, Cobalt) | Dentro del jar de BSE (`include`, como PAL hoy) |

## 1. Canal de contenido — hecho (2026-10-31)
- [x] Cargar definiciones de terceros desde datapacks (`data/<ns>/soulsmode/<tipo>/`), recargables con `/reload`.
- [x] Sincronizarlas al cliente al entrar y al recargar; los errores quedan en el log y el archivo se salta.
- [x] Consultas con prioridad: definición de datapack primero, contenido propio del jar como respaldo
      (moveset por item, peso, pieza de armadura, consumible).
- [x] Items base genéricos (`soulsmode:weapon`, `soulsmode:consumable`) y componentes `soulsmode:weapon` /
      `armor` / `consumable`; modelo por `custom_model_data` desde el resource pack.
- [x] Prueba: datapack + resource pack del servidor con un cliente que solo tiene BSE (`tools/qa/testpack/`).
- Formato para terceros: `docs/datapacks.md`.
- Pendiente de esta fase: probar en PvP la defensa de una pieza de armadura de datapack.

## 2. Sistema de magia de DS2
**Regla del usuario (2026-10-31): no es genérico. El motor es el de DS2, sacado de la decompilación y los params,
sin excepciones autorizadas.** Los packs aportan hechizos en el formato de los params de DS2 (`SpellParam`,
`BulletParam`), igual que las armas de la fase 1 usan el formato de `weapons.json`.
- [x] Investigación: `docs/research/spells.md`, `catalysts.md`, `casting.md`, `dummypoly.md`.
- [x] Catalizadores de DS2 (bastón, campana, llama) con su moveset, escuelas y factores.
- [x] Sintonía en la hoguera, usos por nivel de Sintonía, recarga al descansar / reaparecer, cambio de hechizo.
- [x] Lanzamiento: comprobaciones de DS2 y lanzamiento fallido; preparación + espera (último cuadro) + lanzamiento,
      animaciones de c0001 exportadas de los `.nsa`; estamina al entrar en ShootStateControl.
- [x] Proyectiles con `BulletParam` (velocidad, aceleración, gravedad, rastreo al objetivo fijado, vida, hijos) y daño
      de DS2 (poder de ataque del catalizador + daño plano del hechizo); origen en el dummy poly del catalizador.
- [x] Hechizos por datapack (`docs/datapacks.md`) y exportador `tools/re/spell_export.py`.
- [x] Prueba en el juego (2026-10-31): sintonía, usos, recarga, lanzamiento fallido, Flecha del alma y Bola de fuego con cada mano, con y sin objetivo fijado.
- [x] Emisor completo de `BulletParam` (abanico, disparos en el tiempo, dispersión, corrección al objetivo por bala;
      `casting.md` §8). Probado con la Lanza de rayo (2026-10-31).
- [x] Efectos de hechizo, curaciones (`docs/research/speffects.md`): SpEffect de la fila de daño, bando por `+0x40`,
      selección por Fe, multiplicador por stat, curación en el tiempo. Probado en el servidor QA con el cliente del
      launcher: Curar con Fe 12 cura 450 PS (11,25 corazones de MC) (2026-10-31).
- [x] Buffs (`speffects.md` §11): ataque y reparto por mano, defensa y absorción, daño recibido ×, peso, velocidad;
      duración por stat, ranuras (relanzar renueva, mismas ranuras se reemplazan, cambiar de arma borra la de su mano).
      Probado en el servidor QA (2026-10-31): Carne de hierro (daño ×0,6, velocidad ×0,9, +65 de peso, 25 s) y Arma mágica (más daño con el arma izquierda, falla sin arma encantable).
- [x] Arma mágica y encantamientos: condición de objetivo 1/2 (arma de la otra mano encantable, `spells.md`). Probado.
- [ ] Objetivos 1–4 (encantar arma, escudo, Homeward, Guidance) y hechizos que gastan almas.

## 3. Lua para hechizos
- [x] Cobalt 0.9.9 dentro del jar (`include`), sandbox sin `load` / `io` / `os`, 50 ms por llamada (test unitario).
- [x] Hooks `on_cast` / `on_hit` / `on_expire` y la API `bse` (`docs/datapacks.md`, "Scripts Lua"). Probado en el servidor QA: `triple_arrow.lua` lanza 3 flechas que golpean a 3 husks (2026-10-31).

### Auditoría del sandbox de Lua (2026-11-11)
Arreglado (`SoulsScripts`, tests en `SoulsScriptsTest`):
- [x] El corte por tiempo se podía tragar con `pcall` / `xpcall` / `coroutine.resume` (era un `LuaError`): un script
      podía colgar el servidor. Ahora es un `Error` de Java que Lua no puede atrapar.
- [x] Cada llamada que terminaba en error dejaba sus marcos en la pila del hilo principal de Cobalt
      (`LuaThread.runMain` no la desenrolla); tras un "stack overflow" el script respondía "stack overflow" a todo
      hasta el siguiente `/reload`. Ahora cada llamada corre en un `LuaThread` nuevo.
- [x] `bse.emit` aceptaba NaN / infinito, dirección cero y posiciones en chunks sin cargar; `bse.near` aceptaba NaN.
- [x] Aviso en el log si un hechizo o enemigo nombra un script que no existe o no cargó (`ContentReloader`).
- [x] Presupuesto con `System.nanoTime()` (antes el reloj del sistema, que puede saltar).

Sin hacer (no son de DS2; son límites del sandbox, sin decisión del usuario):
- [ ] **Sin tope de memoria** (límite del motor: Cobalt no cuenta memoria). Dentro de una llamada lo acotan los 50 ms
      y Cobalt convierte el `OutOfMemoryError` en error de Lua; pero un script puede acumular en sus globales entre
      llamadas hasta agotar la memoria del servidor.
- [ ] **Un script que agota los 50 ms en cada llamada no se desactiva**: un `on_think` con un bucle sin fin gasta
      50 ms por enemigo y por tick (el servidor queda a ~1 TPS) hasta que alguien lo quite. Opción: desactivar el
      script tras N cortes hasta el siguiente `/reload`.
- [ ] **El presupuesto es tiempo de reloj, no de CPU**: una pausa del recolector de basura de más de 50 ms durante una
      llamada corta un script legítimo (al cargar, lo deja sin cargar). No observado.
- [ ] **Sin tope de balas por llamada ni de líneas de log**: `bse.emit` en un bucle crea miles de balas en 50 ms, y un
      `on_think` que falla escribe su error (con traza) 20 veces por segundo por enemigo.
- [ ] Los ganchos con la API `bse` no se volvieron a probar en el juego tras el cambio a un hilo por llamada (solo
      tests unitarios y arranque del servidor QA).

## 4. Enemigos y jefes
- [x] GeckoLib dentro del jar; entidad genérica `soulsmode:enemy` definida por JSON (vida, defensas, absorciones,
      poise, estamina de guardia, drops, almas, modelo). `/souls enemy <id>`. Probado en el servidor QA (2026-10-31):
      98 / 95 de daño con 118 físico, 90 almas, drop, muerte.
- [x] Ataques de enemigo: filas EDP con ventanas de golpe y combos de varias ventanas.
- [x] IA declarativa con la forma de `ai102000` (enemy_ai.md): rango (f7018), peso (cascada RandInt), subrutina de
      acercamiento / giro / rendición con alcance y ángulo. Sin enfriamientos (DS2 no los tiene).
- [x] IA por Lua: `on_think` + `bse.distance/angle/height/attack/busy/var/set_var/time`. Probado: `hollow_lua.lua`.
- [x] Jefes: `"boss": true` (barra de vida de jefe); fases por `bse.var` en su script.
- [x] Del TAE de DS2 (`c102000_pl.tae`): ventanas de golpe reales con su fila EDP por golpe, giro del enemigo
      (EnemyMoveParam). Soldado de prueba con los datos reales; probado en QA (2026-10-31).
- [ ] Sin confirmar en DS2: qué reacción toca por categoría de daño, cómo avanza un combo, giro durante el ataque
      (flags `chr.b8+0x4ec/+0x4f0`), guardia del enemigo (entrada lógica 9), daño de poise del jugador, hitbox por
      cápsula del arma (datos en EDP +0x30..+0x38, eje sin confirmar; el servidor no tiene las poses).
- [ ] Revisar si es viable cambiar el moveset de la araña de Minecraft por el de la Ducal Spider de DS2 (pedido del
      usuario 2026-11-02): identificarla en los datos, su esqueleto (ocho patas) frente al modelo de la araña, sus
      ataques, IA y parámetros, como se hizo con el Hollow Soldier en los zombis y se está haciendo con el ogro para el
      golem de hierro.
- [ ] Sonidos (pedido del usuario 2026-11-02): empaquetar los SFX del mod en el jar (`assets/soulsmode/sounds`,
      `sounds.json`) y programar su uso en las armas (golpes, swings, guardia, parry…) y en la hoguera (encender,
      descansar). Qué sonido y en qué momento: de los eventos de sonido del TAE de DS2 (sacar los ids y tiempos).
- [x] Detección de los enemigos (implementado 2026-11-03, `SoulsPerception`, docs/research/detection.md "En el mod";
      pendiente de QA): vista (cono, alcance con luz / sin luz / antorcha, rayo, memoria de 10 s) y oído (sonidos del
      jugador en los tiempos del TAE) de LogicComParam 102010 en los monstruos de tierra, 300030 y las reglas del ogro en
      el golem; el Hollow investiga lo que oye. Sin alarma de grupo.
- [ ] Que el esqueleto de Minecraft dispare flechas con las animaciones de DS2 (pedido del usuario 2026-11-02): buscar
      en DS2 un enemigo arquero cuyas animaciones de tensar y disparar se puedan llevar al modelo del esqueleto
      (o las del arco del jugador, c0001, ya exportadas: docs/research/bow.md), con sus tiempos y su IA de disparo.

## Simplificaciones (van siempre acá salvo que el usuario diga que se quedan; regla 2026-11-03)
Cosas que hice más simples aunque el dato de DS2 existe o se puede sacar. Se corrigen.
- [x] Frames de invencibilidad de la rodada / paso atrás fijos (evento TAE 110500), sin la Agilidad: **corregido
      2026-11-03** (`ds2/Agility`, `Dodge.isInvincible`, `SoulsRoll`; agility.md). Además el 110500 no era el de
      i-frames: ahora son 110100 + la parte de 110300 que da la Agilidad.
- [ ] Ogro: la reacción al golpe usa siempre la columna 2; el ligero de los puños sería la reacción 1 (DmgCategory 20)
      (c3000_ogre.md §6).
- [ ] Ogro: el volumen del golpe usa alcance y ángulo, no las cápsulas / esferas de sus filas EDP (dummies 10 / 20 / 40
      de `c3000.flv`) (c3000_ogre.md, "Sin establecer").
- [ ] Catalizadores (`SoulsItems.SORCERERS_STAFF` / `CLERICS_CHIME` / `PYROMANCY_FLAME`, 0.3.1): sin durabilidad. DS2:
      `WeaponParam +0x28` = 30 / 30 / 50, y qué la gasta al lanzar no está trazado (catalysts.md §7).
- [ ] Calavera seductora (0.5.0, `SoulsThrown`): sale de los ojos del jugador, no de la dummy 20 de la mano (igual que
      Lloyd y la bomba). El mob atraído camina a la velocidad vanilla.
- [ ] Hueso de regreso (0.5.0, `SoulsHomeward`): llega al instante, sin fundido ni pantalla de carga (DS2 la tiene;
      no trazada).
- [ ] Objetos con ItemUsageParam que prohíbe la escalera (resinas 1700, Lloyd, bomba, calavera): solo el Hueso de
      regreso lo comprueba; los demás se pueden usar en una escalera.

## Parry lento (reporte del QA, 2026-11-10; weapons.md "Parry: revisión de la lentitud")
- [x] Compensación de latencia (decisión del usuario): predicción en el cliente y ataque fechado desde la pulsación.
- [ ] La compensación solo cubre ataques desde quieto a una mano; combos, ataques en movimiento, a dos manos, guardia,
      hechizos y arco siguen esperando la respuesta del servidor.
- [x] Búfer de entrada de DS2 (`SoulsInputBuffer`, attack.md "Búfer de entrada"): ataques y esquivas.
- [ ] Búfer: falta la ventana de las reacciones al daño (`SoulsPoise`), los comandos de objeto / cambio de arma /
      interactuar (7..0xe) y repetir objeto (8). La esquiva se guarda al apretar (DS2: al soltar).
- [ ] Las acciones que salen del búfer no tienen compensación de latencia (las arranca el servidor).
- [x] Roll y paso atrás: misma compensación de latencia (agility.md, "Revisión del QA").
- [ ] Si el servidor rechaza un roll predicho (estamina u otro bloqueo que el cliente no ve), el jugador ya se movió
      un poco antes de que el cliente lo corte.

## Vista previa al subir de nivel (stats.md "Pantalla de subir nivel")
- [x] Rejilla de DS2 (3 × 13, "actual ⇒ previsto"), 2026-11-10.
- [ ] Simplificaciones: un arma por mano (DS2 tiene 3 por mano); color del previsto de diseño; sin las marcas de
      `LevelUpStatusCalcParam`; Vel. hechizo sin el ajuste `FUN_14014d130`; estabilidad con la falta de stats del mod.
- [ ] Sin confirmar: colores de DS2, celdas sin texto (col. 1 renglones 0 y 8), modificadores de contexto.

## Pantalla de muerte (you_died.md)
- [x] Cartel "HAS MUERTO" / "YOU DIED" de DS2 antes de la pantalla de muerte de Minecraft, con su animación, la espera de
      3 s, el HUD oculto y el sonido `m000000010` (2026-11-10).
- [ ] Sin confirmar: cómo DS2 lleva el layout 1280×720 a la pantalla (el mod: escala a la altura, centrado), qué ranura
      de la matriz es cuál, el modo de mezcla, que la franja se quede hasta el frame 102, desde cuándo cuentan los 3 s y
      cuándo se apaga el sonido.

## Eventos TAE de ataque decodificados al revisar los espadones (2026-11-10; DarkTwo `espadones.md` §11)
- [ ] No portado (afecta a todas las armas de BSE): **111600 = ventana de contraataque**: si te golpean dentro de tu
      propio ataque recibes más daño (`FUN_140138d50`: × PDP+0x20 × WTP+0x6c). Quién borra el bit no está trazado.
- [ ] No portado: **111400** (durante tu crítico no te pueden hacer un crítico), **2202** (golpe extra con un PDP
      propio, tasa 0: probable empuje/reacción sin daño), **120100** (seguimiento/giro al empezar el ataque, sin
      confirmar), **111500** (suprime contactos de golpes, rama sin confirmar), **1504** (cámara de crítico),
      **300100** (efecto en la dummy 249 de los críticos).

## Arco: disparo sin apuntar (usuario, 2026-11-12: al backlog)
- [ ] El disparo sin apuntar (clic izquierdo) sigue la inclinación completa de la cámara. DS2 (`bow.md` §4.2,
      `casting.md` §7.7): de lado, el frente del cuerpo; vertical, la Y de la cámara con zona muerta (5° arriba /
      25° abajo → recto) y límite ±20° (× factores del arma sin confirmar); con fijado, ±30° / 2°. Falta portar la zona
      muerta y los límites. El usuario comentó que no sale al punto que apunta la cámara: decidir si se queda como DS2.

## Combate en el agua (reglas del usuario, no de DS2)
- [x] Agua baja (pies en el agua, cabeza fuera): moveset del Modo Souls, ataques de arma a 0.9 de velocidad
      (`SoulsWater`, 2026-11-16). Probado con el bot: 42 ticks en tierra, 47 en el agua.
- [ ] Agua honda (nadando o con la cabeza bajo el agua): sigue el moveset de Minecraft (placeholder del 2026-11-04)
      hasta que se diseñe el combate en el agua.
- [ ] En el agua no se predice el ataque en el cliente (la compensación de latencia queda solo en tierra).

## Petrificación, maldición y estatuas (2026-11-18; docs/research/petrification.md)
Simplificaciones (lo que DS2 tiene y el mod hace más simple):
- **Guardia**: en DS2 los golpes en guardia acumulan reducidos por la salida [8] / [9] del escudo; qué campo del escudo
  es no está identificado, así que el mod suma la acumulación completa aunque el jugador bloquee (`SoulsEnemy.dealDamage`).
- **Falta de stats en la armadura** (`falta17`, PlayerLackOfStatsParam fila 0x11): no se aplica a la resistencia de la
  armadura (igual que en el sangrado). `SoulsStatus.resistance`.
- **Anillos y objetos** que cambian la resistencia o la bajada (Anillo de nudillo de cenizas, de mordisco maldito, de
  resistencia, Grageas de serpiente tríclope, Perseverancia, Carne férrea, Lanza de Yorgh) y el **Anillo de protección
  de alma**: no portados (el mod no tiene esos objetos).
- **Material de piedra**: DS2 usa EneStone (mezcla no trazada) y la FullBodySfx 400010 → FFX 3419 (no abierto); el mod
  pinta la textura del mob / jugador en gris sobre el bloque de piedra (`StoneTextures`, mezcla 0.8 + 0.5·luminancia:
  valor del mod) y oculta los objetos de las manos y la cabeza.
- **Salida de la estatua**: los personajes de DS2 salen con su clip `StartUp_XX_Out` (8–12 s) y la SFX 103000 (FFX
  3665); los mobs de Minecraft no tienen clip: vuelven al instante con partículas y sonido de piedra rota de Minecraft.
- **Guía "Examinar"** (KeyGuideParam 1600, campos sin confirmar): no hay aviso; se examina con clic derecho al alcance de
  Minecraft (4.5 bloques).
- **SFX de la maldición** 653 en el dummy 249: no portado (no abierto).
- **Medidores en mobs**: solo el jugador en Modo Souls acumula (DS2 también tiene medidores en enemigos: ECP 120 / 90 s);
  ningún arma del jugador acumula en DS2, así que no hay fuente que los llene.
- **HUD**: solo petrificación y maldición (el sangrado del mod es del atacante jugador y no se muestra); el espacio
  1280×720 se escala a la altura de la pantalla (cómo lo hace DS2 no está trazado).
- **Después de DieCurse** (4.333 s) el cuerpo queda en el último cuadro hasta 5.25 s: el nodo `0x183c` que sigue en DS2
  no está identificado.
Sin confirmar: lo de petrification.md "Sin confirmar" (máximo del medidor, `+0x54` y la fila de DeadTypeParam, qué hace
cada acción de la muerte, SpEffect 901110, cuántas veces suma la maldición, la tabla estado de arranque → clip de las
estatuas, KeyGuideParam, los objetos de mapa asociados, la máscara del HUD).
Valores del mod (no de DS2): frecuencia de las estatuas (1 de cada 256 chunks), 20 % de rama por cofre, los cofres y los
cuatro mobs (decisiones del usuario salvo los dos números). Sin probar en el juego: daño de enemigos con `petrify` /
`curse` (probado con `/souls status`), tótem contra la petrificación, la generación natural (probado con `/place feature`).

## Minerales incandescentes (diseño del usuario, 2026-11-16; no es de DS2)
- [x] Implementado (`SoulsIncandescent`, regla de juego `soulsIncandescentMinerals`, activa por defecto porque el
      game feel ya se probó en DarkTwo con el acero incandescente).
- [x] Probado en el servidor QA: sale incandescente del horno (clic y shift-clic), sin XP; en el cursor y en el suelo
      se enfría a los 5 s; quema 1 por segundo en el inventario (mensaje de muerte propio); en el caldero se termina y
      suelta la XP de la receta de fundición.
- [ ] Sin probar en el juego: el 25 % de vaciar el caldero, la Protección contra el fuego, alto horno y ahumador
      (usan la misma ranura de resultado).
- [ ] Nota: con la comida y la saturación llenas, la regeneración de Minecraft (1 cada 10 ticks) tapa la quemadura de
      1 por segundo; igual que en DarkTwo.
- [ ] Texturas: el lingote de Minecraft recoloreado con una rampa de metal al rojo (la del acero de DarkTwo en el
      hierro; el oro más amarillo, el cobre más rojo, la chatarra oscura). Falta el visto bueno del usuario.

## Armas de DS2 de DarkTwo (usuario, 2026-11-12: "después necesito todas estas armas")
La lista y lo que falta en cada una está en DarkTwo `ARMAS-PENDIENTES.md`: 22 espadones (Espada bastarda … Espadón de
los Forlorn, con sprite) y el Florete / estoque (sprite; investigación en curso). Lo que necesitan **de BSE** para
quedar como en DS2 (DarkTwo `docs/espadones.md` §8, §10–§12):
- [x] Refuerzo e infusiones de las armas de datapack (2026-11-12, `datapacks.md` "Armas").
- [x] Material de refuerzo = cualquier ítem (el hueso de dragón petrificado va en DarkTwo como ítem propio).
- [x] Postura doble, ataque con guardia, capa de guardia, estabilidad por slot y ventanas de poise de datapack.
- [x] Balas de los ataques (`bullets` + `shots`; `SoulsWeaponBullets`). DarkTwo tiene que exportar las balas
      (BulletParam + PDP) en el formato de los hechizos y los `weaponBones` de sus animaciones propias.
- [x] Efectos propios: `atkRateBySoul`, `atkRateByHollowCount`, `changeWeaponParam` (`WeaponEffect`).
- [x] Ventana de contraataque (TAE 111600) para todas las armas (`SoulsAttack.counterFactor`).
- [ ] No portado: el pecado no existe en BSE (la Espada de la Pecadora queda con factor 1.0 siempre).
- [ ] No portado: contraataque cuando la víctima es un mob, un invasor NPC o un enemigo de datapack, y cuando el
      atacante es un enemigo o un invasor NPC (solo jugador → jugador).
- [ ] No portado: el subtipo físico de DamageCtrlParam +0x00 (1 = estocada en el florete) en el daño (φ / ARP_sub);
      BSE calcula todo con subtipo 0.
- [ ] No portado: las balas de los slots 5..9 en powerstance salen con el arma del ataque, no con la de la mano izquierda.
- [ ] No portado: los efectos propios de armas de BSE: Llama de piromancia 5400000 (`100130[04]` + `1000[03] (23, 0)`),
      Arco corto 4200000 y Ballesta ligera 4600000 (`1000[06]`, sin decodificar). Hace falta investigar `1000[06]`.
- [ ] Sin confirmar: lista de `weapons.md` "Contraataque, balas… — Sin confirmar".
- [ ] Simplificación: el menú (inventario, subida de nivel) no muestra el factor del efecto propio sobre S.

## Sonidos de objetos (sounds.md §8)
- [x] Almas (`s000000274`), efigie (`s000000273` + `s000000872`), calavera (impacto `s000000845`, señuelo
      `s000000846` en bucle).
- [ ] Simplificación: suenan en el jugador / en el punto, no en la dummy (20 / 26) que sigue el FFX.
- [ ] Sin confirmar: el bucle del señuelo (por el nombre de la muestra) y el momento exacto dentro del FFX.

## Calavera seductora y Hueso de regreso (usuario 2026-11-10; thrown_items.md, bonfire.md)
- [x] Implementados en 0.5.0 con sus datos de DS2; recetas del usuario (cruz de huesos con un alma → 4 calaveras;
      hueso + alma → 1 hueso).
- [x] Regla del usuario: la calavera afecta a los no-muertos de Minecraft (`#minecraft:undead`), no a la lista de
      DS2 (`LogicComParam +0x66`).
- [x] Decisión del usuario: sin hoguera de descanso, o si la hoguera ya no está o está apagada, el hueso lleva al spawn
      del mundo.
- [ ] Sin confirmar en DS2 (calavera): qué hace el enemigo con el objetivo (el mod: solo camina hacia él, sin atacar),
      a quién apunta al acabar, el factor de vida del señuelo (`petición+0x7c`), los tipos de personaje 5..19 que no
      crean señuelo.
- [x] Hueso: diálogo "¿Volver a la última hoguera?" con SÍ / NO (cursor en NO) y 5 s sin recibir daño
      (`1000[03] (24, 5.0)`), 2026-11-10. El diálogo también en las almas de gigante ("¿Usar %s?").
- [ ] Hueso, no portado: quitar el SpEffect 754 (quién lo aplica no está trazado).
- [ ] Hueso, sin confirmar: el rol de red que no viaja localmente (`DAT_1410c0050`), y si la invulnerabilidad de 5 s
      también anula el tambaleo (camino `vt+0x80` no trazado; en el mod no hay golpe, así que no hay tambaleo).
- [ ] Simplificación: el diálogo del mod es una pantalla de Minecraft (botones SÍ / NO), no el layout de DS2.
- [ ] Sin confirmar (los dos): qué hace `ItemUse_Speed` 0.7 con el clip; cuándo se descuenta el objeto; que el hueso se
      gaste es por datos (`ItemParam +0x52` bit 0).

## Agilidad: lo que quedó fuera (2026-11-03, docs/research/agility.md "Sin confirmar")
- [ ] Invasor NPC: sus esquivas siguen con la ventana 110500 (que no es i-frame). DS2 calcula su fracción con la
      calculadora de `ChrDodgeCtrl` / `EnemyDodgeCtrl` (`FUN_140328b10` / `FUN_140328c20`), sin trazar. Decidir
      (usuario) o investigar.
- [ ] Estabilidad (poise) del jugador: **no existe en el mod**. El 110500 de las esquivas es justamente "sin
      estabilidad" (`FUN_140139380`). En curso (usuario 2026-11-03): docs/research/poise.md.
- [x] **El jugador no reacciona a los golpes** (hallado 2026-11-03): implementado con la estabilidad (`SoulsPoise`,
      `ds2/Poise`, `PlayerReactionClient`, poise.md), **compilado, sin probar en el juego** (el usuario cortó las pruebas).
      Hecho: máximo (armadura + bono de stats), regeneración, daño por golpe (zombis/illagers c1020, Ogro c3000,
      invasor, PvP), superarmadura TAE 110500/110600, regla DCP+0x02, reacción 1 (capa del torso + ×0.7 de velocidad
      1.1 s) y reacciones 2/3 (DmgKnockBack con bloqueos, raíz y entrada 0.05 s).
      Falta (anotado según la regla de CLAUDE.md):
  - [ ] Reacción 4 (`DmgExtraHeavy`, Ogro 130000110): clips exportados (160110..160183), árbol en poise.md §7.2; no
        aplicada: el golpe solo baja la estabilidad.
  - [ ] Reacción 8 (`DmgTumble` + suelo 1.5 s + levantarse, Ogro 130000040): cadena sin decodificar; no aplicada.
  - [ ] Reacción 23 (Ogro 130000050 / 120): sin confirmar; no aplicada.
  - [ ] HipsIK de la reacción del jugador (Dmg_Rotation de ChrCommonParam 100: 15/10/15/8) y de la reacción 1
        (MoveHit, pelvis): **simplificación**, no aplicados (el rig de PAL no tiene pelvis aparte; el invasor sí lo
        hace en su renderer).
  - [ ] Reacción 1 dentro de `BT_Damage` (SM 0x187d, torso a peso 1): no portada.
  - [ ] Capa de la reacción 1: **simplificación** de alfas por hueso: cuerpo = Spine1 0.5, cabeza = Head 0.75,
        brazos = UpperArm 0.5 (Spine 0.4, Neck 0.6, Clavicle 0.6, Forearm 0.4 no se usan: los miembros de MC son un
        solo hueso).
  - [ ] Bloqueos de la reacción 1 (pistas del clip Light mezcladas): sin confirmar; no aplicados.
  - [ ] DmgDownOrUp modo 2 (por altura del golpe) se toma como "arriba" igual que en los mobs (decisión del usuario
        para los mobs, extendida al jugador: confirmar).
  - [ ] Caída del daño de estabilidad por distancia (DCP+0xb8) en cuerpo a cuerpo: sin confirmar; no aplicada.
  - [ ] Enemigos de datapack (`SoulsEnemy`) y mobs vanilla sin datos de DS2 (esqueleto, araña, creeper...): no quitan
        estabilidad ni hacen reaccionar al jugador. Mobs vanilla: decisión del usuario.
  - [ ] Segundo medidor (`chr+0x1b8`, reacción 10) y guardia (categoría 2): aparte.
- [ ] Falta de stats del equipo (`f` de la penalización de i-frames y de `ItemUse_Speed`): vale 0; no se sabe qué param
      tiene los requisitos por pieza (`FUN_14034d150`, u16 `+0x2c..+0x32`).
- [ ] `ItemUse_Speed` (velocidad de uso de objetos, columna `+0x78` × ItemParam `+0x38`): calculable, pero no se sabe
      qué nodo del nmb la usa. No aplicada a las animaciones de uso (almas, efigie).
- [ ] i-frames de `RollLanding` (140020, 110300 0..0.4333): no aplicados; no está trazado qué `b8+0xa4` rige ahí
      (`PlayerJumpCtrl` vt+0x48).
- [ ] Resistencia elemento 9 (`+0x54`, ¿maldición?) con el índice de AGL: sin lector comprobado; no portada.
- [ ] Petición 7 del roll (`a = 1.0`, coste 0): sin identificar.

## The Last Giant (castigo por romper una hoguera, usuario 2026-11-04; `SoulsGiants`, last_giant.md)
- [ ] Simplificación: el volumen de golpe es la banda de distancia y ángulo de la fila de la tabla que lanzó el ataque
      (salto atrás: 4.5 m a la redonda), no las cápsulas de las filas EDP en sus dummies (como el Ogro).
- [ ] Simplificación: las ondas de choque (balas 2301) usan el mismo alcance que el golpe, no su esfera.
- [ ] Simplificación: en el modo 1 camina / trota con los clips del modo 0 (los del modo 1 no están trazados).
- [ ] Autorizado por el usuario, sin confirmar en DS2: f7300 ≠ 0 en pelea (vuelve a la tabla) y f7003 = "objetivo a
      su derecha".
- [ ] No portado: reacciones 5 (BlowAway) y 8 (DmgTumble) que sus golpes provocan en el jugador (solo bajan la
      estabilidad); su animación de muerte (8.3 s) y la de aparición (StartUp 6 s); el giro durante los ataques
      (EnemyAttackMoveParam, bandera sin trazar); la daño de golpes a distancia y hechizos usan la fórmula genérica.
- [ ] Almas de gigante (Alma de un gigante 50920000, Alma del último gigante 64010000): no portada su velocidad de
      uso 0.7 (como las demás). El diálogo "¿Usar %s?" ya está (2026-11-10).
- [x] Desde 0.5.0 el Último Gigante suelta el Alma del último gigante (6000), como en DS2 (usuario, 2026-11-10).
- [ ] El Alma de un gigante (10 000) ya no la suelta nada: queda registrada (mundos existentes y creativo) sin fuente
      en el mod. Diseño del usuario si se le da otra.
- [x] Diseño del usuario (no DS2): el gigante sale cada 3 hogueras activas rotas por el mismo jugador
      (`SoulsGiants.BONFIRES_PER_GIANT`, contador `soulsmode:bonfires_broken`).
- [ ] **Los invasores NPC no tienen efectos visuales (VFX) de espíritu oscuro**: el usuario pidió el gigante "con los
      vfx de los invasores" y no existen. Investigar el efecto de los espíritus oscuros de DS2.

## Titanita y mejora de armas (próximo parche, usuario 2026-11-09; titanite.md, scythes.md, CHANGELOG.md)
- [x] **Implementar la titanita.** Mejorar armas por nivel DS2 con titanita, con los valores de
      `WeaponReinforceParam` y los costos de `ReinforceCostParam` (titanite.md). Todas las decisiones
      del usuario están tomadas (2026-11-09).
- [x] **Sistema de daño nivel DS2 × multiplicador de material.** Multiplicadores propuestos por Claude y aprobados
      por el usuario (2026-11-09): madera 0.67, piedra 0.83, hierro 1, oro 0.67, diamante 1.17, netherita 1.33.
      - Tiers de nivel máximo (usuario): +5 madera, piedra, oro y palo; +10 hierro, diamante y
        sin material; +15 netherita (oro y palo bajados a +5 por el usuario, 2026-11-09).
      - Reescalado a DS2: el máximo del tier equivale al máximo del arma en DS2.
      - Las armas +5 de DS2 (espada de piedra, hacha de diamante, espada de netherita) llegan al máximo de su tier.
- [x] Palo como tier propio, ×0.5 al ataque (`SoulsUpgrade.materialFactor`).
- [x] Azadas con la Hoz Platinegra (2100000).
- [x] El hacha y la azada siguen cumpliendo su función (`SoulsTools`). Probado en el servidor QA.
- [x] **Ataque lento por no cumplir requisitos, ventana 151** (implementado 2026-11-09, `SwingSpeed`,
      `SwingClient`; lack_of_stats.md). Dentro de la ventana 151 de `AttackSpeedControl` el clip corre a
      `WAMP+0x0c × (1 − pen8)`: PLSP fila 8 con la falta de Fuerza/Destreza y la falta de estamina para el costo.
      Probado en el servidor QA (modo `swing` de QaDriver): con el Explorador, la espada de madera da ×1.0 y el
      hacha de netherita (Fue 28) da ×0.966 (base 1.3 × 0.743).
- [ ] **Ventana 150 (inicio del golpe, fila 7): sin portar.** Corre a velocidad 1 porque su multiplicador
      `b8+0x200` no tiene escritor confirmado. Hace falta leer memoria con DS2 en ejecución.
- [ ] Ataques corriendo: en DS2 la ventana 150 usa `PlayerCommonParam +0x88/+0x8c` según la velocidad de
      movimiento en vez de la fila 7. Va junto con la ventana 150.
- [ ] Invasores NPC: sus ataques no usan la velocidad de golpe (`SoulsInvader` tiene su propio reloj).
- [ ] El segundo escritor de DS2 (`FUN_1403947b0`, cuando `ctx+0x48 ≠ 4`) pone la velocidad sin falta de stats;
      no se sabe cuándo pasa, así que el mod siempre aplica la falta.
- [ ] Sin confirmar en lack_of_stats.md:
  - [ ] el escritor de `b8+0x200` (ventana 150);
  - [ ] el segundo escritor `FUN_1403947b0` (qué es `ctx+0x48`);
  - [ ] `b8+0x195`;
  - [ ] las unidades de tiempo de la pista;
  - [ ] las otras tres llamadas a `FUN_140393350`;
  - [ ] qué son las filas 2, 3, 5, 13, 14 y 27;
  - [ ] filas sin consumidor: 12 y 20 a 25.
- [ ] PLSP filas 15, 16 y 17 (defensa y resistencias de la armadura con falta de stats) y 19 (multiplicador del
      costo de acción): con consumidor identificado, no portadas.
- [ ] Requisitos por material en picos y azadas: usan una sola arma DS2 para todos los materiales. Decisión del
      usuario.
- [ ] Peso de la azada: por ahora conserva el de su receta. Falta decidir si toma el peso DS2 de la Hoz Platinegra
      (4).
- [ ] **El combate con azada no está probado en el juego.** Compila y la prueba QA solo cubrió arar y cosechar;
      falta verla atacar con la Hoz Platinegra.
- [x] Titanita como ítems: fragmento, fragmento grande, trozo, losa y titanita centelleante; el hueso de dragón
      petrificado no lo usa ningún arma del mod. Faltan sus texturas (iconos DS2) y su obtención en Minecraft. Lo de
      DS2 (titanite.md): tiendas, drops por modelo de enemigo y tesoros fijos.
- [x] Mejora en la **mesa de herrería** (decisión del usuario, 2026-11-09; en DS2 la hace el herrero NPC,
      `FeGroupNpcMenuReinforce`). La mejora no choca con los encantamientos: se conservan y se suman encima.
- [x] Click con la rueda del mouse sobre el arma: la página del arma (`InventoryStats`) muestra el nivel de mejora
      guardado en el arma y el máximo de su tier (hoy muestra el nivel que da el material).
- [x] Yunque (decisión del usuario, 2026-11-09): al combinar dos armas o dos piezas de armadura con nivel de
      mejora, el resultado queda con el nivel más alto. No se promedian ni se suman.
- [x] Material por nivel (usuario): la ruta de DS2 reescalada al tier. Titanita normal: fragmento → grande → trozo
      → losa; centelleante: ×1 a ×5. El último nivel del tier siempre usa la losa (o la centelleante ×5).
- [x] Costo en niveles de experiencia (usuario; en DS2 son almas): mejorar a +N cuesta N niveles.
- [x] Sin plantilla en la mesa de herrería: arma o armadura más titanita (usuario).
- [x] Obtención de la titanita (usuario): cofres de estructura, drops de phantom, devastador, murciélago y vex, y
      comercio con el herrero de armas, el armero y el comerciante errante. 
      - Drop por mob (probabilidades de DS2): murciélago, fragmento 20 %; phantom, grande 15 %; vex, trozo 2 %;
        devastador, losa 2 % o centelleante 5 %.
      - Cofres: mazmorras y minas (fragmento y grande); saqueadores y mansiones (fragmento a trozo); fortalezas y
        bastiones (trozo y losa); ciudades antiguas (losa y centelleante).
      - Aldeanos por nivel: novato fragmento, aprendiz grande, oficial trozo, experto losa; el precio sale del valor
        de DS2.
      - Precios: 500 almas de DS2 = 1 esmeralda (fragmento 2, grande 5, trozo 12, losa 26). La centelleante la vende
        solo el comerciante errante (30), en pocas unidades.
      - Cofres: 1 a 3 unidades, no en todos.
      - Crafteo (usuario): 9 fragmentos = 1 grande; 9 grandes = 1 trozo; 9 trozos = 1 losa. La centelleante no se
        craftea. Al revés también: 1 losa = 9 trozos, 1 trozo = 9 grandes, 1 grande = 9 fragmentos.
      - Losa de titanita colocable (usuario): mismo tamaño que las losas de Minecraft. 2 losas = bloque de titanita,
        decorativo, como el de netherita pero más claro y trizado.
- [x] Mejora de armadura con los máximos de DS2 (usuario): la normal hasta +10, la especial (sets del Rey y de
      Drangleic) hasta +5. DS2: armadura normal hasta +10 con titanita; armadura especial (sets del Rey y de
      Drangleic) hasta +5 con titanita centelleante. Defensa ×2 en +10 y ~×1.5 en +5 (`ArmorReinforceParam`).
- [x] Infusiones de DS2 como encantamientos (usuario, 2026-11-09; implementadas Fuego, Magia, Electricidad,
      Oscuridad, Veneno, Básico y Encantado): salen de la mesa de encantamientos, de libros
      en cofres y del bibliotecario. DS2: 10 tipos, 2000 almas y una piedra, infusiones permitidas por arma (`CustomAttrSpecParam`),
      el nivel no se pierde (titanite.md §5).
- [x] Estabilidad de guardia por nivel en escudos. DS2: `WRP +0x74 + 4·nivel` (Escudo de Drangleic: 55 en +0, 63 en
      +5). El mod solo exporta la de +0 (`tools/re/weapons.py`, `guardStability`).
- [x] Infusión Hemorragia (2026-11-09, `SoulsBleed`, bleed.md). Probada en el servidor QA (acumulación, bajada y
      estallido contra un zombi).
- [ ] Simplificación: un golpe bloqueado con escudo no acumula sangrado. DS2 lo reduce por `(1 − guardia)` con un
      campo del escudo sin identificar (bleed.md "Sin confirmar").
- [ ] Sangrado sin portar: la ralentización del estallido (`100090[06]`, ×0.5 / ×0.7 3 s, sin confirmar), el
      sangrado que causan los enemigos al jugador, y el factor Z con varios jugadores en la sesión (ChrMultiplayParam).
- [ ] El Ogro de DS2 tiene 50 % de resistencia al sangrado, pero en el mod el Ogro es el golem de hierro, que el
      usuario dejó inmune.
- [x] Infusión Mundano (2026-11-09): PSPL `+0x8c` del stat más bajo × WTP `+0x13a` (bleed.md §8); test
      `InfusionTest` (espada larga, stat mínimo 10: 166 en +0, 222.5 en +10).
- [ ] Armas y armaduras de datapack: no se pueden reforzar (no traen ReinforceCostParam). Falta definir cómo las
      declara un datapack.
- [ ] Infusiones en el palo y el escudo: solo con libro en el yunque; la mesa de encantamientos no los encanta
      (no tienen encantabilidad en vanilla).
- [x] Decisiones de Claude en la titanita, aprobadas por el usuario (2026-11-09; no son de DS2):
  - [x] bloque y losa con propiedades del bloque de hierro (dureza 5, resistencia 6, cualquier pico);
  - [x] en los cofres, cada titanita de la lista con el mismo peso;
  - [x] ofertas de los herreros con 12 usos, experiencia 1 / 5 / 10 / 15 y multiplicador de precio 0.05 (los
        valores de vanilla); la del errante con 1 uso;
  - [x] los drops de mobs exigen que los mate un jugador (`killed_by_player`), como en DS2 los mata el jugador;
  - [x] encantamientos de infusión con los costos de Multidisparo (peso 2, 20 a 50, yunque 4) y en ambas manos;
  - [x] textura del bloque: la de netherita aclarada 35 % hacia gris claro, con 4 grietas en zigzag (rehechas: la primera versión tenía una forma inapropiada).
- [ ] Palo con Irrompibilidad: falta medir en el juego el desgaste golpe a golpe. En el QA (`tools/qa/stick_setup.js`)
      los golpes contra el zombi de prueba casi no conectan; revisar la prueba.
- [ ] Guadañas de DS2 sin portar: 8 guadañas reales con 3 movesets (Gran guadaña, Gran machete, Hoz de luna llena,
      Hoz de media luna, Nahr Alma, Guadaña de hueso, Guadaña del deseo, Forlorn) y la "Guadaña" 3210000
      (alabarda). La exportación ya se probó sin errores (scythes.md §4).
- [ ] Invasores NPC: solo eligen de las listas de espadas y hachas (`SoulsInvader`). No usan azadas ni el palo con
      su tier nuevo.
- [ ] Sin confirmar en titanite.md (investigar):
  - [ ] máximo de pila 99 y precios de tienda;
  - [ ] el código que aplica el refuerzo y la infusión (resta de almas y material, nivel + 1, la infusión
        conserva el nivel);
  - [ ] si imbuir cuesta lo mismo a cualquier nivel;
  - [ ] qué opciones da el menú de cada herrero, si Lenigrast imbuye y qué desbloquea la bandera 102680 de McDuff;
  - [ ] semántica de `ShopLineupParam +0x04/+0x08/+0x0c` y si 255 significa ilimitado;
  - [ ] `WRP +0x50..+0x70` como reducciones de guardia y si la infusión del escudo las cambia;
  - [ ] si el nivel cambia la durabilidad;
  - [ ] `ARP +0x64`, `WSA +0x04`, `PlayerWeaponReinforceParam`, el tercer byte de la tabla `0x1410c3e10` y la
        fila CAS 10;
  - [ ] nombres de los mapas y de los enemigos de los drops;
  - [ ] lotes por generador de mapa y tesoros colocados.
- [ ] Sin confirmar en scythes.md (investigar):
  - [ ] nombre de la categoría "Guadaña" en el menú;
  - [ ] cómo clasifica el juego la Hoz Platinegra (hoz o hacha);
  - [ ] desofuscar `FUN_14003c250` (lectura de WRP);
  - [ ] `WRP+0xe8 = 0` de la Guadaña del deseo;
  - [ ] filas sin nombre de WTP 150 (784000, 821000, 851000, 910000, 6410000);
  - [ ] rareza de las armas.

## Pendientes sin decisión del usuario (2026-11-09)
- [ ] Actualizar y reiniciar QA Server SAM con el jar nuevo (arreglo del crash de zombies en
      `SoulsPerception.Investigate` y cambios de hoguera). Necesita el OK del usuario.
- [ ] Hoguera: decidir si sigue quitando veneno y fuego al descansar (la leche y el agua ya lo hacen en Minecraft).

## Inventado sin autorización (viola la regla 1; listado 2026-11-01)
Comportamientos de juego que agregué sin sacarlos de DS2 y sin pedir permiso. Cada uno se reemplaza por lo de DS2
cuando se descifre, o se quita, según lo que decida el usuario.
- [x] Búfer de ataques (guardaba una pulsación hecha durante el bloqueo): **quitado** (2026-11-01). Lo de DS2 es la
      pista `InputControl` del nmb, sin descifrar.
- [ ] Empujón de la rodada / paso: dura 0,6 s / 0,3 s (`RollClient`). Lo de DS2: el movimiento raíz de la animación.
- [ ] Bloqueo solo de frente, ±90° (`SoulsAttack.guarded`). Lo de DS2: el volumen del escudo, sin descifrar.
- [ ] Área de golpe de los ataques del jugador: alcance vanilla y ±60° (`SoulsAttack`). Lo de DS2: el hitbox de la
      ranura de ataque, sin descifrar.
- [ ] Gravedad del ataque en salto: la de Minecraft (`JumpAttackLeap`). Lo de DS2: física ofuscada, sin descifrar.
- [ ] Al morir se reaparece con el arma a una mano (`SoulsMode`). Lo de DS2: no trazado.

## Revisiones pedidas
- [x] GuardSpeed (implementado 2026-11-01, weapon_arts.md s.10): toda guardia es la capa de SM 0x210e con
      GuardSpeed, alfas de una y dos manos y fundido de salida por categoría; pendiente de QA.
- [ ] Escudo (feedback de QA 2026-11-01: "el escudo es inútil"): revisar guardia, estabilidad, rangos de bloqueo,
      parry y costo de estamina frente a la decompilación de DS2, punto por punto.
      Énfasis del QA (2026-11-01): **el escudo no pierde durabilidad al cubrir** y **el parry es solo animación: no
      hace nada funcional** (no abre al atacante ni habilita el riposte).
      Además (QA 2026-11-01): al moverse con la guardia arriba el personaje no hace la animación de caminar
      cubriéndose. Sacar de DS2 la locomoción en guardia (animaciones de c0001 / nodos del nmb) y exportarla de los
      `.nsa`. Desde 2026-11-01 la guardia es la capa de SM 0x210e y debajo corre la locomoción normal (weapon_arts.md
      s.10); falta confirmar si la red 0x233e cambia de locomoción con la guardia arriba.
- [x] Arco (implementado 2026-11-01: Short Bow de DS2, `SoulsBow` / `BowAim`; carcaj en la cadera derecha por
      decisión del usuario; pendiente de QA y de lo "sin confirmar" de docs/research/bow.md) (feedback de QA 2026-11-01: "no funciona exactamente como en Dark Souls"): revisar contra la decompilación
      de DS2 (apuntar, tensar, disparo, estamina, daño, flechas) punto por punto.
      Detalle del QA: en DS2 se apunta con zoom y es cómodo; acá apuntar es más torpe que en DS2 y que en el MC
      original, un punto intermedio incómodo. Revisar el modo de apuntado de DS2 (cámara / zoom / sensibilidad / mira)
      y cómo el mod lo combina con el arco vanilla.
      Énfasis del QA (2026-11-01): el botón principal funciona tal cual, pero **con el arco también se puede apuntar
      con el otro botón**, y eso le quita mucha gracia al arco. Revisar qué entradas usa el arco en DS2 y quitar la que
      sobra.
      Además (usuario 2026-11-01): **las flechas se dibujan en la espalda y deben ir al costado** (carcaj de DS2).
      Sacar la posición de DS2 (dummy poly / modelo) en vez de ubicarla a ojo.
- [ ] Empuje al golpear (QA 2026-11-01): en modo Souls, al golpear a un oponente solo debe retroceder, sin el salto
      del knockback vanilla. Revisar contra DS2 cuánto y cómo retrocede (reacción al golpe / movimiento raíz).
- [ ] Mancha de sangre (QA 2026-11-01): recuperar las almas al tocar el charco de sangre no funciona ahora.
- [x] Weapon arts (investigado e implementado 2026-11-01: DS2 no tiene weapon arts tipo DS3; lo que hay es el ataque
      con la guardia puesta de la lanza, `GuardAttacks`, docs/research/weapon_arts.md; pendiente de QA. Armas nuevas con
      ataque especial: decisión del usuario) (pedido del usuario 2026-11-01): según el equipo del usuario, en DS2 algunas armas tienen ataques
      especiales. Investigar en el decomp qué armas, con qué entrada y con qué datos (sin suponer nada de DS3).
- [x] Power stance (implementado 2026-11-01, docs/research/powerstance.md; pendiente de QA) (pedido del usuario 2026-11-01): usar dos armas a la vez como en DS2. Investigar en el decomp
      requisitos, movesets y animaciones antes de implementar.
- [x] Reacción al daño (implementado 2026-11-01, `SoulsHitReaction`, docs/research/hit_reaction.md s.7; pendiente de QA;
      sin animación de reacción en el mob) (usuario 2026-11-01): en DS2 la mayoría de los oponentes no se pueden mover mientras están en
      la animación de daño; en Minecraft sí, y eso deja al jugador en desventaja. En modo Souls los mobs golpeados
      no deben seguir avanzando ni atacando durante su reacción. Sacar de DS2 la duración / condición (poise, tipo
      de reacción) antes de implementar. Tema distinto del empuje al golpear.
- [x] Invasiones NPC (usuario 2026-11-01; `SoulsInvasions`, `SoulsInvader`, docs/research/npc_invaders.md): tiradas cada
      5 min por lugar y noches sin dormir, NPC Steve con clase / nivel / equipo, IA ai820004 (tabla de ataques), daño
      DS2 en ambos sentidos, textos de DS2. `/souls invade` para QA. Pendiente de QA.
- [ ] Invasor (2026-11-01: esquivas, estamina y backstab hechos, npc_invaders.md s.5; pendiente de QA). Falta: la
      fila de patada (condición f5050(1) sin identificar), la reposición (f5212 / f5213 sin identificar); recompensa al
      vencerlo (DS2 da almas fijas por NPC; para nivel aleatorio lo decide el usuario).
- [x] Golem de hierro = Ogro de DS2 (c3000) (usuario 2026-11-02; `SoulsGolems`, `ds2/C3000Attacks`,
      `tools/re/ogre_anims.py`, docs/research/c3000_ogre.md): ataques 14 / 15 y combos 50 / 51 / 52 con la IA de
      ai300000, reacción al daño con los clips del Ogro (sale de `no_hit_reaction`), locomoción quieto / caminar /
      trotar; daño vanilla del golem; brazos girando en el hombro mientras suena un clip del Ogro. Sin agarre (acción
      13). Pendiente de QA y de lo "sin establecer" de c3000_ogre.md.
- [x] Llama de la adversidad (Bonfire Ascetic) (diseño del usuario 2026-11-03; `dev.soulsmode.ascetic`,
      docs/research/llama_adversidad.md): quemarla en la hoguera regenera 17 × 17 chunks como los generó el mundo
      (nivel temporal, ~30–40 s, ≤ 20 ms por tick salvo ~30–60 ms al abrir) y sube el ciclo de la zona (máx. 5): mobs más
      fuertes, botín extra, vindicadores de día, devastadores desde 4, generadores con más luz, phantoms sin insomnio,
      almas al matar. Comandos `/souls ascetic burn|info`. Probado por RCON; pendiente de QA con jugador (menú,
      vindicadores, generadores, phantoms, almas).
- [x] Nombres de hogueras (usuario 2026-11-03; `BonfireNames`, `ActiveBonfires`, `RenameScreen`): "Renombrar" en el
      menú de la hoguera, hasta 32 caracteres, compartido por todos, se borra con la fogata; el menú de viaje muestra el
      nombre (coordenadas en el tooltip) y el menú de la hoguera lo usa de título. Pendiente de QA en el juego.
- [ ] Auditoría del jugador de DS2 (usuario 2026-11-03, tras descubrir que la Agilidad no estaba porteada): lista
      completa de las mecánicas centrales del jugador de DS2 SotFS (stats y todo lo que cada uno mueve, combate,
      movimiento, distancia, magia, equipo, progresión) contra lo que hace el mod: implementada / parcial (un stat que
      existe pero no mueve nada es parcial) / ausente. Cada hueco se clasifica: límite de Minecraft, investigación
      pendiente o simplificación nuestra. Fuera de la lista lo cerrado (curación de DS2, mancha de sangre, hechizos
      propios, aspecto hueco, luz de antorcha, bolsa).
