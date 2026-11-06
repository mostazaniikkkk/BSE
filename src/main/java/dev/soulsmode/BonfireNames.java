package dev.soulsmode;

/**
 * Bonfire names (the user's design, 2026-11-03): a player resting at a bonfire may name it; the name is shared by
 * everyone and shown in the bonfire and travel menus. Pure Java, tested by BonfireNamesTest.
 */
public final class BonfireNames {
	/** The user's limit. */
	public static final int MAX_LENGTH = 32;

	private BonfireNames() {
	}

	/**
	 * What the server stores for a requested name: formatting codes (section sign plus the next character) and control
	 * characters removed, surrounding spaces trimmed, cut to {@link #MAX_LENGTH}. An empty result clears the name.
	 */
	public static String sanitize(String raw) {
		if (raw == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '§') {
				i++;
				continue;
			}
			if (Character.isISOControl(c)) {
				continue;
			}
			out.append(c);
		}
		String name = out.toString().strip();
		if (name.length() > MAX_LENGTH) {
			name = name.substring(0, MAX_LENGTH).strip();
		}
		return name;
	}
}
