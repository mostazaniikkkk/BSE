package dev.soulsmode.client.qa;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.client.camera.BowAim;
import dev.soulsmode.client.camera.SoulsCamera;
import dev.soulsmode.entity.SoulsBullet;
import dev.soulsmode.network.AttackPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/**
 * QA only (tools/qa): with {@code -Dsoulsmode.qa=bow} a dev client connected to the QA server drives the bow and
 * crossbow through the real key mappings (no OS input, so it never touches the user's game) and logs what happens.
 * Does nothing without the property.
 */
public final class QaDriver {
	private static int tick = -1;
	private static int bulletsSeen;
	private static boolean attackHeld;
	private static net.minecraft.world.phys.Vec3 first;
	private static int firstId;
	private static final java.util.Set<Integer> logged = new java.util.HashSet<>();
	private static boolean useHeld;

	private QaDriver() {
	}

	public static void init() {
		if ("poise".equals(System.getProperty("soulsmode.qa"))) {
			ClientTickEvents.END_CLIENT_TICK.register(QaDriver::poise);
			return;
		}
		if (!"bow".equals(System.getProperty("soulsmode.qa"))) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(QaDriver::tick);
	}

	private static void log(String text, Object... args) {
		SoulsMode.LOGGER.info("[BSE QA] " + text, args);
	}

