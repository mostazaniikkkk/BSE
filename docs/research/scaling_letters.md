# Letras de escalado del menú de armas (-, E, D, C, B, A, S)

Fuente: `DarkSoulsII.exe` (SotFS), regulation (`FeWeaponThreshold`, `WeaponStatsAffectParam` WSA,
`WeaponReinforceParam` WRP) y `menu__Text__English__ingamemenu`. Salidas en `D:\Reversing\DS2\out\scaling\`
(`menu.c`, `callers.c` = decompilación Ghidra; `b8ea0.txt` = traza desofuscada con `out\py\deob.py`;
`letters.py` = cálculo de las letras con los datos del mod).

## 1. Regla

**Valor comparado = coeficiente de la WSA del nivel de refuerzo actual**, el mismo `c[k]` que usa el daño
(damage.md §1, `FUN_1403903b0`), puesto a 0 si el arma no tiene ese tipo de daño. No se multiplica por la escala
WSA `+0x00`, ni por los bonos de stats, ni por el reparto.

Constructor de los datos del arma para el menú: `FUN_1401b8ea0` (llamado desde `thunk_FUN_141c4e24e` ←
`FUN_140036f40`; ofuscado, leído con `deob.py`):
- Fila WSA: `FUN_14034ec60` = `WRP+0x4c` + byte `+1` de `FUN_1401ad060(…)` (la infusión; tabla `+0x480` del gestor).
- Nivel = byte `[rbp+0x98]`; coeficientes `WSA + 8 + nivel·0x24 + 4k` (`0x1401b90d6..0x1401b9119`).
- Presencia de cada tipo: `M[i]` de `FUN_14034c580` (`WRP+0xa0+4i × 0.01` + resinas, damage.md) con
  `0.01 < |M[i]|` (constante `0x1410acb08` = 0.01; `0x1401b912f..0x1401b91d0`).
- Infusión = byte `[rbp+0xa0]`; `== 8` activa la rama de Int en el físico (damage.md: byte 8 → `bono(3)·c[8]`).

| Campo struct menú | Valor guardado | Campo menú (id) | Fila `FeWeaponThreshold` |
|---|---|---|---|
| `+0x50` Fuerza | `c[0]` si físico, si no 0 | 0x29 | 1 |
| `+0x54` Destreza | `c[1]` si físico, si no 0 | 0x2a | 2 |
| `+0x58` Magia | infusión 8: `c[8]`; si no `c[2]` si magia, si no 0 | 0x2b | 3 (infusión 8: 7) |
| `+0x5c` Rayo | `c[3]` si rayo, si no 0 | 0x2c | 5 |
| `+0x60` Fuego | `c[4]` si fuego, si no 0 | 0x2d | 4 |
| `+0x64` Oscuro | `c[5]` si oscuro, si no 0 | 0x2e | 6 |

(`0x1405b79ce`, `0x14038ce30`, `0x141b9f564`, `0x141c31e75`, `0x1406e908d`, `0x140201e84`.)
Veneno `c[6]` y sangrado `c[7]` se guardan en `+0x24` / `+0x28`, no tienen letra.

Selección de letra: `FUN_140037720` (switch del campo, `0x14003785e..0x1400378fd`) →
`FUN_140032460` (lee el float del struct) → `FUN_1400404d0(valor, fila)`:
- `valor == 0` o no `valor > 0` → índice 6.
- Si no, toma los 5 floats de la fila (`FUN_1404ffc60`, gestor `[DAT_1416148f0+0x22e0]+0x290`) más un 6.º = 0 y
  devuelve el primer `i` con `valor >= umbral[i]` (comparación float32, `comiss/jae`, sin redondeo).
- Índice → texto: punteros en `[+0x22e0]→+0x130 → +0x538 + i·8`, cargados en `0x14003897a..0x140038a08` con
  `FUN_140503620(7 = ingamemenu, id)`: 0 S (2210110), 1 A (2210120), 2 B (2210130), 3 C (2210140), 4 D (2210150),
  5 E (2210160), 6 "-" (2210100).
- Que `+0x290` es `FeWeaponThreshold.param`: `FUN_140502b40` carga `+0x290` con la ruta de `FUN_14048c930` =
  `L"param:/FeWeaponThreshold.param"` (`0x1410f2e40`).

Umbrales (`FeWeaponThreshold`, regulation; letra si `valor >= umbral`, E si `0 < valor < D`):

| Fila | Uso | S | A | B | C | D |
|---|---|---|---|---|---|---|
| 1 | Fuerza | 1.00 | 0.80 | 0.55 | 0.35 | 0.20 |
| 2 | Destreza | 0.60 | 0.45 | 0.35 | 0.25 | 0.15 |
| 3 | Magia | 1.00 | 0.80 | 0.55 | 0.35 | 0.20 |
| 4 | Fuego | 1.00 | 0.80 | 0.55 | 0.35 | 0.20 |
| 5 | Rayo | 1.00 | 0.80 | 0.55 | 0.35 | 0.20 |
| 6 | Oscuro | 1.00 | 0.80 | 0.55 | 0.35 | 0.20 |
| 7 | Magia con infusión 8 (`c[8]`) | 0.50 | 0.40 | 0.30 | 0.20 | 0.15 |
| 8 | campo 0x10 (struct `+0x38`) | 1.00 | 0.52 | 0.35 | 0.25 | 0.21 |

- **Cambia con el refuerzo**: sí, porque usa `c[k]` del nivel actual.
- **Por tipo**: sí, una fila por stat/tipo; Destreza tiene su propia tabla.
- Los umbrales son float32 (p. ej. 0.8 = `0x3f4ccccd`); un coeficiente igual al umbral sube de letra.

## 2. Datos del mod
`src/main/resources/data/soulsmode/ds2/weapons.json` → `weapons.<id>.attack.coef[nivel][k]` (exportado por
`tools/re/weapons.py`, `attack_data`) y `split` para la presencia del tipo (`|split[i]| > 0.01`).
Los valores están redondeados a 4 decimales: hay que volver a float32 antes de comparar (en double, 0.8 < 0.8f y
la letra baja una).
- El exportador usa la fila WSA `WRP+0x4c` sin sumar la infusión: vale para armas sin infundir.

## 3. Comprobación (`out\scaling\letters.py`)
Comparado con las tablas de mejora de la wiki de Fextralife (fuente externa, no medido en el juego):

| Arma | Calculado +0 | Calculado +10 | Wiki +0 | Wiki +10 |
|---|---|---|---|---|
| Longsword | Fue C (0.45), Des C (0.25) | Fue C (0.50), Des C (0.25) | C / C | C / C |
| Club | Fue A (0.95), Des - | Fue S (1.00), Des - | A / - | S / - |
| Battle Axe | Fue C (0.50), Des D (0.15) | Fue B (0.55), Des D (0.15) | C / D | B / D |
| Mace | Fue B (0.75), Des E (0.10) | Fue A (0.80), Des E (0.10) | B / E | A / E |

Las cuatro coinciden. Battle Axe +10 y Mace +10 solo coinciden si se compara en float32 con `>=`.

## Sin confirmar
- Qué muestra el campo 0x10 (fila 8, struct `+0x38`).
- `FUN_14034fd40` (llamado 6 veces desde `0x14034fa5e..0x14034fb1d`) usa el mismo `FUN_1400404d0` fuera del menú y
  elige un float `+0x13c + 4·rango` de la fila de `FUN_1402036d0`: el rango por letra afecta algo del juego.
  No se identificó qué.
- Qué texto concreto del menú usa la infusión 8 en la columna de Magia (solo se vio que cambia a `c[8]` y a la fila 7).
- Que las letras salgan así en pantalla no se comprobó en el juego; solo contra la wiki.
- Rutas que no son de armas (`FUN_1401ab390` categorías 3/4 → este constructor; 5 → `thunk_FUN_141b49bde`).
