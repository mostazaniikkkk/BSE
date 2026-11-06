package dev.soulsmode;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Lua sandbox: plain Lua works, loading code / files is gone, endless loops are stopped. */
class SoulsScriptsTest {
	private static final ResourceLocation OK = ResourceLocation.fromNamespaceAndPath("t", "ok");
	private static final ResourceLocation LOOP = ResourceLocation.fromNamespaceAndPath("t", "loop");

	@Test
	void sandbox() throws LuaError {
		SoulsScripts.load(Map.of(
				OK, """
						function f() return tostring(load) .. tostring(io) .. tostring(os) .. string.rep("a", 2) .. math.floor(2.5) end
						function spin() while true do end end
						""",
				LOOP, "while true do end"));
		assertTrue(SoulsScripts.loaded(OK));
		assertFalse(SoulsScripts.loaded(LOOP));
		assertEquals("nilnilnilaa2", SoulsScripts.callForTest(OK, "f"));
		long start = System.currentTimeMillis();
		assertThrows(LuaError.class, () -> SoulsScripts.callForTest(OK, "spin"));
		assertTrue(System.currentTimeMillis() - start < 2000);
	}
}
