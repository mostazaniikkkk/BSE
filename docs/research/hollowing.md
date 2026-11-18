# Hollowing (estado de Hueco) y Efigie humana

Fuente: `DarkSoulsII.exe` (SotFS), proyecto Ghidra `D:\Reversing\DS2\ghidra_proj`. Salidas en `D:\Reversing\DS2\out\hollow\`.
Datos: regulation (`CriminalAndDeceasedParam`, `ItemParam`, `ItemTypeParam`, `ItemUsageParam`, `SpEffect*.emevd`),
`c0001.nmb`, `c000100_pl.tae`, `GameDataEbl.bdt`.

## Estado y contador
- **Contador de Hueco `H`**: `s8` en el objeto de estado del jugador (`chr+0x490`) `+0x1ac`.
  - Sumar (`FUN_14038bac0`) y fijar (`FUN_14038bd10`) recortan a **0..32**. Poner a 0: `FUN_14038bbe0`.
  - Lo leen la red (`FUN_140297f20` casos 9 y 0x1d) y la copia del bloque de estado (`FUN_14038ad20`, `FUN_140357920`).
    Que se guarde en la partida es probable, no confirmado.
- **Estado humano/hueco** (`FUN_140203db0`): `H <= 0` → 0 (humano); `H >= CriminalAndDeceasedParam[0].u8@7` (= 5) → 2;
  si no → 1. El valor va a la apariencia (`modelo+0x2d`, `FUN_1402026e0`) y a otros chequeos (`FUN_1401a8b90`,
  sin identificar); para la vida solo cuenta si la bandera de abajo es 0 o no.
- **Bandera "es hueco"**: `chr+0xb0` `+0x3e`. La fija `FUN_1402026e0` (= estado de `H`) y la limpia `FUN_140203d50`.
  `FUN_1402026e0` corre al (re)iniciar el personaje (`FUN_14037d380`, cuando `+0x1d0` pasa a 1) y en `FUN_140202690`.
- **Al morir** (`FUN_14037dcc0`): `FUN_140202c30(estado, chr.b8+0x76d)`:
  - `chr.b8+0x76d != 3` → `H += CriminalAndDeceasedParam[0].u8@5` (= **1**);
  - `== 3` → `H += u8@6` (= **5**). La muerte 3 es la **petrificación** (petrification.md §2.4).
  - **No mira si eras humano**: muriendo como humano también suma. No suma si `thunk_FUN_140014b03(chr)`,
    `FUN_14016f740(chr)`, `FUN_14031c850(b8)`, `b8+0x4c8` bit 55 o `b8+0x4b8` bit 58 (significado sin identificar;
    `FUN_14013d9f0` pone el bit 55).
  - La bandera de hueco no cambia en la muerte: cambia al reiniciar el personaje (`FUN_1402026e0`).

`CriminalAndDeceasedParam` fila 0 (12 bytes): `01 01 01 0a 64 01 05 05 32 05 32 00`.

## Vida máxima — `FUN_140202820` / `FUN_140202ca0`
```c
// FUN_140202820: factor de vida (solo si la tabla de tipo de chr 0x1410bfff0[tipo*5+3] != 0)
if (chr.b0+0x3e == 0) return 1.0;                  // humano: sin recorte
piso = (FUN_14038b960(estado) == 2) ? P.u8@9 : P.u8@8;   // 5 : 50   (10.0 si falta la fila)
pct  = max(100.0 - H * 5.0, piso);                 // 100.0 = DAT_1410acb18, 5.0 = DAT_1410ad5e4
f    = (100 - (100 - pct) * chr.b8+0x430) / 100;
return f < 0.01 ? 0.01 : min(f, 1.0);

