# Hechizos y sintonía (lado del jugador)

Fuentes: Ghidra (`DarkSoulsII.exe`), `SpellParam` / `SpellSoulConsumeParam` / `PhysicalStatsPerLevelStatValuesParam` /
`RelatePhysicalStatToLevelStatParam` / `WeaponTypeParam` de la regulation, `SpEffect*.emevd`, `c000100_pl.tae`,
`c0001` (`.nsa`) e `ItemName.fmg` (solo para poner nombre a las filas).
Salidas en `D:\Reversing\DS2\out\sp\*` (decompilados `callers.c`, `c2.c`…`c17.c`, `inv.c`, `r1ab.c`, `r34a.c`, `r38f.c`).
El código ofuscado se siguió con `deob.py` (el de `out/py`, ver stamina.md).

**Nada de esto está portado.** No hay hechizos en el mod.

## Params
- `SpellParam`: índice `0x23`, slot `paramMgr+0x540`. Fila de **0x108 bytes**; id = id del objeto (`ItemName.fmg`,
  p. ej. 31010000 = Soul Arrow). Getters por id (búsqueda binaria `FUN_14003e8f0` sobre `+0xc8`):
  `FUN_140359030(paramMgr, id)` y `FUN_1401ab840(id)`.
- `SpellSoulConsumeParam`: índice `0x24`, slot `+0x550`, getter `0x140359000` (salta a `0x141b7e643`). Fila de 0x30 bytes.
  Ver "Coste en almas".

## Estructura de `SpellParam` (0x108 bytes)

"Lector" = función que lee el campo y fija su significado. "Solo datos" = el significado no tiene lector rastreado.

| Offset | Tipo | Campo | Lector / evidencia |
|---|---|---|---|
| `+0x00` | u32 | **escuela** 0..4 | `FUN_140397a30`: elige qué flag del catalizador hace falta (ver "Catalizador"). Ids: 0 = 31xxxxxx (hechicerías), 1 = 32xxxxxx (milagros), 2 = 33xxxxxx (piromancias), 3 = 34xxxxxx, 4 = 35xxxxxx (los dos últimos son hexes por nombre). El menú la copia (`FUN_1401b8e50`, `menú+4`). |
| `+0x04` | u8 | ? | `0x140397bda` (selector de bloque, agarre 5): si vale 0, el agarre 5 usa los bloques del agarre 4. En `FUN_140395930`, si es ≠ 0 con agarre 5 y mano 0, el cast pide **2** usos. Vale 0 en todas las filas. |
| `+0x05` | u8 | **condición de objetivo** | `FUN_1401a46a0` (vía `FUN_140397a00`): 0 = siempre; 1 = el arma de la mano debe tener `+0x15 == 0` y `+0x50 & 2` (encantar arma: Magic Weapon, Flame Weapon, Sunlight Blade, Dark Weapon, Resonant Weapon, Hidden Weapon); 2 = lo mismo con `+0x15 == 3` (Strong Magic Shield); 3 = Homeward (`FUN_140513600/610`); 4 = Guidance (falla si `FUN_140513130 > 0`); 5 = nunca. Qué param es la fila del arma devuelta por `thunk_FUN_141c5212f`: **sin identificar**. |
| `+0x08` | u16 | **requisito de Inteligencia** | `FUN_1403978c0`: se compara con el atributo efectivo de la ranura 6 (Inteligencia). El menú lo copia (`menú+8`). |
| `+0x0a` | u16 | **requisito de Fe** | `FUN_1403978c0`: se compara con la ranura 7 (Fe). El menú lo copia (`menú+0xa`). |
| `+0x0c`, `+0x10` | u32 ×2 | id de bala | Solo datos: igual al id del hechizo, y son filas de `BulletParam` (31010000…). Lector sin rastrear. |
| `+0x14..+0xb3` | 8 × 0x14 | **bloques de lanzamiento** | `thunk_FUN_141c37a31` (`0x140397ba0`), ver abajo. |
| `+0xb4` | f32 | ? | Solo datos: 1.0 en todos los hechizos. |
| `+0xb8` | u8 | ? | Solo datos: 1 casi siempre, 0 en algunos milagros/hexes de curación o área. |
| `+0xb9` | u8 | flag | `FUN_140395930` / `FUN_1401fac60`: copiado como bool a la petición (`+0x61` / `+0x2a`). Vale 1 en Heal, Med Heal, Great Heal(Excerpt), Soothing/Bountiful Sunlight, Caressing Prayer, Magic Barrier y Sacred Oath. Qué hace: **sin rastrear**. |
| `+0xba` | u8 | ? | Solo datos: 1 en las lanzas de rayo, Soul Flash, Fire Snake, Dance of Fire, Dark Greatsword y Recollection. |
| `+0xc0` | u32 | ? | Solo datos: 5350 hechicerías, 5351 milagros, 5352 piromancias, 5353 hexes, y otros valores (5117–5120 bolas de fuego, 5060–5064 lanzas, 5152 Resonant Soul…). No coincide con `WeaponActionCategoryParam` (no existe una fila 5351). **Sin identificar.** |
| `+0xc4..+0xcf` | bytes / u32 | ? | Solo datos: `00 c8 c8 00/01`, luego un u32 (5023, 5024, 5034…) y `00/01 c8/f9 c8/f9 00`. **Sin identificar.** |
| `+0xd0..+0xd3` | bytes | ? | Solo datos: `+0xd3 = 1` en las piromancias. |
| `+0xd4`, `+0xd8`, `+0xdc` | f32 ×3 | ? | Solo datos: 50, 50, 50 (70 en `+0xdc` para Combustion). |
| `+0xe0`, `+0xe4`, `+0xe8` | f32 ×3 | ? | Solo datos: p. ej. Soul Arrow 1.2 / 1.05 / 1.0. **Sin identificar.** |
| `+0xf0` | u8 | **ranuras de sintonía que ocupa** | `FUN_1401af030` / `FUN_1401af0d0` lo copian a `entrada+0x21`, y `FUN_1401b40f0` suma `+0x21` contra la capacidad de sintonía. El menú lo copia (`menú+1`). |
| `+0xf1..+0xfa` | u8 ×10 | **usos por nivel 1..10** | `FUN_1401ab750`: `fila[0xf0 + nivel]`, donde nivel = derivada `+0x03` (1..10, de Sintonía). Ver "Usos". |
| `+0xfc` | u32 | id de `SpellSoulConsumeParam` | Solo datos: en los hexes 35030000, 35040000, 35050000, 35060000, 35070000, 35080000 y 35310000 vale el id de la fila de `SpellSoulConsumeParam`; en el resto vale 0. Lector sin rastrear. |
| `+0x100`, `+0x104` | u32 ×2 | id de bala | Solo datos: igual al id del hechizo, o id + 1000 en los encantamientos de arma (31181000…). Todos existen en `BulletParam`. |

