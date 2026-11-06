# Souls Mode — reglas obligatorias

Mod Fabric 1.21.1 que porta las mecánicas del jugador de Dark Souls II SotFS a un modo de juego de Minecraft.
Estas reglas las puso el usuario y **no se negocian**.

## 1. Nada inventado ni deducido
- Toda mecánica, número, tiempo **o animación** debe salir del código o los datos de DS2: decompilación con Ghidra
  (`D:\Reversing\DS2`), params de la regulation, TAE, `c0001.nmb` o `.nsa`.
  La fuente (dirección, param, archivo) se cita en `docs/research/*.md`.
- **Está prohibido crear o modificar poses o animaciones a mano.** El movimiento sale de los `.nsa` de DS2,
  decodificados y retargeteados al rig de Minecraft. Que los tiempos vengan de DS2 no autoriza inventar las poses.
- Si algo aún no se puede sacar de DS2, **no se rellena con valores plausibles**: se deja sin implementar y se avisa.
- Cualquier excepción necesita **autorización explícita del usuario antes** de hacerla. Si no hay autorización,
  no se cambia.
- **Toda simplificación se anota en el backlog, sin excepción.** Si algo se hace más simple que DS2 aunque el dato
  existe o se puede sacar, se anota en el mismo momento en `docs/todo-bse-extensible.md`, sección
  "Simplificaciones" (qué se simplificó, dónde en el código, qué dice DS2). **No está permitido simplificar sin
  anotarlo.** Solo sale del backlog si el usuario dice explícitamente que se queda así.
- **El usuario no se entera por terceros de lo que falta.** Avanzar de a poco está bien; lo que no está bien es que
  un QA o un video le muestre un hueco que no sabía. Al entregar cualquier mecánica, el reporte dice qué parte de
  DS2 quedó fuera (no porteado, sin confirmar, simplificado), y eso queda anotado en el backlog.

## 2. Primero el game feel
- No se activan costos ni reglas que cambien cómo se juega (estamina, salto…) hasta probar el game feel.
  Van detrás de un flag.

## 3. No dejar pedidos a medias
- Si el siguiente paso ya está definido, se hace en el mismo turno: análisis → implementación → build → prueba → reporte.
  Solo se pregunta ante un bloqueo real.
- **No pedir confirmación para decisiones obvias o secuenciales.** Si se sabe exactamente qué hacer, se hace sin
  detener el trabajo ni terminar el turno con "¿le doy?". Las preguntas son solo para bloqueos reales o excepciones a
  la regla 1.

## 4. Verificar en el juego antes de entregar
- Compilar con JDK 25 (`JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"`). `gradlew build` instala el jar en
  `.minecraft/mods`.
- Probar en el juego real, desde el launcher, conectado al **servidor QA** (`D:\Proyectos\Minecraft\QA Server`,
  `start.bat`, entrada "BSE QA" = 127.0.0.1 en Multijugador; RCON 127.0.0.1:25576), con `tools/qa/ui.ps1`.
  **Nunca abrir mundos de un jugador** (el orden de la lista cambia; el primero puede ser el mundo personal del usuario).
  Antes de probar, copiar el jar nuevo a `QA Server/mods`. No tomar el control del juego mientras el usuario lo usa.
- El idioma del juego es `es_mx`.

## 5. Temas cerrados
- La mancha de sangre está cerrada hasta que el usuario la reabra.
- **No se agregan mecanismos de curación de DS2**: la hoguera NO cura, no hay Estus, no hay gemas de vida. Minecraft
  ya tiene los suyos. No proponerlos ni listarlos como pendientes.
- **Los hechizos existen solo en datapacks**: el mod trae el motor, no hechizos propios.
