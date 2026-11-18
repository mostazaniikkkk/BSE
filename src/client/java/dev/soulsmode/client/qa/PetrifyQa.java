package dev.soulsmode.client.qa;

import dev.soulsmode.SoulsAttachments;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.SoulsStatues;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * QA (tools/qa/petrify_setup.js): statues, the branch dialog and the prayer, the status gauges, a petrification death.
 * Logs "STEP <name>" for the RCON script and takes screenshots (qa_petrify_*.png).
 */
public final class PetrifyQa {
	private static int tick = -1;

	private PetrifyQa() {
	}

	private static void log(String text, Object... args) {
		SoulsMode.LOGGER.info("[BSE QA] " + text, args);
	}

	private static void shot(Minecraft mc, String name) {
		net.minecraft.client.Screenshot.grab(mc.gameDirectory, "qa_petrify_" + name + ".png", mc.getMainRenderTarget(),
				m -> log("screenshot {}", name));
	}

	private static Mob statue(Minecraft mc, boolean petrified) {
		Mob best = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof Mob m && SoulsStatues.isPetrified(m) == petrified && m.distanceTo(mc.player) < 4.5
					&& (best == null || m.distanceTo(mc.player) < best.distanceTo(mc.player))) {
				best = m;
			}
		}
		return best;
	}

	private static void look(Minecraft mc, Entity e) {
		double dx = e.getX() - mc.player.getX();
		double dz = e.getZ() - mc.player.getZ();
		double dy = e.getY() + e.getBbHeight() * 0.6 - mc.player.getEyeY();
		mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
		mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
	}

	private static void interact(Minecraft mc) {
		Mob m = statue(mc, true);
		if (m == null) {
			log("no statue in reach");
			return;
		}
		look(mc, m);
		mc.gameMode.interact(mc.player, m, InteractionHand.MAIN_HAND);
		log("interact {} petrified={}", m.getType().toShortString(), SoulsStatues.isPetrified(m));
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (tick > 400 && tick < 540 && mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
			// The body behind the death screen: an empty screen instead (null would bring the death screen back).
			mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.empty()) {
				@Override
				public boolean isPauseScreen() {
					return false;
				}

				@Override
				public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int x, int y, float d) {
				}
			});
		}
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
			mc.setScreen(null);
		}
		if (tick < 0) {
			if (mc.player.tickCount < 80) {
				return;
			}
			tick = 0;
			log("STEP setup");
		}
		tick++;
		if (tick % 20 == 0) {
			var meters = mc.player.getAttached(SoulsAttachments.STATUS_METERS);
			log("t={} meters={} dead={} screen={}", tick, meters, mc.player.isDeadOrDying(),
					mc.screen == null ? "-" : mc.screen.getClass().getSimpleName());
		}
		if (tick > 400 && tick < 470 && tick % 3 == 0) {
			var pl = mc.player;
			log("body t={} removed={} deathTime={} inLevel={} pos={} anim={}", tick, pl.isRemoved(), pl.deathTime,
					mc.level.getEntity(pl.getId()) != null, pl.position(), pl.getPose());
		}
		switch (tick) {
			case 100 -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				Mob m = statue(mc, true);
				log("statue near: {}", m == null ? "none" : m.getType().toShortString());
				if (m != null) {
					look(mc, m);
				}
			}
			case 110 -> shot(mc, "statues");
			case 120 -> log("STEP gauges");
			case 150 -> shot(mc, "hud_gauges");
			case 160 -> log("STEP curse");
			case 172 -> shot(mc, "hud_cursed");
			case 220 -> interact(mc);
			case 226 -> shot(mc, "no_branch");
			case 240 -> log("STEP branch");
			case 270 -> interact(mc);
			case 285 -> shot(mc, "dialog");
			case 290 -> {
				if (mc.screen instanceof ConfirmScreen screen) {
					for (var child : screen.children()) {
						if (child instanceof Button b && b.getMessage().getString().equals(
								net.minecraft.network.chat.CommonComponents.GUI_YES.getString())) {
							b.onPress();
							log("pressed yes");
						}
					}
				} else {
					log("no dialog");
				}
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
			case 305 -> shot(mc, "pray");
			case 360 -> {
				Mob freed = statue(mc, false);
				log("freed: {} noAi={}", freed == null ? "none" : freed.getType().toShortString(),
						freed != null && freed.isNoAi());
				shot(mc, "freed");
			}
			case 390 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			case 400 -> log("STEP petrify");
			case 425 -> shot(mc, "petrified_1s");
			case 435 -> shot(mc, "petrified_1_5s");
			case 450 -> shot(mc, "petrified_2s");
			case 465 -> shot(mc, "petrified_3s");
			case 480 -> shot(mc, "petrified_4s");
			case 539 -> mc.setScreen(null);
			case 540 -> {
				log("dead={} petrifiedDeath={}", mc.player.isDeadOrDying(),
						mc.player.getAttached(SoulsAttachments.PETRIFIED_DEATH));
				mc.player.respawn();
			}
			case 580 -> log("STEP hollow");
			case 640 -> {
				log("done");
				mc.stop();
			}
			default -> {
			}
		}
	}
}