### Bloques de lanzamiento (`+0x14 + k·0x14`, k = 0..7)
Selector `0x140397ba0(fila, agarre, acción)`. El agarre viene de `FUN_140397a80` (ver [weapons.md](weapons.md): 2/6 una
mano, 3 dos manos, 4 dual, 5 estados 5/6). La acción es el tipo de acción del botón (`decisión+9` / `+0x21`,
`FUN_140367b60`):

| Agarre | Acción 1 o 3 | Acción 2 o 4 |
|---|---|---|
| 2, 6 | `+0x14` | `+0x28` |
| 3 | `+0x3c` | `+0x50` |
| 4 (y 5 si `fila+0x04 == 0`) | `+0x64` | `+0x78` |
| 5 con `fila+0x04 ≠ 0` | `+0x8c` | `+0xa0` |
| otro | sin bloque (no lanza) | |

Bloque (lectores `FUN_140395930` local / `FUN_1401fac60` y `FUN_1401feab0` red):

| Offset | Tipo | Uso |
|---|---|---|
| `+0x00` | f32 | **sin lector rastreado** (0–1.75) |
| `+0x04` | f32 | `max(v, 1.19e-7)` → petición `+0x5c` / `+0x24`. Uso **sin rastrear** (0 en Soul Arrow, 1.8 Firestorm, 2.8 Darkstorm…). |
| `+0x08` | u32 | **animación 1** (TAE) → petición `+0x4c` / `+0x14` |
| `+0x0c` | u32 | **animación 2** (TAE) → petición `+0x50` / `+0x18` |
| `+0x10` | f32 | **sin lector rastreado** (0.8–3.0) |

