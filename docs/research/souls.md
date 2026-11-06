# Almas

Objeto de estado del player: `chr+0x490` (ver [stats.md](stats.md)). Contadores (u32) con un byte de bloqueo detrás:
`+0xec` / `+0xf0`, `+0xf4` / `+0xf8`, `+0xfc` / `+0x100`. Si el byte de bloqueo ≠ 0, ese contador no se modifica.
Origen de esos bytes: desconocido.

## Ganar — `FUN_14038ab40(obj, cantidad)`
- Si el flag `chr.b8+0x4b8` bit 59 está activo, no hace nada.
- Para cada contador (`+0xec`, `+0xf4`, `+0xfc`): `cantidad < 999999999 - valor` (sin signo) ? `valor + cantidad` : `999999999`.
- Luego notifica `FUN_1402d8710(0x11 / 0x14 / 0x12)`.

## Gastar — `FUN_14038be30(obj, cantidad)`
`+0xec = cantidad < +0xec ? +0xec - cantidad : 0`. **Solo** baja las almas en mano.

## Confirmar level-up — `FUN_14038b1a0`
Copia el bloque de stats del menú y resta de `+0xec` el total gastado (`FUN_1401fb950`), con piso 0.
El menú recibe las almas desde `obj+0xec` (`FUN_1401fb530` → `FUN_1401fbac0`).

## Qué es cada contador
- **`+0xec` — almas en mano.** `FUN_1402005f0` lo devuelve. Es lo que usa el level-up y lo único que baja al gastar.
- **`+0xf4` — Soul Memory (probable).** Sube con cada ganancia y nunca baja al gastar.
  - `FUN_14003ebe0` (números del menú de estado) lo muestra justo después de nivel, almas y almas requeridas.
  - `FUN_1402aa540` lo envía por red junto al SL (`+0xd0`).
  - El texto 909050 "Soul Memory" existe, pero no encontré la referencia directa que lo una a este campo.
- **`+0xfc`** — otro total acumulado. `EventConditionOther_CompareTotalSoul` (`FUN_140470700`) compara contra `+0xf4` (tipo 0)
  o `+0xfc` (tipo 1). Diferencia: **sin resolver**.

Port: `ds2/Souls.java` (`+0xec` y `+0xf4`).

## Mancha de sangre — `NetSvrBloodstainManager` (ctor `FUN_14026aad0`)
Registro propio del jugador (único): `+0x2c` u8 hay mancha, `+0x2d` u8 ya procesado en esta muerte, `+0x30` u32 almas,
`+0x34..+0x48` ubicación (6 dwords que devuelve `FUN_1402120a0()->vfunc+0x80`, formato sin analizar).

- **Al morir — `FUN_14026af40`** (solo una vez por muerte, guardado por `+0x2d`):
  `perdidas = hayMancha ? mancha.almas : 0`, luego `hayMancha = 1`, `mancha.almas = obj+0xec` y `obj+0xec = 0`
  (salvo bloqueo `+0xf0`), y se guarda la ubicación. **La mancha anterior se sobrescribe: sus almas se pierden.**
- **Al recoger — `FUN_14026b500`**: si hay mancha, busca el objeto, reproduce el evento `0x2ee3`, borra el objeto,
  llama `FUN_14026b610(obj, mancha.almas)`, pone `hayMancha = 0` y notifica el evento 8.
- **`FUN_14026b610`**: suma **solo** a `+0xec` (con tope 999999999). **No suma a Soul Memory (`+0xf4`).**
- `FUN_14026aef0` limpia el registro y `FUN_14026b4c0` / `FUN_14026bb70` lo cargan y guardan (save).

Port: `Bloodstains.java`, `Souls.recover()`.
En Minecraft es el bloque plano `soulsmode:bloodstain`, que funciona así:
- **Aspecto**: como la redstone, sin colisión, irrompible e inamovible, con partículas de alma.
- **Datos**: una block entity guarda el dueño.
- **Recogerla**: clic derecho. Siempre muestra el efecto de partículas y sonido; solo el dueño recupera las almas.
- **Dónde se coloca** (decisión de diseño):
  1. El primer hueco seco, hasta 16 bloques por debajo del punto de muerte, que esté sobre una cara superior sólida.
  2. Si no hay, el último bloque donde el jugador estuvo en el suelo.
  3. Si tampoco hay, la mancha se pierde.

Pendiente: qué posición exacta guarda DS2 (`+0x34..+0x48`).

## Integración con Minecraft (decisión de diseño, no de DS2)
- **Almas en mano = puntos de experiencia de vanilla.** Se calculan desde nivel + progreso de la barra
  (`ExperienceSouls`), porque el `totalExperience` de vanilla se desincroniza al encantar o usar yunques.
- **Soul Memory** vive en el attachment `soulsmode:souls_data` y sube con cada ganancia de XP mientras se está en Modo Souls
  (`PlayerMixin` sobre `Player.giveExperiencePoints`).
- **Level-up**: `/souls levelup <atributo>` mientras no haya UI de hoguera.
  - Cobra `cost(SL)` y sube el atributo en 1.
  - Tope de 99: si un atributo pasa de 99, `FUN_14038d570` invalida el bloque entero.
- **Muerte**: no se sueltan orbes (`PlayerMixin.getBaseExperienceReward` devuelve 0); las almas van a la mancha y se
  reaparece con 0, también con `keepInventory`.
- **Vida máxima**: `SoulsStats` pone `MAX_HEALTH = vida DS2 / 40` (constante de diseño `DS2_HP_PER_HEALTH`).
  Un Deprived queda en ~19,4 y con todo en 99, en ~62,6.

## Pendiente
- Pérdida de almas al morir y bloodstain (`NetSvrBloodstain*`, `ChrRoundDropSoulParam`).
- Almas que dan los enemigos.