// FUN_140202ca0: tope de vida
chr+0x174 = max(1, (int)(f * chr+0x170 + 0.999999)); // 0.999999 = DAT_1410cacc0 → redondeo hacia arriba
chr+0x168 = clamp(min(vidaActual, chr+0x174), chr+0x16c, chr+0x170);
```
- `chr+0x170` es la vida máxima de stats: `(int)(derivada+0 × chr.b8+0x3d8)` (ver [stats.md](stats.md)).
- Resultado: **−5 % por muerte**, con piso de **50 %** desde `H = 10` (el contador sigue hasta 32).
  Primera muerte como humano: `H = 1` → **95 %** al reiniciar.
- `chr.b8+0x430` = índice 29 del arreglo de multiplicadores `b8+0x3bc` (`SpEffectActionImpl_ChrStatus_Rate`,
  `FUN_14021bd70`, instrucción `1000[01]` según la tabla de despacho en el exe, archivo `0x156e118`).
  - **Ring of Binding** (`SpEffectRing.emevd` 40410000): `1000[01] (29, 0, 0.5)` → ×0.5 → piso efectivo 75 %.
  - El valor base 1.0 de ese multiplicador no lo verifiqué.
- Rama aparte: si `thunk_FUN_140014b03(chr)` y `FUN_14016f510(chr)` (probablemente fantasmas) el factor es
  `1 − max(0, (1 − x+0x38) × b8+0x3e4)`. No aplica al jugador anfitrión (no verificado).
- El piso 5 % (`u8@9`) aplica cuando el estado `+0x1d6` vale 2: contador `+0x1d7` (`FUN_140202ae0`; umbrales
  `u8@3` = 10 → 1, `u8@4` = 100 → 2; suma `u8@0`/`u8@2`, resta `u8@1`; `FUN_14013d9f0` suma al matar ciertos
  personajes). Qué es ese contador (pecado/"criminal") **no está confirmado**.

## Estamina y demás
- **La estamina NO baja con el hollowing.** `FUN_140202820` solo lo llama `FUN_140202ca0`, que solo toca la vida
  (`+0x174`, `+0x168`). La estamina máxima (`FUN_14038be90`, `FUN_14037e8a0`) es `b8+0x3dc × base`, sin `H`.
- Otros usos de `H` hallados: `SpEffectChrStatus_AtkRateByHollowCount` (multiplicador de ataque según `H`, para algún
  equipo; ctor `FUN_14021ec20`; no trazado), condición de evento `ComparePlayerHumanCondition` (`FUN_14046ee10`),
  red y apariencia. Nada más cambia números del jugador según lo encontrado.

## Efigie humana — objeto 60151000
- **ItemParam 60151000** (0x54 bytes): `+0x00` icono 60151000, `+0x04` SpEffect al usar **60151000**,
  `+0x08/+0x0c/+0x10` modelos en mano (en realidad ids de FFX, ver sounds.md §8) 870/871/872 (TAE 220200 → `b8+0x6f0..0x6f8`, `FUN_14016b840`),
  `+0x38` f32 0.7 (velocidad: `ItemUse_Speed` = 0.7 × factores, `FUN_14032f750`), `+0x40` ItemTypeParam **500**,
  `+0x44` ItemUsageParam 2140, `+0x4a` u16 99 (probable máximo; no verificado).
- **SpEffect 60151000** (`SpEffectActiveItem.emevd`): una instrucción `100050[02] (1)` → `FUN_14022ab90`
  (tabla de despacho del exe, archivo `0x156f810`). Crea una instancia en la lista de SpEffect del chr con `+0x50 = 1`.
- **Volver a humano**: el update de SpEffect del chr (código ofuscado `0x141c5b717` → `FUN_14022f4f0` →
  `FUN_1402246a0`) encuentra la instancia lista y llama **`FUN_140203d50(chr)`**:
  - limpia la bandera de hueco (`b0+0x3e = 0`) y la apariencia;
  - `H = 0` (`FUN_14038bbe0`), recalcula vida y estamina (`FUN_14038be90`) y el tope (`FUN_140202ca0`) → **100 %**.
  - Con `+0x50 != 0` además llama `FUN_1405012e0(sesión online, 5)` (red; no relevante para el mod).
  - La misma instrucción está en el SpEffect 755 (`SpEffectCondition.emevd`).
- **No verificado**: si se puede usar siendo humano (el código de restauración no lo impide; la compuerta del menú no
  está trazada) y **cuándo se descuenta el objeto** del inventario.

## Animación de uso — `a00_18_0190 ItemUseAbsorb` (TAE 180190)
- Cadena: `FUN_14032f450` → `FUN_14032f750`: `b8+0x6e0 = ItemTypeParam +0x10` (en escalera, si `b8+0x4c8` bit 2,
  `+0x14`); `FUN_14015fbf0` lo pasa al CP de Morpheme `ItemUseCategory`. ItemTypeParam 500 `+0x10` = **190**.
- El nodo del nmb que elige cada valor **no está decodificado**. Apoyo: las tablas de selector del nmb contienen
  {100,110,150,170,180,190} y {30,40,50,170,190,200}, y los números coinciden con los nodos `a00_18_0XXX`
  (Estus: tipo 450 → 200 `ItemUseEstusDrink`; Lifegem: tipo 100 → 50 `ItemUseCrush`).
- `.nsa`: `a00_18_0190_ItemUseAbsorb_unt_ca4e2d4f.nsa`, **4.0 s**.
- TAE:
  - **220000 a 1.667 s** (hasta 1.833): `FUN_14016b840` pone `b8+0x6de = 1`; `FUN_14032f870` aplica el SpEffect de
    `ItemParam +0x04` (60151000). **Ese es el momento del efecto.**
  - 220200 (`ChrItemCtrl`, dummy 26) 0.267–1.667 s modelo 0 (870); 1.5–1.833 s modelos 1 y 2 (871/872).
  - 220100 0–3.0 s (`b8+0x53c` += 1 mientras dura; uso sin identificar); 310000 0–3.5 s.
- nmb: `CancelControl_Avoid`, `CancelControl_AttackAll`, `CancelControl_Guard` hasta **2.967 s**; `InputControl` y
  `ItemUseInputControl` desde 1.8 s; `ItemUseCancelControl` desde 2.967 s. **No hay `CancelControl_Move`** (igual que la
  antorcha). Herramienta: `tools/re/weapons.py item_use(180190)`.
- `ItemUsageParam` 2140 `+5` bit 0 = 0: con `b8+0x4c8` bit 2 (probablemente escalera) no se puede usar
  (`FUN_14032f450`).

## Icono
- `FUN_14048c3e0` caso por defecto: `icon:/tex/Icon/IC_%010d.tpf` con el id de icono. En el `.bhd` existe
  `/menu/tex/icon/IC_0060151000.tpf` (8373 bytes). Que el id sea `ItemParam +0x00`: 1239 de 1275 filas tienen archivo
  con `+0x00` contra 1146 con el id de fila (comprobación con datos, no con código).
- TPF → DDS DXT5 64×128 (`IT_IC_0060151000`). PNG recortado al alfa: `D:\Reversing\DS2\out\hollow\human_effigy_icon.png`
  (60×95, recorte (0,14)-(60,109)); completo: `out\hollow\icon\IC_0060151000_full.png`.

## Textos (NeutralSpanish = es_mx; igual en Spanish)
- Nombre: **Efigie humana**. Corto: "Anula estado de Hueco."
- Largo: "Una cálida y suave efigie de sombras. / Este objeto permite volver a ser humano. Además, si lo quemas en la
  hoguera debilita los vínculos con otros mundos para prevenir las invasiones y gran parte de la cooperación. / Cuando
  observas con atención esta efigie, empiezas a percibir una forma humana. La forma depende de la persona que la mira."
- Archivos: `gamedata:/menu/Text/NeutralSpanish/{itemname,SimpleExplanation,DetailedExplanation}.fmg` id 60151000
  (`out\hollow\effigy_text.txt`).

## Sin confirmar
- Las condiciones que evitan sumar. (La muerte tipo 3, +5, es la petrificación: petrification.md §2.4.)
- Significado del contador `+0x1d7` / estado `+0x1d6` (piso 5 %).
- Valor base de `b8+0x430` (se asume 1.0 sin efectos: **no verificado**).
- Qué valor de `ItemUseCategory` elige qué nodo dentro del nmb.
- Cuándo se consume la efigie y si se puede usar siendo humano.
- `HollowLevelStatsParam` (índice 0x2b, `+0x5c0`) no está en la regulation; no se encontró uso.

## Implementado (`SoulsHollow`, `item/HumanEffigyItem`)
- Contador H (attachment `hollowing`, se conserva al morir): +1 por muerte en Modo Souls, tope 32. Hueco = H > 0.
- Vida máxima: `max(1, ceil(f × vida máxima))`, `f = max(100 − 5H, 50) / 100`. La estamina no cambia.
- Efigie humana (`soulsmode:human_effigy`, "Efigie humana" / "Anula estado de Hueco."): reproduce `a00_18_0190
  ItemUseAbsorb` (sin bloqueo de movimiento; esquiva, ataque y guardia bloqueados 2.967 s) y a los 1.667 s (TAE 220000)
  pone H = 0 y gasta una efigie. Si se corta antes, no hace nada y no se gasta. **Cuándo la gasta DS2 no está sacado**:
  aquí al aplicar el efecto.
- Fuera del Modo Souls el estado no tiene efecto ni se ve; la efigie no hace nada.
- Ícono: redibujado a 16×16 a partir del ícono de DS2 (`IC_0060151000`).
- **Lado Minecraft (diseño del usuario)**: aldeanos que no comercian y huyen (≤ 8 bloques), gólems que atacan,
  Héroe de la aldea anulado, comerciante errante con la efigie a 2 esmeraldas (4 usos), clérigo maestro 25 %, cofres de
  templos y fortalezas del Nether 20 %, reputación a 0 con los aldeanos cargados al dejar de ser Hueco.