**En la regulation actual los 8 bloques de cada fila son idénticos**, así que agarre y acción no cambian nada.
Las animaciones son `.nsa` de `c0001`: `a38_11_*` = `StickSpell1HandLight…` (bastón) y `a39_11_*` = `BellSpell1HandLight…`
(campana). Ejemplos (duración `.nsa+0x28` / fin del último evento TAE):
- Soul Arrow / Dark Orb: `38110010` (0.5 s / 0.6 s) → `38110020` (2.17 s / 1.5 s; dos eventos TAE 2300 en 0.567–0.667 s).
- Heal: `39110110` (1.0 / 1.17 s) → `39110120` (2.83 / 2.33 s; 2300 en 0.667–0.767 s).
- Fireball: `38111110` (1.0 / 1.33 s) → `38111120` (1.83 / 1.5 s; 2300 en 0.767–0.867 s).

Que el 2300 sea el evento que dispara la bala **no está confirmado**. Ninguna de estas animaciones tiene evento 2200
(el que cobra la estamina de los ataques).

## Sintonía: ranuras y lista de hechizos sintonizados
Inventario del jugador (objeto `ItemInventory2…`; los hechizos poseídos son una `ItemInventory2SpellList`, vtable
`0x1410c4288`, entradas de 0x48 bytes):
- Entrada de hechizo: `+0x14` id, `+0x1c` handle, `+0x1f` bit 1 = sintonizado, **`+0x20` u8 usos restantes**,
  **`+0x21` u8 ranuras** (= `SpellParam+0xf0`).
- Sintonizados: arreglo de **14** punteros en `inv+0x25910..+0x25980` (categoría 7, slots de equipo `0x1c..0x29`).
- `inv+0x259ec` u8 = **capacidad** (ranuras utilizables). `inv+0x259ed` u8 = bono. `inv+0x259ef` s8 = **hechizo activo**
  (índice en el arreglo; −1 = ninguno).

**Capacidad** (`FUN_1401b40f0`):
```c
cap = min(14, derivada[+0x02] + inv+0x259ed);   // derivada +0x02 = ranuras de sintonía (stats.md)
acum = 0;
for (i = 0; i < 14 && sint[i]; ) {
    acum += sint[i]->ranuras;                       // +0x21
    if (acum > cap) quitar(i);                      // FUN_1401b4330; la lista se compacta, se vuelve a mirar i
    else i++;
}
```
- `derivada+0x02` = `PhysicalStatsPerLevelStatValuesParam[Sintonía].u8@+2` (`RelatePhysicalStatToLevelStatParam[0] = 4`).
- El bono `+0x259ed` lo escribe `FUN_1401b42f0` (tope 14), llamada desde `FUN_1401a5da0` / `FUN_1401a7d50`.
  **De dónde sale el bono (anillos, etc.): sin rastrear.**
- Al equipar (`FUN_1401b3d50`), un slot `0x1c + n` con `n ≥ cap` se desequipa.
- Copias del mismo hechizo en posiciones contiguas cuentan como **un grupo** (ver "Usos").

| Sintonía | Ranuras (`+2`) | Nivel de usos (`+3`) |
|---|---|---|
| 1–9 | 0 | 1 |
| 10–12 | 1 | 1 |
| 13–14 | 2 | 1 |
| 15 | 2 | 2 |
| 16–19 | 3 | 2 |
| 20–24 | 4 | 2 |
| 25 | 5 | 2 |
| 26–29 | 5 | 3 |
| 30–31 | 6 | 3 |
| 32–37 | 6 | 4 |
| 38–39 | 6 | 5 |
| 40–42 | 7 | 5 |
| 43–48 | 7 | 6 |
| 49 | 7 | 7 |
| 50–57 | 8 | 7 |
| 58–59 | 8 | 8 |
| 60–74 | 9 | 8 |
| 75–78 | 10 | 8 |
| 79–93 | 10 | 9 |
| 94–99 | 10 | 10 |

(Tabla sacada de `PhysicalStatsPerLevelStatValuesParam` bytes `+2` / `+3`, por valor de Sintonía efectiva.)