	private static void click(KeyMapping key, boolean down) {
		InputConstants.Key k = InputConstants.getKey(key.saveString());
		if (down) {
			KeyMapping.click(k);
		}
		KeyMapping.set(k, down);
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen && tick < 590) {
			mc.setScreen(null);
		}
		// The QA script sets the player up through RCON (bow, shield, arrows; later the crossbow): wait for it.
		if (tick < 0 && !mc.player.getMainHandItem().is(Items.BOW)) {
			if (mc.player.tickCount % 40 == 0) {
				log("NEED bow");
			}
			return;
		}
		if (tick == 420 && !mc.player.getMainHandItem().is(Items.CROSSBOW)) {
			if (mc.player.tickCount % 40 == 0) {
				log("NEED crossbow");
			}
			return;
		}
		if (tick < 0) {
			mc.options.setCameraType("first".equals(System.getProperty("soulsmode.qa.camera")) ? net.minecraft.client.CameraType.FIRST_PERSON : net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		}
		tick++;
		int bullets = 0;
		for (var e : mc.level.entitiesForRendering()) {
			if (e instanceof SoulsBullet) {
				bullets++;
				if (first == null || firstId != e.getId()) {
					first = e.position();
					firstId = e.getId();
					continue;
				}
				var d = e.position().subtract(first);
				if (d.lengthSqr() > 1 && !logged.contains(e.getId())) {
					logged.add(e.getId());
					log("t={} bullet flies yaw {} pitch {} (body yaw {} pitch {})", tick,
							Math.toDegrees(Math.atan2(-d.x, d.z)), -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())),
							mc.player.getYRot(), mc.player.getXRot());
				}
			}
		}
		if (bullets > 0 && bulletsSeen == 0) {
			log("t={} bullet visible on the client ({})", tick, bullets);
		}
		bulletsSeen = bullets;
		if (attackHeld) {
			KeyMapping.set(InputConstants.getKey(mc.options.keyAttack.saveString()), true);
		}
		if (useHeld) {
			KeyMapping.set(InputConstants.getKey(mc.options.keyUse.saveString()), true);
		}
		switch (tick) {
			case 80 -> log("t={} one-handed bow: arrows={} screen={}", tick, arrows(mc), mc.screen);
			case 85 -> {
				log("A: one-handed bow, attack key tap");
				click(mc.options.keyAttack, true);
			}
			case 87 -> click(mc.options.keyAttack, false);
			case 130 -> {
				log("t={} after A: arrows={}", tick, arrows(mc));
				ClientPlayNetworking.send(new AttackPayloads.Grip(false));
			}
			case 160 -> {
				log("t={} two-handed={} B: attack key held 30 ticks", tick, BowAim.bowTwoHanded(mc.player));
				click(mc.options.keyAttack, true);
				attackHeld = true;
			}
			case 190 -> {
				attackHeld = false;
				click(mc.options.keyAttack, false);
			}
			case 230 -> {
				log("t={} after B: arrows={}", tick, arrows(mc));
				log("C: attack key tap, then tap again at +30");
				click(mc.options.keyAttack, true);
			}
			case 232, 262 -> click(mc.options.keyAttack, false);
			case 260 -> click(mc.options.keyAttack, true);
			case 300 -> {
				log("t={} after C: arrows={}", tick, arrows(mc));
				log("D: aim (use key held); camera yaw {} pitch {}", SoulsCamera.yaw(), SoulsCamera.pitch());
				click(mc.options.keyUse, true);
				useHeld = true;
			}
			case 305 -> log("t={} aim active={} yaw {} pitch {} body {}", tick, BowAim.active(), BowAim.yaw(),
					BowAim.pitch(), mc.player.getYRot());
			case 340 -> {
				log("t={} after 30 ticks of +20 px/tick mouse x and -1 y: aim yaw {} pitch {} body {}", tick,
						BowAim.yaw(), BowAim.pitch(), mc.player.getYRot());
				log("E: fire while aiming (attack held 25 ticks)");
				click(mc.options.keyAttack, true);
				attackHeld = true;
			}
			case 365 -> {
				attackHeld = false;
				click(mc.options.keyAttack, false);
			}
			case 400 -> {
				log("t={} after E: arrows={} aim yaw {} pitch {} body {}", tick, arrows(mc), BowAim.yaw(),
						BowAim.pitch(), mc.player.getYRot());
				useHeld = false;
				click(mc.options.keyUse, false);
			}
			case 405 -> log("t={} aim released: active={} camera yaw {} pitch {}", tick, BowAim.active(),
					SoulsCamera.yaw(), SoulsCamera.pitch());
			case 410 -> ClientPlayNetworking.send(new AttackPayloads.Grip(false));
			case 460 -> {
				log("t={} F: crossbow one-handed (two-handed={}), attack key held 20 ticks", tick,
						BowAim.bowTwoHanded(mc.player));
				click(mc.options.keyAttack, true);
				attackHeld = true;
			}
			case 480 -> {
				attackHeld = false;
				click(mc.options.keyAttack, false);
			}
			case 540 -> {
				log("t={} after F: arrows={}", tick, arrows(mc));
				log("G: crossbow one-handed, use key held 20 ticks");
				click(mc.options.keyUse, true);
				useHeld = true;
			}
			case 560 -> {
				useHeld = false;
				click(mc.options.keyUse, false);
			}
			case 590 -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
			case 605 -> net.minecraft.client.Screenshot.grab(mc.gameDirectory, "qa_inventory.png", mc.getMainRenderTarget(),
					message -> log("screenshot: {}", message.getString()));
			case 620 -> {
				log("t={} after G: arrows={}", tick, arrows(mc));
				log("done");
				mc.stop();
			}
			default -> {
			}
		}
		if (tick > 305 && tick < 340) {
			// Mouse movement through the normal path (MouseHandler -> Entity.turn), as Minecraft's sensitivity scales it.
			double curve = Math.pow(mc.options.sensitivity().get() * 0.6 + 0.2, 3) * 8;
			mc.player.turn(20 * curve, -1 * curve);
		}
	}

	private static int poiseTick = -1;
	private static int lastKind;
	private static int shots;
	private static int shotAt = -1;

	/**
	 * {@code -Dsoulsmode.qa=poise}: the QA script equips armour and summons attackers through RCON (NEED armour); this
	 * stands still in third person, logs every reaction and takes a few screenshots a moment into each.
	 */
	private static void poise(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
			mc.setScreen(null);
		}
		mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		if (poiseTick < 0 && mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()) {
			if (mc.player.tickCount % 40 == 0) {
				log("NEED armour");
			}
			return;
		}
		poiseTick++;
		int kind = dev.soulsmode.client.anim.PlayerReactionClient.playingKind(mc.player);
		if (kind != 0 && kind != lastKind) {
			log("t={} reaction {} health {}", poiseTick, kind, mc.player.getHealth());
			shotAt = poiseTick + (kind == dev.soulsmode.network.PoisePayloads.HELD ? 4 : 6);
		}
		lastKind = kind;
		if (poiseTick == shotAt && shots < 6) {
			int n = shots++;
			net.minecraft.client.Screenshot.grab(mc.gameDirectory, "qa_poise_" + n + "_k" + kind + ".png",
					mc.getMainRenderTarget(), message -> log("screenshot {}", n));
		}
		if (poiseTick == 20) {
			log("SPAWN attackers");
		}
		if (poiseTick >= 900) {
			log("done");
			mc.stop();
		}
	}

	private static int arrows(Minecraft mc) {
		return mc.player.getInventory().countItem(Items.ARROW);
	}
}
