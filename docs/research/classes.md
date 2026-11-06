# Clases iniciales — textos y equipo

Fuentes: `gamedata:/menu/Text/{NeutralSpanish,English}/{common,charamaking,bofire}.fmg` (extraídos con
`extract_bdt.py`, volcados con `dump_fmg.py` → `D:\Reversing\DS2\out\classes\text\`), `PlayerStatusParam`
(regulation, leído con `tools/re/params.py`; volcado completo en `D:\Reversing\DS2\out\classes\equip.txt`,
script `out\classes\py\equip.py`). NeutralSpanish = es_mx.

## Lista de clases — `FUN_1400e06c0`
Arma la lista de la pantalla de clase con tuplas (nombre `common.fmg`, fila `PlayerStatusParam`, descripción
`charamaking.fmg`). Orden en pantalla = orden del código:

| # | common id | fila | desc. id | Nombre es_mx | Nombre EN |
|---|---|---|---|---|---|
| 1 | 11010001 | 20 | 11090300 | Guerrero | Warrior |
| 2 | 11010002 | 30 | 11090310 | Caballero | Knight |
| 3 | 11010009 | 100 | 11090380 | Espadachín | Swordsman |
| 4 | 11010004 | 50 | 11090330 | Bandido | Bandit |
| 5 | 11010006 | 70 | 11090350 | Clérigo | Cleric |
| 6 | 11010007 | 80 | 11090360 | Hechicero | Sorcerer |
| 7 | 11010008 | 90 | 11090370 | Explorador | Explorer |
| 8 | 11010010 | 110 | 11090390 | Marginado | Deprived |

Fila por índice también en `FUN_140203e80` (default → fila 10). Hint de la pantalla: `charamaking` 11030100.

## Descripciones (`charamaking.fmg`; `\n` = salto de línea del juego)
| Clase | es_mx | EN |
|---|---|---|
| Guerrero | Guerrero con cicatrices de guerra.\nGran fuerza y destreza.\nHábil con las armas. | Battle-scarred warrior.\nHigh strength, dexterity.\nSkilled with weapons. |
| Caballero | Caballero trotamundos.\nAdaptabilidad y PS elevados.\nEs difícil derribarlo. | Traveling knight.\nHigh HP and adaptability.\nTough to take down. |
| Espadachín | Espadachín habilidoso.\nLucha grácilmente con fuertes\narmas en ambas manos. | Finely skilled swordsman.\nFights gracefully with strong\nweapons in both hands. |
| Bandido | Un forajido despiadado.\nGran destreza y hábil con el arco.\nLucha bien a distintas distancias. | A merciless outlaw.\nHigh dexterity, skilled with bow;\nfights well at various ranges. |
| Clérigo | Clérigo peregrino.\nSu gran fe y sus milagros\nguían sus pasos. | Cleric on pilgrimage.\nHigh faith and miracles\nguide the way. |
| Hechicero | Hechicero erudito.\nLanza hechizos con elevada\ninteligencia y aprendizaje. | Knowledgeable sorcerer.\nCasts sorceries with high\nintelligence and attunement. |
| Explorador | Explorador curtido.\nNo es demasiado poderoso,\npero tiene muchos objetos. | Well-traveled explorer.\nNot terribly powerful,\nbut has many items. |
| Marginado | Desnudo, origen desconocido.\nNo tiene nada para luchar,\nsalvo la vitalidad de su piel. | Unclothed, origin unknown.\nHas nothing to fight with,\nexcept life-affirming flesh. |

## Textos de UI (es_mx / EN)
`charamaking.fmg`: 10010100 Clase y obsequio / Class & gift · 10020100 Determinar clase y obsequio ·
11010100 **Clase** / Class · 11010110 **Obsequio** / Gift · 11010120 Restablecer / Reset ·
11010130 Completar creación / Finalize creation · 11020100 Determinar clase / Determine class ·
11020110 Determinar obsequio · 11020120 Revertir cambios · 11020130 Finalizar personaje y completar creación ·
11020140 Muestra los datos de cada sección. · 80090130 Seleccionar clase y obsequio ·
80090100 Los ajustes actuales se perderán.\n¿Restablecer? · 212 ¿Es tu verdadero yo? ·
110010 Intenta recordar tu nombre · 11030100 `⑤: Seleccionar ⑲: OK ⑳: Atrás ㎏: Controles de cámara ⑯: Completar creación ⑥: Ayuda`.

`common.fmg` (etiquetas de estado; **ojo: la abreviatura es_mx no es la traducción literal de la EN del mismo id**):

| id | es_mx | EN | | id | es_mx | EN |
|---|---|---|---|---|---|---|
| 909000 | Nvl. | Lv | | 909009 | DES | DEX |
| 909001 | Puntos | Points | | 909010 | INT | INT |
| 909002 | Almas | Souls | | 909011 | FE | FTH |
| 909003 | Almas nec. | Req souls | | 909012 | ADP | ADP |
| 909004 | VIT | VGR | | 909014 | PS | HP |
| 909005 | AGU | END | | 909015 | Energía | Stamina |
| 909006 | VIG | VIT | | 909016 | Peso equ. | Equip load |
| 909007 | APR | ATN | | 909038 | Agilidad | AGL |
| 909008 | FUE | STR | | 909049 | Estabilidad | Poise |

Resto (909017–909052: defensas, bonos, ranuras de arma) en `out\classes\text\menu__Text__NeutralSpanish__common.txt`.

Obsequios (`charamaking` 11010500–11010570 nombre, 11090500–11090570 desc.; filas `PlayerStatusParam` 500–570):
Nada · Anillo de vida (40010000) · Efigie humana (60151000 ×1) · Material de curación (60010000 ×10,
60070000 ×5, 60020000 ×3, 60030000 ×1) · Hueso de regreso (60350000) · Semilla de Árbol de gigantes (62170000) ·
Ascua de la adversidad (60527000) · Objeto petrificado (60511000).

## Layout de equipo en `PlayerStatusParam` — `FUN_140155b90`
Convierte una fila en 0x34 ranuras `{u32 id, …, u8 categoría, u16 cantidad, …}`; nombres de ranura en la tabla
`0x141568be0` (`FUN_1401544d0`). Índices de ranura: `FUN_14034e310` (+0), `e2e0` (+6), `e2b0` (+0xc),
`e2f0` (+0x10), `e2d0` (+0x14), `e300` (+0x1e), `e2c0` (+0x2c).

| Campo | Ranura | Cantidad |
|---|---|---|
| +0x88 / +0x8c / +0x90 (s32) | R_hand1/2/3 | 1; bytes +0xc0,+0xc1 / +0xc4,+0xc5 / +0xc8,+0xc9 a la ranura (significado no identificado) |
| +0x94 / +0x98 / +0x9c | L_hand1/2/3 | 1; bytes +0xcc,+0xcd / +0xd0,+0xd1 / +0xd4,+0xd5 |
| +0xa0..+0xac | Helmet, Armor, Guntlet, Leggings | byte +0xd8..+0xdb (no identificado) |
| +0xb0..+0xbc | Ring[0..3] | 1 |
| +0xec, +0xf0 | Arrow1, Arrow2 | u16 +0xfc, +0xfe |
| +0xf4, +0xf8 | Bolt1, Bolt2 | u16 +0x100, +0x102 |
| +0x24..+0x48 (10) | Item[0..9] | u16 +0x4c..+0x5e |
| +0x60..+0x84 (10) | Spell[0..9] | 1 |
| +0x104..+0x120 (8) | Gesture[0..7] | 1 |

Uso: `FUN_140347760` equipa la fila guardada en `+0x840` (puesta por `FUN_1400de610` desde la fila de clase);
`FUN_1400de610` mete en inventario la lista de otra fila (`+0x10c`, probablemente el obsequio — no confirmado) con
`FUN_1401ac3a0(id, cantidad)` (cantidad = 1 si la categoría de ranura es 7 = hechizo). `FUN_1400e3ca0` (vista previa)
añade los objetos/hechizos/flechas del obsequio a los huecos libres de la clase.
Campos no leídos aquí: +0xdc..+0xe4 (3 × s32, distintos por clase), +0xe8/+0xe9 (u8, usados en `FUN_140347760`
vía `FUN_1401ad140`), +0x124/+0x125 (u8, `FUN_1400de610`).

## Equipo inicial (nombres `itemname.fmg` es_mx / EN)
Todas las clases: gestos 63000000, 63001000, 63003000, 63006000, 63007000, 63009000, 63012000, 63013000
(Señalar, No muerdo, Hacer reverencia, Saludo, Puño en alto, Grito de guerra, De acuerdo, Ni hablar).
Ranura vacía: `3400000` (fila en `WeaponParam`) y `21001100..21001103` (sin fila en `ArmorParam`) **no tienen texto**
en `itemname.fmg`; qué representan no está verificado.

| Clase (fila) | Der. | Izq. | Armadura (cabeza/torso/brazos/piernas) | Anillo | Objetos | Otros |
|---|---|---|---|---|---|---|
| Guerrero (20) | 1200000 Espada recta rota | 11020000 Parma de hierro | 27440100 Yelmo normal · 27440101 Armadura de cuero rígido · 27440102 Guanteletes de cuero rígido · 27440103 Botas de cuero rígido | — | 60010000 Gema de vida ×10 | — |
| Caballero (30) | 1230000 Espada ancha | — | 21001100 · 23130101 Armadura de cetrero · 23130102 Guantes de cetrero · 23130103 Botas de cetrero | — | Gema de vida ×10 | — |
| Espadachín (100) | 1650000 Cimitarra (+0xc0=1) | 1210000 Espada corta (+0xcc=1) | 21030100 Capucha de viajero · 21030101 Abrigo de viajero · 21030102 Brazales de viajero · 21030103 Botas de viajero | — | Gema de vida ×10 | — |
| Bandido (50) | 2000000 Hacha de mano; R2 4200000 Arco corto | — | 22110100 Yelmo de bandido con púas · 22110101 Armadura de bandido · 22110102 Guanteletes de bandido · 22110103 Botas de bandido | — | Gema de vida ×10 | Arrow1 60760000 Flecha de madera ×25 |
| Clérigo (70) | 4010000 Campana sagrada del clérigo; R2 2410000 Mazo | — | 21001100 · 22310101 Túnicas Archidragón · 21001102 · 21001103 | — | Gema de vida ×10 | Spell[0] 32010000 Curación |
| Hechicero (80) | 3800000 Bastón de hechicero; R2 1000000 Daga | — | 21001100 · 22180101 Túnica de mago Hueco negra · 21001102 · 21100103 Pantalones importados | — | Gema de vida ×10 | Spell[0] 31010000 Flecha de alma |
| Explorador (90) | 1000000 Daga | — | 21140100 Sombrero / 21140101 Abrigo / 21140102 Guantes / 21140103 Botas de mercader viajero | 40060000 Anillo de cuarzo de hechizo | Gema de vida ×20 · 60550000 Urna de brujería ×8 · 60240000 Viscosidad aromática ×4 · 60450000 Piedra de prisma ×5 · 60320000 Moneda oxidada ×2 · 60410000 Polvo de reparación ×1 · 60536000 Piedra de cierre de Pharros ×1 | Bolt1 60910000 Perno de madera ×20 |
| Marginado (110) | — | — | 21001100..21001103 | — | — | — |

EN: Broken Straight Sword, Iron Parma, Standard Helm, Hard Leather Armor/Gauntlets/Boots, Lifegem, Broadsword,
Falconer Armor/Gloves/Boots, Scimitar, Shortsword, Wanderer Hood/Coat/Manchettes/Boots, Hand Axe, Short Bow,
Spiked Bandit Helm, Bandit Armor/Gauntlets/Boots, Wood Arrow, Cleric's Sacred Chime, Mace, Archdrake Robes, Heal,
Sorcerer's Staff, Dagger, Black Hollow Mage Robe, Imported Trousers, Soul Arrow, Traveling Merchant
Hat/Coat/Gloves/Boots, Spell Quartz Ring, Witching Urn, Aromatic Ooze, Prism Stone, Rusted Coin, Repair Powder,
Pharros' Lockstone, Wood Bolt.

## Sin confirmar
- Significado de los bytes +0xc0..+0xdb (Espadachín tiene 1 en +0xc0 y +0xcc).
- Qué fila es `+0x10c` en `FUN_1400de610` (¿obsequio?).
- Qué son 3400000 / 21001100..03 (ranura vacía, sin texto).