## Usos
**Máximo** — `FUN_1401ab750(id)`:
```c
nivel = (s8) derivada[+0x03];                       // bloque de atributos +0x2f; de Sintonía (relate[1] = 4)
max   = (int) ceilf((float) SpellParam[id].u8[0xf0 + nivel] * chr.b8+0x484);
```
- `thunk_FUN_140c356d8` es `ceilf` (desensamblado: trunca y suma 1.0 si había decimales).
- `chr.b8+0x484` = índice **50** del arreglo de multiplicadores `b8+0x3bc` (`SpEffectActionImpl_ChrStatus_Rate`,
  `1000[01]`; el ctor lo pone a 1.0, ver equipload.md). SpEffects que lo cambian:
  Northern Ritual Band 40340000/01/02 → ×1.1 / 1.2 / 1.3; Saint's Hood 27690100 y Hexer's Hood 27700100 → ×1.05;
  SpEffect 3930000 (id de Azal's Staff) → ×0.25. Cuándo se aplica cada uno: **sin rastrear**.
- **Solo la Sintonía cambia los usos** (vía `+0x03`); ningún otro atributo entra en la fórmula.

**Al obtener un hechizo** (`FUN_1401af030`, vtable +0x20): `usos = max`, `ranuras = +0xf0`. Al cargar
(`FUN_1401af0d0`, +0x18): usos guardados.

**Usos de un grupo** (`FUN_1401b1a90`): suma de `+0x20` de las entradas contiguas con el mismo id, empezando por el
hechizo activo. Es lo que se compara al lanzar.

**Gastar** — al disparar (`FUN_1401b3b10` prepara, `FUN_1401b4670` cobra):
1. Prepara: dentro del grupo del activo toma **la primera copia con usos > 0**; guarda su handle en `inv+0x259ea`
   (−1 si no hay).
2. Cobra: si la copia tiene usos > 0, `usos = clamp(usos − 1, 0, max)` (`FUN_1401aee80(…, −1)`); si quedaba 1, borra el
   handle. Pone el bit 2 de `inv+0x259f0` = "se gastó".

**Recuperar** — `FUN_1401b2e80(pct, redondeoArriba)` (por `FUN_1401ac360` / `FUN_1401a7250`):
```c
R(max) = redondeoArriba == 1 ? (int)(ceilf(max * pct * 0.01) + 0.1)
                              : (int)(floorf(max * pct * 0.01) + 0.1);   // FUN_1401ab700
```
1. Hechizos poseídos **no sintonizados** (`+0x1f & 2 == 0`) y la segunda lista `inv+0x259d0` (categoría 7):
   `usos = clamp(usos + R(max propio), 0, max)` (`FUN_1401aef20`, `FUN_1401aebf0`).
2. Sintonizados: por id suma los máximos de sus copias, calcula `R(Σmax)` y lo reparte **de la última posición hacia la
   primera**; cada copia se llena hasta su máximo y pasa el sobrante a la siguiente del mismo id (`FUN_1401aee80` devuelve
   el exceso).

Quién llama:
- `FUN_1400c9f50` (confirmar subida de nivel) y una rutina de stats en `0x14038b594` llaman `(0, 0)`: **solo recorta los
  usos al nuevo máximo**, no rellena.
- `0x14014c60d` es un envoltorio que pasa `(float, byte)` recibidos de `0x14070d9af`. La cadena exacta desde el grupo
  `Spell` del SpEffect (`caseD_6` `14022cf60` guarda `pct × factor` en `+0x50` y el flag en `+0x54`) hasta aquí
  **no está trazada del todo**.
- SpEffects `100060[00] (pct, 1)`:

| SpEffect | Origen | pct |
|---|---|---|
| 110000010 | descansar en la hoguera / reaparecer (bonfire.md) | 100 |
| 100000020 | sin rastrear | 100 |
| 60040000 | Amber Herb | 35 |
| 60050000 | Twilight Herb | 50 |
| 60060000 | Wilted Dusk Herb | 70 |
| 60110000 | Rouge Water | 50 |
| 60120000 | Crimson Water | 70 |
| 21630110 | SpEffectArmor, sin nombre en `ItemName` | 20 |

Todos usan flag 1 (redondeo hacia arriba).

## Elegir el hechizo activo
- `FUN_14038f200`: si la entrada `9` del objeto de entrada (`chr+0xe8 → +0x18`, `thunk_FUN_141b10376(…, 9)`) está
  activa → `FUN_140349cd0` → `FUN_1401b2870`. **Qué botón es la entrada 9: sin confirmar.**
- `FUN_1401b2870` (siguiente): avanza el índice dando la vuelta en `cap`. **Salta las copias del mismo id** y se detiene
  en el primer hechizo de otro id. Si solo encuentra copias del mismo, se queda ahí. Si no hay nada, pone −1. Copia el
  índice en el guardado (`+0xf001`).
- `FUN_14034ba80` refresca `equipo+0x508` = índice activo (`FUN_1401abbb0` → `inv+0x259ef`).
  `FUN_14034a1d0` guarda el id en `equipo+0x428 + i·0x10` y la fila en `+0x430 + i·0x10` (i < 14).
- `FUN_1401b3c90(n)` fija el índice (n = 0 → último; si no, `n−1` y avanza). Sin rastrear quién lo llama.

## Lanzar: comprobaciones — `FUN_140395930` → `FUN_140396ba0` → `FUN_1403963b0`
Solo si la acción de la mano es `0xc` (`decisión+0xc + mano·0x18 == 12`). Hechizo = activo (`FUN_140348eb0(equipo, −1)`).
`FUN_140396ba0` da 0 si el agarre no es 2..6 o la acción no es 1/2. Si no, da un código y **pasa a 1 (fallo) si
`FUN_1403963b0` es falso**:
1. `thunk_FUN_141ce2883` verdadero → fallo (**sin identificar**; ofuscado).
2. **Usos**: `usosGrupo(activo) < necesarios` → fallo. Hace falta 1 uso (2 en el caso de `+0x04` de arriba).
3. **Requisitos** (`FUN_1403978c0`):
   ```c
   red = tabla(DAT_1416148f0+0xa8 → +0xc0).buscar(id de equipo);  // FUN_14019e110, short; 0 si no está
   faltaInt = max(0, SP+0x08 − (red & 0xff));
   faltaFe  = max(0, SP+0x0a − (red >> 8));
   ok = attrEfectivo[6] >= faltaInt && attrEfectivo[7] >= faltaFe;    // bloque+0x16+slot·2
   ```
   Qué id busca (`FUN_140348a80(equipo, thunk_FUN_141b4b64a(equipo, −1))`) y qué tabla es: **sin identificar**.
   Con la tabla vacía, el requisito es el valor del param.
4. **Catalizador** (`FUN_140396510` → `FUN_140397a30`): el arma de la mano (`thunk_FUN_14014cbeb(equipo, −2 / −1)` →
   ranura `+0x68` = fila de `WeaponTypeParam`, ver `FUN_14034a390`) debe tener ≠ 0 el byte que pide la escuela:
   0 → `WTP+0x00`, 1 → `+0x01`, 2 → `+0x02`, 3 → `+0x03`, 4 → `+0x53`. Datos:

   | `+0x00/01/02/03/53` | Armas |
   |---|---|
   | 1 0 0 0 0 | Staff of Wisdom, Blue Flame, Sorcerer's Twinblade |
   | 1 0 0 1 0 | Sorcerer's Staff y casi todos los bastones (incl. Azal's), Pilgrim's Spontoon |
   | 0 1 0 0 1 | campanas (Cleric's Sacred Chime…), Mace of the Insolent |
   | 0 0 0 0 1 | Caitha's Chime |
   | 1 1 0 1 1 | Black Witch's Staff, Sanctum Shield |
   | 0 0 1 0 0 | Pyromancy Flame, Dark Pyromancy Flame |

   Si falla, `FUN_140395f40` (solo deja un registro) y fallo.
5. **Objetivo** (`FUN_140397a00` → `FUN_1401a46a0`): ver `SpellParam+0x05`.

**Resultado** (`FUN_140395930`):
- Comprobación ok → petición con la fila, las animaciones 1 / 2 del bloque y `+0x5c = bloque+4`.
- Fallo (código 1: sin usos, sin stats, sin catalizador, etc.) → animación **`0x24596d6` = 38115030 =
  `a38_11_5030_StickSpellNoBullet`** (2.0 s) y fila nula: se hace el gesto y no sale bala.
  En las comprobaciones no se gasta nada (el gasto va en el evento, abajo).
- Código 0 → no hay petición.

## Al disparar — `FUN_14038f0c0` / `FUN_14037d300` (objeto `chr+0x188`)
Manejador de eventos: compara el id del evento (`evento+0x40`) con los ids guardados en `obj+0x14/+0x18/+0x1c/+0x20`.
Si es `obj+0x20` y la mano tiene marcado `obj+0x10+mano`:
1. `FUN_140348590` → `FUN_1401abb30` → `FUN_1401b4670`: **−1 uso** (arriba).
2. Si `obj+0x3c ≠ 0` → `FUN_14016a300(vida, obj+0x3c)`: resta PS; acota entre `+0x16c` y `+0x170`. Si `FUN_14016d8f0`
   es verdadero, no baja de 1.
3. Si `obj+0x40 ≠ 0` → `FUN_14038be30(estado, obj+0x40)`: `almas = max(0, almas − coste)` (`estado+0xec`), salvo si
   `estado+0xf0 ≠ 0`.

Quién escribe `obj+0x3c` / `+0x40` y qué evento TAE es `obj+0x20`: **sin rastrear**.

## Coste en almas — `SpellSoulConsumeParam`
Fila de 0x30 bytes: `+0x00` u8 **modo**; `+0x04` i32 **coste**; `+0x08..+0x18` 5 ids (niveles T0..T4);
`+0x1c..+0x2c` 5 ids alternativos. Los ids son filas de `BulletParam`.
Código (trozo ofuscado en `0x1405ba210`, getter `0x140359000`), con `almas = estado+0xec`:
- **Modo 0**: `r = (coste − almas) / coste`. T0 si `r ≤ 0` (almas ≥ coste); T1 si `r ≤ 0.2`; T2 si `r ≤ 0.6`; T3 si
  `r < 1.0`; si no, T4. **Cobro = coste.**
- **Modo 1**: `s = almas / coste`. T0 si `s > 1.0`; T1 si `s > 0.7`; T2 si `s > 0.4`; T3 si `s > 0`; si no, T4.
  **Cobro = todas las almas.**
- Salida `{id, id, cobro}`. Usa la columna alternativa si `[rbx+4] == 6`, o `== 3` con `[rbx] == esi`. Qué representan:
  **sin identificar**. Solo Resonant Weapon tiene ids alternativos distintos (35071000…).
- Constantes: 0.2 `0x1410ad5e0`, 0.6 `0x1410e548c`, 1.0 `0x1410ac698`, 0.7 `0x1410e21f0`, 0.4 `0x1410e5488`.
- Que este cobro sea el que llega a `obj+0x40`: **sin confirmar** (no se encontró la entrada de la función).

| Fila | Hechizo | Modo | Coste |
|---|---|---|---|
| 35030000 | Resonant Soul | 0 | 100 |
| 35040000 | Great Resonant Soul | 0 | 500 |
| 35050000 | Climax | 1 | 5000 |
| 35060000 | Resonant Flesh | 0 | 2000 |
| 35070000 | Resonant Weapon | 0 | 2000 |
| 35080000 | Lifedrain Patch | 0 | 3000 |
| 35310000 | Dark Dance | 0 | 5000 |

## Velocidad de lanzamiento — sin confirmar
- **No se encontró ningún código que cambie la velocidad de la animación de un hechizo según un atributo.** Las
  animaciones salen fijas del bloque de `SpellParam`.
- Candidata sin consumidor: la derivada `+0x0c` (`FUN_14038d790`, `0x14038d8a1`):
  `fila((2·Sintonía + Int + Fe) / 4).f32@+0x34` de `PhysicalStatsPerLevelStatValuesParam` (1 → 1.5, 10 → 1.428,
  30 → 1.268, 50 → 1.108, 99 → 0.716). Ni la búsqueda directa (`estado+0x40`) ni los lectores obvios la usan.
  **No se puede afirmar que sea la velocidad de lanzamiento.**
- La Destreza no aparece en ningún cálculo de hechizos rastreado.

## Estamina al lanzar — sin confirmar
- No se encontró el cobro. Las animaciones de hechizo revisadas no tienen evento TAE 2200, y el `WeaponActionCategoryParam`
  de los catalizadores (3800, 4010, 5400) no tiene animaciones `a38_11` / `a39_11`. Así que no usan la vía de los ataques
  (stamina.md).
- Tampoco se encontró una comprobación de estamina en `FUN_1403963b0`.
- Los floats de bloque `+0x00` / `+0x10` y `SpellParam +0xd4..+0xe8` no tienen lector rastreado; **no se les asigna
  significado**.

## Filas de referencia
Campos: escuela `+0`, requisitos `+8/+0xa`, ranuras `+0xf0`, usos `+0xf1..+0xfa` (nivel 1..10), bloque
(`+0x14`: f0, f4, anim1, anim2, f10), `+0xb8..+0xba`, `+0xc0`, `+0xd4..+0xe8`, balas `+0x0c/+0x10/+0x100/+0x104`.

| Campo | Soul Arrow 31010000 | Heal 32010000 | Fireball 33010000 | Dark Orb 34010000 | Resonant Soul 35030000 |
|---|---|---|---|---|---|
| escuela | 0 | 1 | 2 | 3 | 4 |
| Int / Fe | 10 / 0 | 0 / 12 | 0 / 0 | 12 / 10 | 10 / 18 |
| ranuras | 1 | 1 | 1 | 1 | 2 |
| usos 1..10 | 30 31 32 33 34 35 36 37 38 40 | 3 3 3 4 4 4 5 5 5 6 | 8 8 9 10 11 11 12 13 14 15 | 20 21 22 23 24 25 26 27 28 30 | 20 21 22 23 24 25 26 27 28 30 |
| bloque | 0.25, 0, 38110010, 38110020, 0.8 | 1.0, 0.5, 39110110, 39110120, 2.5 | 0.25, 0, 38111110, 38111120, 0.8 | 0.25, 0, 38110010, 38110020, 0.8 | 0.25, 0, 39110010, 38110020, 1.25 |
| `+0xb8 b9 ba` | 1 0 0 | 0 1 0 | 1 0 0 | 1 0 0 | 1 0 0 |
| `+0xc0` | 5350 | 5351 | 5117 | 5353 | 5152 |
| `+0xc4..c7` | 00 c8 c8 00 | 00 c8 c8 00 | 00 c8 c8 01 | 00 c8 c8 00 | 00 c8 c8 00 |
| `+0xd3` | 0 | 0 | 1 | 0 | 0 |
| `+0xd4..+0xe8` | 50 50 50 1.2 1.05 1.0 | 50 50 50 0.7 1.2 1.0 | 50 50 50 1.4 1.0 1.0 | 50 50 50 1.2 1.05 1.0 | 50 50 50 1.2 1.05 1.0 |
| `+0xfc` | 0 | 0 | 0 | 0 | 35030000 |
| balas | 31010000 ×4 | 32010000 ×4 | 33010000 ×4 | 34010000 ×4 | 35030000 ×4 |

Usos máximos con multiplicador 1.0: Soul Arrow con Sintonía 10 → nivel 1 → **30**; con 99 → **40**.
Heal con Sintonía 10 → **3**. Con Northern Ritual Band+2 (×1.3), Soul Arrow con Sintonía 10 → `ceil(39.0)` = 39.

## Pendiente
- Velocidad de lanzamiento y coste de estamina (arriba).
- Qué hacen la bala (`+0x0c/+0x10/+0x100/+0x104` → `BulletParam`), `+0xb4`, `+0xb8..+0xba`, `+0xc0..+0xe8` y los floats
  `+0x00` / `+0x04` / `+0x10` de los bloques.
- De dónde salen el bono de ranuras (`inv+0x259ed`) y la tabla de reducción de requisitos (`FUN_14019e110`).
- Qué botón es la entrada 9, y qué id de evento TAE gasta el uso.
- Quién escribe el coste en PS / almas del objeto `chr+0x188`.
- La cadena completa grupo `Spell` del SpEffect → `FUN_1401ac360`.

## Condición de objetivo 1/2 (encantar arma / escudo)
`SpellParam +0x05` = 1 o 2, en `FUN_1401a46a0` (llamado por `FUN_140397a00`, último paso de `FUN_1403963b0`).
Salidas: `D:\Reversing\DS2\out\sp\c17.c`, `c11.c`, `c10.c`, `c9.c`, `out\equipload\eq34.c`, `out\damage\a390.txt`.

**Qué mano**: `mano = −1 − (lado ≠ 0)`, con `lado` = la mano que lanza (`FUN_140395930(…, param_2)` →
`FUN_140396ba0` → `FUN_1403963b0(…, param_4)` → `FUN_140397a00(…, param_4)`). El catalizador usa la contraria:
`FUN_140396510` pide `(lado ≠ 0) − 2`. Con −1 = ranura impar = derecha y −2 = par = izquierda (dummypoly.md §1):
- lanzar con la **derecha** (lado 1): catalizador en −1 (derecha), objetivo en **−2 (izquierda)**;
- lanzar con la **izquierda** (lado 0): catalizador en −2, objetivo en **−1 (derecha)**.
- `FUN_14051d5a0` (b990_users.c) hace lo mismo: catalizador −2 con objetivo `lado 0`, catalizador −1 con `lado 1`.
- Coincide con speffects.md §11.6 (Magic Weapon lanzado con la derecha → ranura 26 = arma izquierda).
- **El objetivo es siempre el arma de la otra mano.**

**Comprobaciones** (todas obligatorias; si una falla, falso):
1. `equipo+0x10 ∉ {2, 3}` (`1 < +0x10 − 2`). Es el estado de las manos; 2/3 = a dos manos (stamina.md, estabilidad).
   **A dos manos no se puede encantar nada.** Qué valor es cada mano: sin confirmar.
2. Fila A = `thunk_FUN_141c5212f(equipo, mano)` = **`WeaponParam`** del arma de esa mano (desofuscado,
   `FUN_140349259` → `0x141cf7f20`):
   - lado `= índice & 1`, con `índice = FUN_14034e6a0(equipo, mano)`;
   - si `equipo+0x200 + lado·0x48 ≠ −1` → `+0x208 + lado·0x48`; si no → `equipo + (lado·3 + índice/2)·0x48 + 0x58`;
   - `+0x58` de cada entrada = `FUN_140359150` = getter de `WeaponParam` (escrito en `FUN_14034a390`, `0x14034a553`);
   - `+0x200 / +0x248` es el id de la **antorcha** en la mano: `FUN_140349580` → `FUN_14034f110` (`id == 6000000`).
   - Cond. 1: **`WeaponParam +0x15 == 0`**. Cond. 2: **`== 3`**.
3. Fila B = `FUN_1401a4740` = **`ItemParam`**:
   - id = `FUN_1403490e0(equipo, mano)` → `FUN_140348a80`: antorcha (`+0x200`) si la hay, si no `entrada+0x50`
     (el id del arma);
   - recorre `FUN_1401ba720(*(DAT_1416148f0+0xa8))` = `objeto+8` → `+0xc8`; `FUN_1401ba7f0` carga
     `ItemParam.param` en `objeto+8` (`ItemTypeParam` `+0x18`, `ItemUsageParam` `+0x28`);
   - devuelve la **primera fila con `+0x14 == id`** (búsqueda lineal; `FUN_140b035d0` + `FUN_14003e8f0`);
   - sin fila → falso.
   - **`ItemParam +0x50 & 2 ≠ 0`** (byte). Todos los valores: 0 (1005 filas), 2 (102), 3 (168).

**Qué significan los campos (por los datos, el nombre del campo no está en el exe):**
- `WeaponParam +0x15` (u8): 0 = cuerpo a cuerpo y catalizadores (369 filas), 1 = arcos y grandes arcos (17;
  `WTP+0x35` 1/2), 2 = ballestas (9; `WTP+0x35` 3), 3 = escudos (78; 11000000…).
- `ItemParam +0x14`: id de `WeaponParam` (−1 en lo que no es arma).
- `ItemParam +0x50` bit 1: **se puede encantar**. Lo tienen 166 + 27 armas (3 / 2), los escudos (2), 3 filas con `WTP+0x35 = 6`
  (catalizador; 1280000, 2470000: 3; 3910000: 2). No lo tienen 21 filas con `WTP+0x35 = 0` ni 27 con `+0x35 = 6`
  (entre ellas Sorcerer's Staff, Cleric's Chime, Pyromancy Flame).
  - Bit 0 (`+0x50 = 3` frente a 2): **sin confirmar** (no lo lee este código).

**Si falla**: `FUN_1403963b0` falso → `FUN_140396ba0` da código 1 → `FUN_140395930`: animación `0x24596d6`
(38115030, `a38_11_5030_StickSpellNoBullet`), fila nula. Mismo fallo que sin usos o sin catalizador: gesto, sin bala,
sin gasto.

**Valores para exportar** (WeaponParam `+0x15`; ItemParam con `+0x14 ==` id, `+0x50`; todas las filas encontradas
tienen el mismo id que el arma):

| Arma | Id | `WP+0x15` | `IP+0x50` | Cond. 1 (arma) | Cond. 2 (escudo) |
|---|---|---|---|---|---|
| Longsword | 1220000 | 0 | 3 | sí | no |
| Battle Axe | 2010000 | 0 | 3 | sí | no |
| Mace | 2410000 | 0 | 3 | sí | no |
| Club | 2400000 | 0 | 3 | sí | no |
| Spear | 2800000 | 0 | 3 | sí | no |
| Pickaxe | 2610000 | 0 | 3 | sí | no |
| Drangleic Shield | 11230000 | 3 | 2 | no | sí |
| Fists (mano vacía) | 3400000 | 0 | 0 | no | no |
| Sorcerer's Staff | 3800000 | 0 | 0 | no | no |
| Cleric's Chime | 4010000 | 0 | 0 | no | no |
| Pyromancy Flame | 5400000 | 0 | 0 | no | no |
| Torch | 6000000 | 0 | sin fila | no | no |

- La antorcha no tiene fila de `ItemParam` con `+0x14 = 6000000`: `FUN_1401a4740` da 0 y falla.
  Con la antorcha en esa mano también se lee ella (no el arma de debajo), por el `+0x200`.
- Con la mano del objetivo vacía (puños) también falla.
