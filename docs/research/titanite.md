# Titanita: refuerzo e infusión de armas y armaduras

Fuente: `DarkSoulsII.exe` (SotFS), proyecto Ghidra `D:\Reversing\DS2\ghidra_proj`. Salidas en `D:\Reversing\DS2\out\`
(`reinf_x2.txt` / `reinf_x3.txt` = referencias, `reinf_dec.c` / `reinf_dec2.c` = decompilado; `py\deob.py` para las
funciones ofuscadas). Datos: regulation (`ItemParam`, `WeaponParam` WP, `WeaponReinforceParam` WRP,
`WeaponStatsAffectParam` WSA, `ArmorParam`, `ArmorReinforceParam` ARP, `ReinforceCostParam` RCP,
`CustomAttrSpecParam` CAS, `CustomAttrCostParam` CAC, `ShopLineupParam`, `ItemLotParam2_*`, `EnemyParam` EP),
textos `itemname` / `SimpleExplanation` / `DetailedExplanation` / `npcMenu` / `talk/<mapa>` (English y NeutralSpanish),
`gamedata:/ezstate/talk_<mapa>.esd` (con `tools/ds2/esd.py`).

Slots del gestor de params (`out\damage\plist.txt`): CAS `+0x4f0`, CAC `+0x500`, RCP `+0x510`. Lectores de fila:
CAS `FUN_1403587b0`, CAC `FUN_140358780`, RCP `FUN_140358ee0` (id 0 → sin fila).

## 1. Materiales (`ItemParam`)
| Id | English | NeutralSpanish | Uso (texto del juego) | `+0x30` | `+0x34` |
|---|---|---|---|---|---|
| 60970000 | Titanite Shard | Fragmento de titanita | "Refuerza el equipo hasta +3." | 1000 | 100 |
| 60975000 | Large Titanite Shard | Fragmento grande de titanita | "…hasta +6." | 2500 | 250 |
| 60980000 | Titanite Chunk | Trozo de titanita | "…hasta +9." | 6000 | 600 |
| 60990000 | Titanite Slab | Losa de titanita | "…hasta +10." | 13000 | 1300 |
| 61000000 | Twinkling Titanite | Titanita centelleante | "Refuerza el equipo que normalmente no se puede reforzar hasta +5." | 15000 | 1500 |
| 61030000 | Petrified Dragon Bone | Hueso de dragón petrificado | "Refuerza el equipo creado con almas especiales." | 17500 | 1750 |
| 60538000 | Fire Seed | Semilla de fuego | "Se usa para fortalecer una llama de piromancia." | 8000 | 800 |
| 61160000 | Palestone | Piedra pálida | quita la infusión; "no revierte el refuerzo" | 5000 | 500 |
| 61080000 | Firedrake Stone | Piedra de fuego dragón | infusión Fuego | 7000 | 700 |
| 61060000 | Faintstone | Piedra débil | infusión Magia | 7000 | 700 |
| 61070000 | Boltstone | Piedra de rayo | infusión Rayo | 7000 | 700 |
| 61090000 | Darknight Stone | Piedra de noche oscura | infusión Oscuro | 7000 | 700 |
| 61100000 | Poison Stone | Piedra de veneno | infusión Veneno | 7000 | 700 |
| 61110000 | Bleed Stone | Piedra de sangre | infusión Sangrado | 7000 | 700 |
| 61130000 | Raw Stone | Piedra básica | infusión Básica ("no en escudos") | 6000 | 600 |
| 61140000 | Magic Stone | Piedra mágica | infusión Encantada (Int → ataque; "no en escudos") | 7000 | 700 |
| 61150000 | Old Mundane Stone | Vieja piedra mundana | infusión Mundana ("no en escudos") | 10000 | 1000 |

- Todos: `+0x40` ItemTypeParam 1200, `+0x44` ItemUsageParam 2230, `+0x4a` = **99** (probable máximo de la pila,
  igual que en resins.md; **no verificado en código**). `+0x30` / `+0x34` parecen precio de compra / venta (§7).
- `Dull Ember` (50990000, "Ascua mortecina") no es material: McDuff la pide para imbuir (§6).
- Textos completos (simple y detallado, en/es): `itemname`, `SimpleExplanation`, `DetailedExplanation` con esos ids.

## 2. Refuerzo de armas
### 2.1 Cadena de params (confirmada: `FUN_14003c490`, `FUN_14003c690`, `FUN_140034fb0` → `FUN_14003b480`)
- `WeaponParam +0x08` → fila WRP (ya en `tools/re/weapons.py`).
- `WRP +0xe8` = fila **CAS** (qué infusiones admite), `+0xec` = fila **CAC** (coste de infundir), `+0xf0` = fila **RCP**
  (coste de reforzar). La armadura usa los mismos tres en `ARP +0x68 / +0x6c / +0x70` (§5).
- El arma se elige con `FUN_14003c250` (WP de la ranura del menú → WRP); la armadura con `FUN_14003ba30`
  (`ArmorParam +0x18` → ARP).
- **Nivel actual** = `objeto+0x25 & 0x0f` del inventario (`FUN_140037cf0`; 0 si `objeto+0x1e > 5`).

### 2.2 `ReinforceCostParam` (0x64 bytes)
| Desplazamiento | Tipo | Contenido |
|---|---|---|
| `+0x00 + 4·L` | s32 ×10 | almas para pasar de +L a +L+1 |
| `+0x28 + 4·L` | s32 ×10 | id del material |
| `+0x50 + 2·L` | u16 ×10 | cantidad del material |

- **Índice = nivel actual** (`FUN_14003c690`): el arma aparece en el menú de refuerzo si `L < 10` y
  (`almas[L] ≠ 0` o `material[L] > 0`). Ese es el filtro de la lista (`FUN_140039d10` / `FUN_14003a0b0`, modo 5).
- No se mira `WRP +0x48` para cortar: el máximo sale de dónde se acaba la fila (las de +5 tienen ceros desde el 5).
  `WRP +0x48` es el nivel máximo que usa el daño (lerp, §3); en todas las filas coincide con la fila de coste.

### 2.3 Rutas (todas las filas WRP con WeaponParam)
| Ruta | Armas | Material por nivel (cantidad) | Ejemplos |
|---|---|---|---|
| Titanita +10 | 213 | +1..+3 Fragmento ×1/×2/×3, +4..+6 Grande ×1/×2/×3, +7..+9 Trozo ×1/×2/×3, +10 Losa ×1 | Longsword, Dagger, Battle Axe |
| Centelleante +5 | 58 | Titanita centelleante ×1/×2/×3/×4/×5 (RCP `100xxxxx`) | Puzzling Stone Sword, Red Rust Sword, Royal Dirk |
| Hueso de dragón +5 | 60 | Hueso de dragón petrificado ×1..×5 (RCP `200xxxxx`) — armas de alma de jefe | Mytha's Bent Blade, Fume Sword |
| Llama de piromancia +10 | 2 | Semilla de fuego ×1 cada nivel (RCP 302500 / 402500) | Pyromancy Flame, Dark Pyromancy Flame |
| Sin refuerzo | — | RCP 0 | Puños, antorcha, llaves |

Total a +10 por titanita: **6 fragmentos, 6 grandes, 6 trozos, 1 losa**. A +5: **15** centelleantes o 15 huesos.
Las almas cambian por arma (el primer dígito del id de RCP, 1..5, es la escala; el resto, la clase).

### 2.4 Armas del mod (`weapons.json`)
| Arma (WP) | WRP | Máx | RCP | Almas +1 → +máx | CAS (infusiones) |
|---|---|---|---|---|---|
| Longsword / Shortsword / Drakekeeper's (1220000 / 1210000 / 1370000) | 1220 / 1210 / 1370 | 10 | 301000 | 500, 630, 750, 1000, 1130, 1250, 1500, 1630, 1750, 2000 | 101000: todas |
| Broken Straight Sword (1200000) | 1200 | 10 | 100400 | 260, 320, 390, 510, 580, 640, 770, 830, 900, 1020 | 100400: todas |
| Hand Axe (2000000) | 2000 | 10 | 101600 | 310, 390, 470, 620, 700, 780, 930, 1010, 1090, 1240 | 101700: todas |
| Infantry Axe (2030000) | 2030 | 10 | 201600 | 440, 550, 660, 880, 990, 1100, 1320, 1430, 1540, 1760 | 101700: todas |
| Bandit / Gyrm / Battle Axe (2020000 / 2070000 / 2010000) | 2020 / 2070 / 2010 | 10 | 301600 | 520, 650, 780, 1030, 1160, 1290, 1550, 1680, 1810, 2060 | 101700: todas |
| Mace / Pickaxe (2410000 / 2610000) | 2410 / 2610 | 10 | 301400 | 520, 650, 770, 1030, 1160, 1290, 1540, 1670, 1800, 2050 | 101500: todas menos Sangrado |
| Club (2400000) | 2400 | 10 | 201400 | 440, 550, 660, 880, 990, 1090, 1310, 1420, 1530, 1750 | 101500: todas menos Sangrado |
| Spear (2800000) | 2800 | 10 | 300600 | 530, 660, 790, 1050, 1190, 1320, 1580, 1710, 1840, 2100 | 100600: todas |
| Short Bow / Light Crossbow (4200000 / 4600000) | 4200 / 4600 | 10 | 202000 / 202200 | 400, 500, 590, 790, 890, 990, 1180, 1280, 1380, 1580 | 102100 / 102300: todas menos Veneno y Sangrado |
| Sorcerer's Staff (3800000) | 3800 | 10 | 202300 | 430, 540, 640, 850, 960, 1070, 1280, 1390, 1490, 1700 | 102400: Ninguna, Magia, Oscuro |
| Cleric's Sacred Chime (4010000) | 4010 | 10 | 202400 | igual que el staff | 102500: Ninguna, Rayo, Oscuro |
| Pyromancy Flame (5400000) | 5400 | 10 | 302500 | 1000, 1250, 1500, 2000, 2250, 2500, 3000, 3250, 3500, 4000 (Semilla ×1) | 0: no se imbuye |
| Puzzling Stone Sword (1250000) | 1250 | 5 | 10005000 | 1090, 1370, 1640, 2180, 2450 (centelleante ×1..×5) | 121000: todas |
| Red Rust Sword (1330000) | 1330 | 5 | 10000200 | 1090, 1370, 1640, 2180, 2450 (centelleante) | 121000: todas |
| Dragonslayer's Crescent Axe (2080000) | 2080 | 5 | 10001000 | 1130, 1410, 1690, 2250, 2530 (centelleante) | 121700: todas |
| Drangleic Shield (11230000) | 11230 | 5 | 10003500 | 930, 1160, 1390, 1860, 2090 (centelleante) | 122800: Ninguna..Sangrado (sin Básica/Encantada/Mundana) |
| Fists / Torch (3400000 / 6000000) | 3400 / 6000 | 10 | 0 | no se refuerzan | 0 |

Ninguna arma del mod usa hueso de dragón. Script: tabla generada con `tools/re/params.py` (salida en esta nota).

## 3. Qué cambia con el nivel (arma)
- **Ataque base** (`FUN_14034e820`, damage.md): `lerp(WRP[i], WRP[i+9], clamp(nivel / WRP+0x48)) × WSA+0x00`.
  `+0x00..+0x20` = +0, `+0x24..+0x44` = máximo (Longsword físico 112 → 225). En un arma +5, +5 ya es el máximo.
- **Escalado**: coeficientes de la fila WSA para ese nivel, `WSA + 8 + nivel·0x24` (9 floats, 11 niveles; damage.md,
  scaling_letters.md). Longsword Fue 0.45 → 0.50.
- **Estabilidad de guardia**: `WRP +0x74 + 4·nivel` (11 floats; stamina.md). Drangleic Shield 55 (+0) → 63 (+5).
- **Reparto elemental** `WRP +0xa0..` (×0.01): no depende del nivel.
- **Reducciones de guardia** `WRP +0x50..+0x70` (9 floats; Drangleic 100/70/55/55/35/…): un solo juego, no por nivel.
  Que sean las reducciones: **sin confirmar** (weapons.md).
- **Durabilidad** (`WeaponParam +0x28`): no hay campo por nivel en WRP; si el refuerzo la cambia: **sin confirmar**.

## 4. Infusión
### 4.1 Tipos (índice = byte `desc+0x31` de damage.md; nombres `npcMenu` 59201 + i)
| i | Nombre (en / es) | Piedra | Elemento (tabla `0x1410c3e10`, byte 0) | Fila WSA (byte 1) | Cantidad movida (WRP) |
|---|---|---|---|---|---|
| 0 | None / Nada | Piedra pálida (quita) | — | +0 | 0 |
| 1 | Fire / Fuego | Firedrake | 3 fuego | +3 | `+0xcc` |
| 2 | Magic / Magia | Faintstone | 1 magia | +1 | `+0xc4` |
| 3 | Lightning / Electricidad | Boltstone | 2 rayo | +2 | `+0xc8` |
| 4 | Dark / Oscuridad | Darknight | 4 oscuro | +4 | `+0xd0` |
| 5 | Poison / Veneno | Poison Stone | 5 veneno | +5 | `+0xd4` |
| 6 | Bleed / Hemorragia | Bleed Stone | 6 sangrado | +6 | `+0xd8` |
| 7 | Raw / Básico | Raw Stone | 0 físico | +7 | `+0xdc` |
| 8 | Enchanted / Encantado | Magic Stone | 0 físico | +8 | `+0xe0` |
| 9 | Mundane / Mundano | Old Mundane Stone | 0 físico | +9 | `+0xe4` |

El orden de las piedras sale de CAC (`+0x28 + 4·i`) y coincide con los nombres del menú y con la tabla del exe.
El tercer byte de la tabla (9, 8, …, 1) no está identificado.

### 4.2 Qué cambia (`FUN_14034c760` → `FUN_14034c5c0`, `FUN_14034fe10`, `FUN_14034fed0`)
1. Copia el reparto `WRP +0xa0..` (9 floats).
2. `cantidad = WRP[columna de la tabla] × 1.0` (`FUN_14034fe10`; 0 si i = 0 o i ≥ 10).
3. Si `cantidad > 0` (`FUN_14034fed0`, tope `1000.0` en `0x1410ad5f0`): `reparto[elemento] = min(reparto[elemento] +
   cantidad, 1000)`, y a cada **otro** elemento con valor > 0 se le resta `cantidad / n` (n = cuántos otros tienen
   valor > 0).
4. Todo × 0.01 (`0x1410acb08`) y recorte a ≥ 0 (`FUN_14034c580`).
5. La fila WSA es `WRP +0x4c + byte 1` (scaling_letters.md): cambia el multiplicador de base `WSA +0x00` y los
   coeficientes de escalado. Longsword (WSA 1003050..59):

| Infusión | `WSA+0x00` | Fue / Des (+0 → +10) | Elemental c[2..5] (+0 → +10) | Reparto Longsword |
|---|---|---|---|---|
| Ninguna | 1.0 | 0.45/0.25 → 0.50/0.25 | 0.35 → 0.40 | 100 % físico |
| Fuego / Magia / Rayo / Oscuro | 1.4 | 0.338/0.188 → 0.375/0.188 | 0.525 → 0.60 | 50 / 50 |
| Veneno / Sangrado | 1.4 | 0.338/0.188 → 0.375/0.188 | 0.70 → 0.80 (c[6], c[7] 2.0 → 2.1) | 50 físico / 50 estado |
| Básica | 1.15 | 0.113/0.063 → 0.125/0.063 | 0.263 → 0.30 | 100 % físico |
| Encantada | 1.0 | 0.158/0.088 → 0.175/0.088 | 0.175 → 0.20; c[8] (Int en físico) 0.175 → 0.20 | 100 % físico |
| Mundana | 0.5 | 0.225/0.125 → 0.25/0.125 | 0.175 → 0.20 | 100 % físico; escala con el stat más bajo (damage.md, byte 9) |

- Cantidades del mod: espadas, hachas, mazas, lanza, arcos 50; Puzzling Stone Sword y Drangleic Shield 55;
  Crescent Axe 20 en rayo (ya es de rayo); staff 15; chime 20; Básica / Encantada / Mundana 0 (no mueven reparto).
- **El nivel no se toca**: el nivel y la infusión son bytes distintos del arma (`desc+0x30` / `+0x31`) y el daño los usa
  por separado. Que la operación de imbuir conserve el nivel lo dice el texto de la Piedra pálida ("no revierte el
  refuerzo"); el código que escribe la infusión **no está trazado**.

### 4.3 Permisos y coste
- **CAS** (4 bytes): bit i = infusión i permitida (`FUN_1401a9290`: `switch` de 0..9 sobre los bits).
  `0x3ff` = todas; `0x3bf` = sin Sangrado (mazas); `0x39f` = sin Veneno ni Sangrado (arcos, ballestas);
  `0x7f` = sin Básica/Encantada/Mundana (escudos); `0x15` staff, `0x19` chime; `0x01` = solo "Ninguna" (armaduras).
  Ids `1xxxxx` = armas de titanita, `11xxxx` = de hueso, `12xxxx` = de centelleante; el sufijo es la clase.
- Un arma entra en la lista de imbuir si tiene fila CAS (`FUN_14003c490`, lista modo 6 en `FUN_140034590`).
- **CAC** (`WRP +0xec`; todas las armas usan 100000; 100100 / 100200 / 900000 son iguales):
  - `+0x00 + 4·i` almas: **1000** para quitar (i = 0), **2000** para cualquier otra.
  - `+0x28 + 4·i` piedra (tabla 4.1), `+0x50 + 2·i` cantidad = **1**.
- `FUN_1401a9290` lee la fila CAS **10** (= Ninguna, Básica, Encantada, Mundana) para algo que no está identificado.

## 5. Armaduras
- ARP (0x74 bytes): `+0x60` nivel máximo, `+0x68` CAS, `+0x6c` CAC (0 en todas), `+0x70` RCP. Defensa física
  `lerp(+0x00..+0x0c, +0x30..+0x3c, nivel / +0x60)` y absorciones `+0x10..` (damage.md). `+0x64` = 10.0: **sin identificar**.
- Rutas: **+10 con titanita** (CAS 105000, RCP `X Y 08 0ZZ`) o **+5 con titanita centelleante** (CAS 125000,
  RCP `300xxxxx`, cantidades ×1/×1/×2/×2/×3). CAS 115000 + RCP `40000xxx` (filas vacías): +5 sin material,
  **no se pueden reforzar** en la práctica. Ninguna armadura usa hueso de dragón. Ninguna se imbuye (CAS = solo bit 0).
- Titanita para armadura: `+1..+3` Fragmento ×1/×2/×3, `+4..+6` Grande ×1/×2/×3, `+7..+9` Trozo ×1/×2/×3, `+10` Losa ×1
  (filas `3xxxxxx..6xxxxxx`); las filas `2xxxxxx` piden ×1/×1/×2. Las filas `X008000..3` (desnudo) tienen almas 0.
- Defensa: +10 dobla la de +0 (Knight Helm 40 → 80); +5 la multiplica por ~1.5 (King's Crown 48 → 72).

| Set del mod (cabeza/pecho/brazos/piernas) | Máx | Almas +1 → +máx por pieza |
|---|---|---|
| Hunter's Hat / Leather Armor / Gloves / Boots (11040100..03) | 10 | 130→500 / 270→1050 / 150→580 / 170→650 |
| Creighton's Mask / Chainmail / Gloves / Leggings (17420100..03) | 10 | 500→2000 / 790→3150 / 150→580 / 490→1950 |
| Knight Helm / Armor / Gauntlets / Leggings (11050100..03) | 10 | 500→2000 / 1050→4200 / 580→2300 / 330→1300 |
| King's Crown / Armor / Gauntlets / Leggings (15130100..03) | 5 | 710→1590 / 1490→3340 / 820→1830 / 920→2070 (centelleante) |
| Drangleic Helm / Mail / Gauntlets / Leggings (17240100..03) | 5 | 550→1230 / 1150→2580 / 630→1410 / 710→1600 (centelleante) |
| Old Bell Helm (11660100) | 10 | 500→2000 |
| Smelter Demon (13050100..03; propuesta de equipload.md, no usada) | 5 | 950→2120 / 1980→4450 / 1090→2440 / 1230→2760 (centelleante) |

Valores intermedios: RCP de cada pieza (`ARP +0x70`), tabla 2.2.

## 6. Quién lo hace
- Menú del NPC (`npcMenu`): 10104 "Reforzar arma/armadura", 10120 "Imbuir arma", 10122 "Ref. Llama de piromancia",
  10103 "Reparar arma/armadura"; confirmaciones 49302 "¿Usar %d almas para reforzar?", 59302 "¿Usar %d almas para
  imbuir?", errores 49401 "Almas insuficientes", 49403 "Se necesitan objetos adicionales", 49404 "…objetos/almas
  adicionales". Clases del exe (RTTI): `FeGroupNpcMenuReinforce`, `FeGroupNpcMenuChangeAttribute`,
  `ItemInventory2::EquipmentReinforceLv`, `ItemInventory2::EquipmentCustomAttribute`.
- **Solo hay camino de NPC**: los únicos usuarios de RCP/CAC/CAS son las listas y comprobaciones del menú
  (`FUN_140034fb0`, `FUN_14003c490`, `FUN_14003c690`, `FUN_1401a9290`). No se encontró uso desde objetos ni hogueras.
- Herreros por nombre (`npcMenu`): 7640 "Herrero Lenigrast", 7643 "McDuff Manos Firmes", 7760 "Armero Ornifex",
  7720 "Magerold de Lanafir". Sus grupos de diálogo están en `talk_m10_04` (Lenigrast), `talk_m10_16` (McDuff),
  `talk_m10_14` y `talk_m10_32` (Ornifex), `talk_m10_19` (Magerold).
- **McDuff y la Ascua mortecina** (`talk_m10_16`, grupo local `2147483596`): si el jugador tiene 50990000, ofrece
  dársela; al aceptar, `131401(50990000, 1)` y `130101(102680, 1)` (bandera 102680) y dice 76430520 "Dame piedras y
  reforzaré tu equipo". Que 102680 sea lo que habilita "Imbuir arma": **sin confirmar** (la lista de opciones del menú
  la arma el motor, ver abajo).
- El menú se abre con el comando 130461 (`talk_*.esd`, args `0, 220, <id>, 0`, iguales para todos los NPC). Qué
  opciones muestra cada NPC (quién refuerza, quién imbuye, quién refuerza la llama) **no está decodificado**.

## 7. De dónde sale la titanita
### 7.1 Tiendas (`ShopLineupParam`, 0x24 bytes)
Campos: `+0x00` objeto, `+0x08` / `+0x0c` banderas, `+0x10` objeto pedido a cambio (Ornifex), `+0x1c` f32 factor de
precio, `+0x20` u8 existencias. Prefijo del id = NPC (7620 → 762xxxxx), por coincidencia con `npcMenu` (no por código).

| NPC (filas) | Objeto | Existencias | Factor | Banderas `+0x08` / `+0x0c` |
|---|---|---|---|---|
| Chloanne (76200600/01) | Fragmento | 10, luego 255 | 1.0 | −1 / 100952, luego 100952 / −1 |
| Chloanne (76200602/03) | Grande | 10, luego 255 | 1.0 | 100952 / 100958, luego 100958 / −1 |
| Chloanne (76200604/05) | Trozo | 10, luego 255 | 1.0 | 100975 / 100973, luego 100973 / −1 |
| Chloanne (76200606) | Losa | 1 | 1.0 | 100975 / −1 |
| Chloanne (76200607 / 08) | Centelleante / Hueso de dragón | 3 / 3 | 1.0 | 100975 / −1 |
| Chloanne (76200609..17) | las 9 piedras de infusión | 3 cada una | 1.0 | 100975 (veneno y sangrado 100958) |
| Lenigrast (76400604) | Fragmento | 10 | **0.8** | −1 / −1 |
| McDuff (76430605) | Grande | 255 | 1.0 | −1 / −1 |
| Rosabeth (76300603) | Semilla de fuego | 3 | 1.0 | −1 / −1 |
| Targray (78500602) | Piedra de rayo | 255 | 1.0 | −1 / −1 |

- Las banderas de Chloanne coinciden con las de los intercambios de Ornifex: 100952 = alma del Viejo Rey de Hierro
  (77601002), 100958 = alma del Caballero del Espejo (77601006), 100975 = alma de Velstadt (77601008), 100973 = alma de
  Nashandra (77601015). Es decir, el stock crece al matar jefes. Que la bandera sea "jefe muerto" y no "alma obtenida":
  **sin confirmar**.
- Lectura de los pares: `+0x08` = la fila aparece con la bandera, `+0x0c` = desaparece con ella. Es lo que dan los datos;
  **no comprobado en código**. 255 = ilimitado: **sin confirmar**. Precio = `ItemParam +0x30 × factor` (fragmento 1000,
  Lenigrast 800): **sin confirmar** en código.

### 7.2 Enemigos (`ItemLotParam2_Chr`, formato en enemies.md §6; lote por defecto `EP +0x134`)
Modo 0, 1 tirada salvo donde se indica; % por tirada. EP id = modelo `cXXXX` × 100 (Hollow Soldier 102000 = c1020).
| EP (modelo) | Lote | Titanita |
|---|---|---|
| 132003..132007 (c1320), 149010/11 (c1490) | 13200000 / 13200700 / 14900000 | Fragmento 20 % |
| 148010..148041 (c1480) | 14801000.. | Fragmento 5 %, Grande 3 %, Trozo 3 % |
| 107000/01, 203002 (c1070, c2030) | 10700000 | Grande 10 % |
| 134000 (c1340) | 13400000 | Grande 20 % |
| 151000/01 (c1510) | 15100000 | Grande 15 % |
| 222002 (c2220) | 22200200 | Grande ×3, 100 % |
| 224000 (c2240) | 22400000 | Grande 10 %, Trozo 5 % |
| 135000..135050 (c1350) | 13500000.. | Trozo 2 % (3 tiradas) |
| 656000..656006 (c6560) | 65600000 | Trozo 3 % |
| 117000/10 (c1170) | 11700000 | Losa 3 % |
| 150000 (c1500) | 15000000 | Losa 5 % |
| 650000..650013 (c6500) | 65000000 | Losa 2 % |
| 105000/10, 131000..20 (3 tiradas en 131000), 141002, 671000..671100, 678000..678099 | varios | Centelleante 5 % |
| 206001 (c2060) | 20600100 | Centelleante 10 % |
| 222001 (c2220) | 22200100 | Hueso de dragón 100 % |
| 317000 (c3170) | 31700000 | Hueso de dragón 10 % |
| 661000/01 (c6610) | 66100000 | Hueso de dragón 5 % |
| 683000 (c6830) | 68300000 | Hueso de dragón 25 %, Losa 5 % |
| 764300 (McDuff) | 76430000 (modo 3) | Losa 100 % |

- Lote alternativo `EP +0x138` (modo sin identificar, enemies.md): mismas filas con menos % (c6500 Losa 1 %, c6560 Trozo
  1 %, c6610 Hueso 0.1 %, c6711 Centelleante 0.5 %, c2060 1 %).
- Los mapas pueden poner otro lote por generador (`GeneratorParam +0x88`); esos no están listados aquí.
- **Nombres de los enemigos**: el juego no trae una tabla de nombres por modelo; quedan por id.

### 7.3 Tesoros y eventos (`ItemLotParam2_Other`)
Suma de cantidades en filas de modo 3 (las de objetos fijos): Fragmento 50, Grande 52, Trozo 44, Losa 16,
Centelleante 83, Hueso 56 (más 2–3 de cada en filas de modo 0/2). **No se comprobó qué filas están colocadas en el
mundo** (ni NG+), así que es un techo, no un recuento.

## 8. Mapeo actual del mod (implementado 2026-11-09, `SoulsUpgrade`; diseño del usuario, CHANGELOG)
- La tabla vieja material → nivel (madera +0 … netherita +10) **se quitó**. El nivel se guarda en el objeto
  (`soulsmode:reinforcement`) y se sube en la mesa de herrería con la titanita de esta nota.
- Exportado por `tools/re/weapons.py`: `attack.reinforceCost` (§2.2, por nivel destino), `attack.infusionMask` (CAS
  `+0x00`), `attack.infusions` (§4: elemento, cantidad, escala y coeficientes de la fila WSA `+ i`), y en cada pieza
  de `armor`: `maxLevel`, `defenceMax`, `absorptionMax` (ARP `+0x30..`, `+0x40..`) y `reinforceCost` (ARP `+0x70`);
  `guardStabilityByLevel` (WRP `+0x74 + 4·nivel`).
- Tiers del usuario (+5 / +10 / +15) reescalados al máximo DS2 del arma: nivel n de un tier T = nivel DS2 n·máx/T;
  material = fila RCP del nivel DS2 ⌈n·máx/T⌉. Armaduras: niveles y filas de DS2 tal cual.
- Infusiones como encantamientos (`soulsmode:<tipo>_infusion`): Fuego, Magia, Electricidad, Oscuridad, Veneno,
  Básico, Encantado. Hemorragia y Mundano no están (sin sangrado en el mod; coeficiente de `FUN_1401641c0` sin sacar).

## Sin confirmar
- Máximo de pila 99 (`ItemParam +0x4a`) y precios (`+0x30` compra / `+0x34` venta; precio de tienda × `+0x1c`).
- Código que aplica el refuerzo (resta de almas/material, `nivel + 1`) y el de imbuir; que imbuir conserve el nivel
  sale del texto de la Piedra pálida y de que nivel e infusión son bytes separados.
- Si imbuir cuesta lo mismo a cualquier nivel (CAC no tiene columna por nivel, pero no se trazó el menú).
- Qué opciones del menú da cada NPC; si Lenigrast imbuye; qué desbloquea la bandera 102680 de McDuff.
- Semántica de `ShopLineupParam +0x04` (10 / 500), `+0x08` / `+0x0c` y de 255 como "ilimitado".
- `WRP +0x50..+0x70` como reducciones de guardia; si la infusión del escudo las cambia (los textos dicen que las
  piedras dan reducción en escudos; el código de guardia no está trazado).
- Si el nivel cambia la durabilidad.
- `ARP +0x64` (10.0), `WSA +0x04`, `PlayerWeaponReinforceParam` (3 filas: 100..309 y 50..149 en `+0x00`, sin uso
  encontrado), tercer byte de la tabla `0x1410c3e10`, uso de la fila CAS 10.
- Qué mapas son m10_04 / m10_16 / m10_14 / m10_32 / m10_19 (no hay tabla de nombres leída) y qué enemigo es cada modelo.
- Lotes por generador de mapa y qué tesoros están colocados.
