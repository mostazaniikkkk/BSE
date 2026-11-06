package dev.soulsmode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BonfireNamesTest {
	@Test
	void trimsAndKeepsText() {
		assertEquals("Majula", BonfireNames.sanitize("  Majula  "));
		assertEquals("Hoguera del bosque", BonfireNames.sanitize("Hoguera del bosque"));
		assertEquals("Cañón ¿sí?", BonfireNames.sanitize("Cañón ¿sí?"));
	}

	@Test
	void stripsFormattingAndControlCharacters() {
		assertEquals("Rojo", BonfireNames.sanitize("§cRojo"));
		assertEquals("ab", BonfireNames.sanitize("a§lb§"));
		assertEquals("ab", BonfireNames.sanitize("a\nb\t"));
	}

	@Test
	void emptyClears() {
		assertEquals("", BonfireNames.sanitize(""));
		assertEquals("", BonfireNames.sanitize("   "));
		assertEquals("", BonfireNames.sanitize("§a"));
		assertEquals("", BonfireNames.sanitize(null));
	}

	@Test
	void cutsTo32() {
		String name = BonfireNames.sanitize("x".repeat(40));
		assertEquals(32, name.length());
		assertEquals("a".repeat(31), BonfireNames.sanitize("a".repeat(31) + " bcd"));
	}
}
