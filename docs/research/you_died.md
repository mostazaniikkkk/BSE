# Pantalla de muerte de DS2 ("YOU DIED" / "HAS MUERTO")

Pedido del usuario (2026-11-10): el mensaje de muerte de DS2 antes de la pantalla de muerte de Minecraft. Investigación
de un agente de solo lectura (2026-11-10); archivos en `D:\Reversing\DS2\out\youdied\` (PNG, renders,
`you_died_anim.json`, `scripts\flo.py`, `flo_sprites.py`, `flo_compose.py`, `render.py`, `fsbwav.py`). Exportador del
mod: `tools/re/you_died.py` (paso `menu` de `tools/ds2_build.py`).

## 1. Gráfico: una textura por idioma, no un texto
- `gamedata:/menu/tex/language/<idioma>.fetexbnd.dcx` (cadena del exe `0x10f2020`) → `YOU_DIED.tpf`, DXT5 1024×256.
  Fila de arriba (v 0–132) "DEFEAT" / "DERROTA"; fila de abajo (**v 126.15–256**) "YOU DIED" / **"HAS MUERTO"**. El rojo
  oscuro viene en la textura (≈ RGB 92, 12, 14). NeutralSpanish y Spanish son idénticos.
- No existe un texto fmg "YOU DIED".
- Franja: sprite de `waku_02.tpf` (`gamedata:/menu/99.febnd.dcx`, DXT5 1024×512), uv **(915.35, 304.9)–(997.6, 512)**:
  el trazo de pincel ondulado blanco de abajo a la derecha, teñido de negro (RGBA 000000FF) y girado 90°. La convención
  de uv (píxeles de la textura, origen arriba a la izquierda, sin inversión) está probada con seis sprites de otros
  layouts que caen exactos en piezas reconocibles del atlas.

## 2. Layout y animación (`gamedata:/menu/01.febnd.dcx` → `l01_14_message.flo`)
- FLO = formato de layout de FromSoft; **fps en el byte `+0x5a` = 30** (`FUN_140b503e0`: 1 / header[+0x5a]).
- **Escena id 3** (entrada 9 de la tabla de escenas, `0x1a2b8`), raíz (631.25, 322.55), clip 0x48 con dos pistas:
  texto (clip 0x47, 35 claves en `0xf080`) y franja (clip 0x45, 30 claves en `0xf710`, que coloca el clip 0x44 con la
  clave de `0xf020`). Clave de 0x30 bytes: x, y, m00, m11, m10, m01, RGBA, aux, flags, frame. Un frame usa la última
  clave anterior.
- Espacio del FLO: **1280×720, origen arriba a la izquierda, y hacia abajo** (probado con la pantalla de carga, el HUD de
  PS, el contador de almas y la barra del jefe).
- Tiempos (30 fps):
  | Frames | Tiempo | Texto | Franja |
  |---|---|---|---|
  | 1 → 30 | 0 → 0.967 s | alfa 0 → 255 (llega en el frame 28), escala 0.817 → 1.0 con suavizado | se abre desde una línea (escala 2.98 × 0.005 → 5.35 × 4.75) |
  | 30 → 82 | hasta 2.70 s | fijo | sin claves |
  | 83 → 87 | 2.73 → 2.87 s | alfa 205, 154, 102, 51, 0 | — |
  | 102 | 3.37 s | etiqueta 0x65 del clip 0x48: `FUN_140065730` cierra el grupo de mensajes | |
- En 1280×720 el texto queda en y ≈ 446–576 (**debajo del centro**, centro ≈ 71 % de la altura) y la franja es
  concéntrica con él.

## 3. Secuencia y sonido
- Muerte → `FUN_14013d250` → `FUN_14044f6d0` → `FUN_140191df0`: si murió el jugador local, mensaje EventResult **1**
  (`FUN_14018f830`, `ResultEventParam` fila 100: tipo FE 1, sin texto).
- Su trabajo `FUN_14018ed90` **espera 3.0 s** (`DAT_1410b0688`), luego `FUN_1401909c0`: tipo FE 1 → tabla
  `0x1410c3580[1]` = **3** → `FUN_1405070d0(3)`, que **oculta el resto del HUD** y muestra la escena; luego espera a que
  se cierre (WaitFeEndJob) y sigue con el viaje (`FUN_14044fde0`, ids −1/−1, presumiblemente el regreso).
- **Sonido `m000000010`** (probado por código: `FUN_1401905c0(this, 2)` → `FUN_1404a55f0(req, 1, 10)` con `"m%09d"`),
  en la misma llamada que muestra el cartel: `frpg2_smain.fev`, onda `Game\sound\frpg2_smain.fsb` sub-sonido 5, 48 kHz
  estéreo, 9.70 s; volumen 1.0, 2D, fade-in 100 ms.

## 4. En el mod (`YouDiedClient`, `tools/re/you_died.py`)
- En Modo Souls la pantalla de muerte de Minecraft espera: 3.0 s, luego la escena (frames 1 → 102 a 30 fps, cada frame
  con la matriz y el tinte del FLO), el HUD oculto mientras se ve, y el sonido `m000000010` al empezar (con la mezcla
  `SoulsSounds.MIX` del usuario). Después, la pantalla de muerte de Minecraft.
- Textura en inglés o en NeutralSpanish según el idioma del juego (`es_*` → "HAS MUERTO").
- El espacio 1280×720 se escala a la altura de la pantalla y se centra en horizontal (no trazado en DS2).

## Sin confirmar
- Cómo DS2 lleva el espacio 1280×720 a la pantalla real (relación de aspecto, escala de HUD).
- Qué ranura de la matriz es m10 y cuál m01 (solo lo apoya que franja y texto queden concéntricos), el modo de mezcla
  (se toma alfa × color del vértice), el color aux `10401100` y las banderas 0x130.
- Que la franja mantenga la clave del frame 30 hasta el cierre en el frame 102 (no tiene claves de salida).
- Desde qué momento cuentan los 3.0 s (el aviso de muerte `FUN_14013d250`: ¿al llegar a 0 PS o después?).
- Cuándo se detiene `m000000010` (su fade-out de 10 s) y si el evento usa solo esa onda.
