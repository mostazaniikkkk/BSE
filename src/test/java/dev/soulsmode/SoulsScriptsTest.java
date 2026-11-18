package dev.soulsmode;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

	/** The time budget cannot be swallowed with pcall, xpcall or a coroutine. */
	@Test
	void timeoutCannotBeCaught() {
		SoulsScripts.load(Map.of(OK, """
				function with_pcall() while true do pcall(function() while true do end end) end end
				function with_xpcall()
				  while true do xpcall(function() while true do end end, function() while true do end end) end
				end
				function with_coroutine()
				  while true do coroutine.resume(coroutine.create(function() while true do end end)) end
				end
				function f() return "ok" end
				"""));
		for (String name : new String[] {"with_pcall", "with_xpcall", "with_coroutine"}) {
			long start = System.currentTimeMillis();
			assertThrows(LuaError.class, () -> SoulsScripts.callForTest(OK, name), name);
			assertTrue(System.currentTimeMillis() - start < 2000, name);
		}
		assertEquals("ok", assertDoesNotThrow(() -> SoulsScripts.callForTest(OK, "f")));
	}

	/** A call that fails leaves the script usable: errors, a stack overflow and a timeout, many times over. */
	@Test
	void scriptSurvivesItsErrors() throws LuaError {
		SoulsScripts.load(Map.of(OK, """
				calls = 0
				function boom() calls = calls + 1 error("boom") end
				function deep() local function g() return 1 + g() end return g() end
				function spin() while true do end end
				function f() return "ok" .. calls end
				"""));
		assertThrows(LuaError.class, () -> SoulsScripts.callForTest(OK, "deep"));
		assertEquals("ok0", SoulsScripts.callForTest(OK, "f"));
		assertThrows(LuaError.class, () -> SoulsScripts.callForTest(OK, "spin"));
		for (int i = 0; i < 5000; i++) {
			assertThrows(LuaError.class, () -> SoulsScripts.callForTest(OK, "boom"));
		}
		assertEquals("ok5000", SoulsScripts.callForTest(OK, "f"));
	}
}
