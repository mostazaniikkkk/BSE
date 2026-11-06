package dev.soulsmode.client.anim;

import com.google.gson.JsonObject;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.client.ZombieAnimClient;
import dev.soulsmode.ds2.Poise;
import dev.soulsmode.ds2.ReactionBlend;
import dev.soulsmode.network.PoisePayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The player's own reaction to a hit (SoulsPoise; docs/research/poise.md):
 * <ul>
 * <li>{@link PoisePayloads#KNOCKBACK}: BT_Damage's DmgKnockBack (hit_reaction.md s.2): the c0001 blend of the 32
 * clips (ZombieAnimClient.playerReaction) at the hit's speed, over everything, entering in 0.05 s (transitions
 * 0x2094.., poise.md s.7) and leaving to locomotion with StateControl_1 (stick held, 0.2 s) or StateControl (still,
 * 0.5 s), both (1 - cos) / 2. The local player moves by its root motion and cannot walk until the server's lock.</li>
 * <li>{@link PoisePayloads#HELD}: reaction 1 in BT_Move (SM 0x5c7): FeatherBlend2 of locomotion and the DmgKnockBackLight
 * blend scaled to DmgDuration, per-bone alphas of poise.json reaction1.overlay, entering in 0.1 s and leaving inside
 * the clip's event 80 over 0.15 s. During an action (attack, dodge, guard) nothing shows (no node reads it there).</li>
 * </ul>
 */
public final class PlayerReactionClient {
	public static final ResourceLocation KNOCKBACK_LAYER = SoulsMode.id("player_reaction");
	public static final ResourceLocation HELD_LAYER = SoulsMode.id("player_reaction_held");
	/** BT_Damage replaces the actions (1500) and sits under the guard layer (1600), which a reaction drops anyway. */
	private static final int KNOCKBACK_PRIORITY = 1550;
	/** Reaction 1 lives in BT_Move: above locomotion and the grip change, below the actions. */
	private static final int HELD_PRIORITY = 30;
	/** Blend into BT_Damage (poise.md s.7, transition attribute 0x3b). */
	private static final float ENTER = 0.05f;
	/** Exits to locomotion (hit_reaction.md s.2): 0x20b5 with the stick held, 0x20b4 still. */
	private static final float EXIT_MOVING = 0.2f;
	private static final float EXIT_STILL = 0.5f;
	/** DmgKnockBack tracks at speed 1 (poise.json staggerClips 160010): StateControl_1 and StateControl starts. */
	private static final float STATE_CONTROL_1 = 0.8333f;
	private static final float STATE_CONTROL = 1.0f;
	/** Minecraft bones of the overlay and the DS2 bone whose alpha each takes (body = Spine1, the retarget's). */
	private static final Map<String, String> OVERLAY_BONES = Map.of("body", "Spine1", "head", "Head",
			"right_arm", "R_UpperArm", "left_arm", "L_UpperArm");

	/** One reaction playing on a player. */
	private static final class Playing {
		final int kind;
		final long startNanos;
		final float speed;
		/** Seconds (real time) after which the exit may start, and the exit when it started (0 = not yet). */
		final float exitAt;
		long exitNanos;
		float exitBlend;
		/** HELD: the clip's event 80 in real time; and the alphas. */
		Map<String, Float> alphas;

		Playing(int kind, float speed, float exitAt) {
			this.kind = kind;
			this.startNanos = System.nanoTime();
			this.speed = speed;
			this.exitAt = exitAt;
		}
	}

	/** Per kind (HELD, KNOCKBACK): the reaction playing on each player. */
	private static final Map<AbstractClientPlayer, Playing> HELD = new WeakHashMap<>();
	private static final Map<AbstractClientPlayer, Playing> KNOCKBACK = new WeakHashMap<>();

	private static Map<AbstractClientPlayer, Playing> playing(int kind) {
		return kind == PoisePayloads.HELD ? HELD : KNOCKBACK;
	}

	/** The local player's root motion and walking lock. */
	private static ReactionBlend.Lower lower;
	private static float lowerSpeed;
	private static double lowerYaw;
	private static int lowerTicks;
	private static float lowerTime;
	private static int moveLockTicks;

	private PlayerReactionClient() {
	}

	public static void init() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(KNOCKBACK_LAYER, KNOCKBACK_PRIORITY,
				player -> controller(player, PoisePayloads.KNOCKBACK));
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(HELD_LAYER, HELD_PRIORITY,
				player -> controller(player, PoisePayloads.HELD));
		ClientPlayNetworking.registerGlobalReceiver(PoisePayloads.Reaction.TYPE, (payload, context) -> {
			if (context.client().level == null
					|| !(context.client().level.getEntity(payload.entityId()) instanceof AbstractClientPlayer player)) {
				return;
			}
			play(player, payload);
		});
		ClientTickEvents.END_CLIENT_TICK.register(PlayerReactionClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			HELD.clear();
			KNOCKBACK.clear();
			lower = null;
			moveLockTicks = 0;
		});
	}

	private static PlayerAnimationController controller(AbstractClientPlayer player, int kind) {
		PlayerAnimationController controller = new PlayerAnimationController(player,
				(c, state, animSetter) -> PlayState.STOP);
		controller.addModifierLast(new Mix(player, kind));
		return controller;
	}

	private static void play(AbstractClientPlayer player, PoisePayloads.Reaction r) {
		if (r.kind() == PoisePayloads.STOP) {
			HELD.remove(player);
			KNOCKBACK.remove(player);
			return;
		}
		String spec = String.format(Locale.ROOT, "c0001:%d:%d:%d:%.2f:%.4f:%.4f", r.category(), r.thrust() ? 1 : 0,
				r.up() ? 1 : 0, r.angle(), r.heavy(), r.speed());
		String name = "reaction_" + Integer.toHexString(spec.hashCode());
		JsonObject json = ZombieAnimClient.playerReaction(name, spec);
		if (json == null) {
			return;
		}
		Animation animation = UniversalAnimLoader.loadAnimations(json).get(name);
		IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(player,
				r.kind() == PoisePayloads.HELD ? HELD_LAYER : KNOCKBACK_LAYER);
		if (animation == null || !(layer instanceof PlayerAnimationController controller)) {
			return;
		}
		Playing p;
		if (r.kind() == PoisePayloads.HELD) {
			JsonObject overlay = Poise.reaction1().getAsJsonObject("overlay");
			// Event 80 at 1.0 s of the 1.3333 s clip, scaled to the clip's play speed.
			p = new Playing(r.kind(), r.speed(), STATE_CONTROL / r.speed());
			p.alphas = new java.util.HashMap<>();
			JsonObject weights = overlay.getAsJsonObject("boneWeights");
			OVERLAY_BONES.forEach((bone, ds2) -> p.alphas.put(bone,
					weights.has(ds2) ? weights.get(ds2).getAsFloat() : 0f));
			p.exitBlend = overlay.get("blendOut").getAsFloat();
		} else {
			p = new Playing(r.kind(), r.speed(), 0);
			// A reaction cuts the action and the dodge on this client too.
			if (player == Minecraft.getInstance().player) {
				dev.soulsmode.client.RollClient.cancel();
				lower = ReactionBlend.c0001Lower(1 + (r.thrust() ? 2 : 0) + (r.up() ? 1 : 0), r.angle(), r.heavy());
				lowerSpeed = r.speed();
				lowerYaw = Math.toRadians(r.yaw());
				lowerTicks = 0;
				lowerTime = 0;
				moveLockTicks = Math.round(r.moveLock() * 20);
			}
			SoulsAnimations.stop(player);
			GuardLayerClient.stopNow(player);
		}
		playing(r.kind()).put(player, p);
		if (System.getProperty("soulsmode.qa") != null) {
			SoulsMode.LOGGER.info("[BSE QA] reaction kind={} category={} angle={} heavy={} speed={} moveLock={}", r.kind(),
					r.category(), r.angle(), r.heavy(), r.speed(), r.moveLock());
		}
		controller.triggerAnimation(RawAnimation.begin().thenPlay(animation), 0);
	}

	private static float transition(float t, float d) {
		if (d <= 0 || t >= d) {
			return t >= 0 ? 1 : 0;
		}
		return t <= 0 ? 0 : (float) ((1 - Math.cos(Math.PI * t / d)) / 2);
	}

	/** The envelope of a reaction now (0 = gone); starts its exit when its time comes. */
	private static float envelope(AbstractClientPlayer player, Playing p) {
		long now = System.nanoTime();
		float t = (now - p.startNanos) / 1e9f;
		if (p.kind == PoisePayloads.HELD) {
			float in = transition(t, Poise.reaction1().getAsJsonObject("overlay").get("blendIn").getAsFloat());
			float out = 1 - transition(t - p.exitAt, p.exitBlend);
			if (out <= 0) {
				HELD.remove(player);
			}
			return in * Math.max(0, out);
		}
		if (p.exitNanos == 0) {
			boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 1e-4;
			if (moving && t >= STATE_CONTROL_1 / p.speed) {
				p.exitNanos = now;
				p.exitBlend = EXIT_MOVING;
			} else if (t >= STATE_CONTROL / p.speed) {
				p.exitNanos = now;
				p.exitBlend = EXIT_STILL;
			}
		}
		float in = transition(t, ENTER);
		float out = p.exitNanos == 0 ? 1 : 1 - transition((now - p.exitNanos) / 1e9f, p.exitBlend);
		if (out <= 0) {
			KNOCKBACK.remove(player);
		}
		return in * Math.max(0, out);
	}

	/** base + (reaction - base) x envelope (x the bone's alpha for the overlay; its pelvis and legs stay). */
	private static final class Mix extends AbstractModifier {
		private final AbstractClientPlayer player;
		private final int kind;

		Mix(AbstractClientPlayer player, int kind) {
			this.player = player;
			this.kind = kind;
		}

		@Override
		public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
			Playing p = playing(kind).get(player);
			if (p == null) {
				return bone;
			}
			float w = envelope(player, p);
			if (kind == PoisePayloads.HELD) {
				// Nothing reads DmgCategory 20 outside BT_Move: an attack, dodge or reaction hides it.
				if (SoulsAnimations.isPlaying(player) || KNOCKBACK.containsKey(player)) {
					return bone;
				}
				w *= p.alphas.getOrDefault(bone.getName(), 0f);
			}
			if (w <= 0) {
				return bone;
			}
			float[] base = {bone.rotX, bone.rotY, bone.rotZ, bone.positionX, bone.positionY, bone.positionZ};
			PlayerAnimBone clip = super.get3DTransform(bone);
			float[] c = {clip.rotX, clip.rotY, clip.rotZ, clip.positionX, clip.positionY, clip.positionZ};
			boolean pelvis = kind == PoisePayloads.HELD && bone.getName().equals("body");
			for (int i = 0; i < 6; i++) {
				if (pelvis && i >= 3) {
					c[i] = base[i];
				}
				c[i] = base[i] + (c[i] - base[i]) * w;
			}
			clip.rotX = c[0];
			clip.rotY = c[1];
			clip.rotZ = c[2];
			clip.positionX = c[3];
			clip.positionY = c[4];
			clip.positionZ = c[5];
			return clip;
		}
	}

	/** The local player: the reaction's root motion (ReactionBlend, the facing it was hit with) and the walking lock. */
	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (moveLockTicks > 0) {
			moveLockTicks--;
		}
		if (player == null || lower == null) {
			return;
		}
		lowerTicks++;
		float t = Math.min(ReactionBlend.C0001_LENGTH, lowerTicks / 20f * lowerSpeed);
		float[] step = ReactionBlend.delta(lower, lowerTime, t);
		lowerTime = t;
		if (step[0] != 0 || step[1] != 0) {
			// Left of a facing (-sin, cos) is (cos, sin); back is (sin, -cos).
			double lx = Math.cos(lowerYaw);
			double lz = Math.sin(lowerYaw);
			double bx = Math.sin(lowerYaw);
			double bz = -Math.cos(lowerYaw);
			player.move(MoverType.SELF, new Vec3(step[0] * lx + step[1] * bx, 0, step[0] * lz + step[1] * bz));
		}
		if (t >= ReactionBlend.C0001_LENGTH) {
			lower = null;
		}
	}

	/** Kind of the newest reaction playing on the player (0 none), for QA. */
	public static int playingKind(AbstractClientPlayer player) {
		return KNOCKBACK.containsKey(player) ? PoisePayloads.KNOCKBACK : HELD.containsKey(player) ? PoisePayloads.HELD : 0;
	}

	/** The reaction keeps the local player from walking (BT_Damage until its exit / CancelControl_Move). */
	public static boolean movementLocked() {
		return moveLockTicks > 0;
	}
}
