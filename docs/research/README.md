# Research: DS2 → Souls Mode

Flujo: **Ghidra / params → spec → Java (`dev.soulsmode.ds2`) → tests**.

1. Cada sistema (stats, estamina, poise, curación, etc.) tiene su nota aquí: `docs/research/<sistema>.md`.
2. La nota documenta la fórmula en pseudocódigo propio, con la dirección o función de Ghidra
   o el param (`PlayerStatusParam`, etc.) de donde salió, y algunos valores de referencia medidos en el juego.
3. La implementación en `src/main/java/dev/soulsmode/ds2` es Java puro, sin imports de Minecraft.
4. Los valores de referencia se convierten en tests en `src/test/java/dev/soulsmode/ds2`.
5. La capa de Minecraft (mixins, attachments, HUD) solo llama al paquete `ds2`.

Reglas: aquí no se guarda código decompilado copiado tal cual ni assets del juego (modelos, texturas, sonidos).
Solo se documentan comportamientos y números, y se reimplementan.
