package dev.soulsmode.client.anim;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerRawAnimationBuilder;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.ds2.Moveset;
import dev.soulsmode.network.AttackPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Two-handing on the client (docs/research/weapons.md, "Agarre a dos manos: cambio y postura"): each player's grip,
 * the grip change animation and the two-handed stance.
 * <p>
 * The change animation (a00_08_00x0) is an upper-body FeatherBlend2 over locomotion in DS2, with per-bone alphas on the
 * DS2 skeleton. Minecraft limbs are single bones, so each one takes the alpha of the DS2 bone the retarget builds it
 * from (tools/re/ds2_anims.py): body = Spine1, head = Head, arm = UpperArm, held item = Weapon, body position =
 * Pelvis. Taking one bone's alpha per limb is a retarget simplification.
 */
public final class GripClient {
	public static final ResourceLocation LAYER = SoulsMode.id("grip_change");
	public static final ResourceLocation MIRRORED_LAYER = SoulsMode.id("grip_change_mirrored");
	/** Above locomotion (5), below gameplay actions (1500): an attack replaces the change animation. */
	public static final int PRIORITY = 20;

	private static final Map<Integer, Moveset.Grip> GRIPS = new HashMap<>();
	/** Each change layer's speed modifier (WeaponChangeSpeed). */
	private static final Map<PlayerAnimationController, SpeedModifier> SPEEDS = new java.util.WeakHashMap<>();

	private GripClient() {
	}

	public static void init() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> controller(player, false));
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(MIRRORED_LAYER, PRIORITY, player -> controller(player, true));
		ClientPlayNetworking.registerGlobalReceiver(AttackPayloads.GripState.TYPE, (payload, context) -> {
			Moveset.Grip grip = Moveset.Grip.values()[payload.grip()];
			Moveset.Grip before = GRIPS.getOrDefault(payload.entityId(), Moveset.Grip.ONE_HANDED);
			if (context.client().level != null
					&& context.client().level.getEntity(payload.entityId()) instanceof AbstractClientPlayer player) {
				PowerStanceClient.onGrip(player, before, grip);
			}
			if (grip == Moveset.Grip.ONE_HANDED) {
				GRIPS.remove(payload.entityId());
			} else {
				GRIPS.put(payload.entityId(), grip);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AttackPayloads.GripChange.TYPE, (payload, context) -> {
			Entity entity = context.client().level == null ? null : context.client().level.getEntity(payload.entityId());
			if (entity instanceof AbstractClientPlayer player) {
				IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(player,
						payload.mirrored() ? MIRRORED_LAYER : LAYER);
				if (layer instanceof PlayerAnimationController controller) {
					SpeedModifier speed = SPEEDS.get(controller);
					if (speed != null) {
						// WeaponChangeSpeed (Agility, agility.md s.3).
						speed.speed = payload.speed();
					}
					controller.triggerAnimation(PlayerRawAnimationBuilder.begin()
							.thenPlay(SoulsMode.id("ds2_weapon_change_" + payload.storage())).build());
				}
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GRIPS.clear());
	}

	public static Moveset.Grip grip(Entity entity) {
		return GRIPS.getOrDefault(entity.getId(), Moveset.Grip.ONE_HANDED);
	}

	/**
	 * Suffix of the locomotion clips with the stance of the weapon held in both hands, exported by
	 * tools/re/ds2_anims.py: {@code _2h_<stance>} for the right weapon, {@code _2h_<stance>_l} for the left one (the
	 * same stance through Morpheme's mirror node, docs/research/animations.md "Espejo de Morpheme"); "" otherwise.
	 */
	static String stanceSuffix(AbstractClientPlayer player) {
		Moveset.Grip grip = grip(player);
		if (grip == Moveset.Grip.ONE_HANDED) {
			return "";
		}
		if (grip.powerStance() && PowerStanceClient.entering(player)) {
			// SM 0x442 until its hold state: rebuilt over one-handed locomotion (PowerStanceClient).
			return "";
		}
		if (grip.powerStance()) {
			// powerstance.md s.4: state 6 = the right weapon's WTP+0x1c stance, state 5 = the left one's +0x20 through
			// the mirror node; the hold phase is baked into the locomotion (_2w_<stance>[_l], tools/re/ds2_anims.py).
			boolean left = grip == Moveset.Grip.POWER_STANCE_LEFT;
			var data = dev.soulsmode.ds2.PowerStance.data(dev.soulsmode.SoulsAttack.damageKey(
					SoulsWeapons.movesetFor(left ? player.getOffhandItem() : player.getMainHandItem(), left)));
			if (data == null) {
				return "";
			}
			int stance = left ? data.stanceLeft() : data.stanceRight();
			// Right after a weapon change the grip is still the power stance until the server's GripState arrives, and
			// the newly held item may have a stance with no exported _2w_ clips (fists 204610: crash 2026-11-03).
			var clips = dev.soulsmode.ds2.Weapons.powerStanceClips();
			if (clips == null || !clips.has(String.valueOf(stance))) {
				return "";
			}
			return "_2w_" + stance + (left ? "_l" : "");
		}
		boolean left = grip == Moveset.Grip.TWO_HANDED_LEFT;
		return SoulsWeapons.grip(SoulsWeapons.movesetFor(left ? player.getOffhandItem() : player.getMainHandItem(), left))
				.map(g -> "_2h_" + g.twoHandStance() + (left ? "_l" : "")).orElse("");
	}

	private static PlayerAnimationController controller(AbstractClientPlayer player, boolean mirrored) {
		PlayerAnimationController controller = new PlayerAnimationController(player,
				(c, state, animSetter) -> PlayState.STOP);
		controller.addModifierLast(new UpperBody(mirrored));
		SpeedModifier speed = new SpeedModifier(1);
		controller.addModifierLast(speed);
		SPEEDS.put(controller, speed);
		return controller;
	}

	/**
	 * Applies the change animation per bone with the FeatherBlend2 alphas; {@code mirrored} is SM_ChangeWeapon_R (the
	 * right weapon put away), whose alphas are the left version's mirrored except Spine1 = 1.
	 */
	private static final class UpperBody extends AbstractModifier {
		private static final Map<String, String> MIRROR = Map.of("left_arm", "right_arm", "right_arm", "left_arm",
				"left_leg", "right_leg", "right_leg", "left_leg", "left_item", "right_item", "right_item", "left_item");
		/** SM_ChangeWeapon_L: Spine1 0.75, Head 1, L_UpperArm 1, L_Weapon 1, R_UpperArm 0.2, the rest 0. */
		private static final Map<String, Float> LEFT = Map.of("body", 0.75f, "head", 1f, "left_arm", 1f,
				"left_item", 1f, "right_arm", 0.2f);
		/** SM_ChangeWeapon_R: Spine1 1, Head 1, R_UpperArm 1, R_Weapon 1, L_UpperArm 0.2. */
		private static final Map<String, Float> RIGHT = Map.of("body", 1f, "head", 1f, "right_arm", 1f,
				"right_item", 1f, "left_arm", 0.2f);

		private final boolean mirrored;

		UpperBody(boolean mirrored) {
			this.mirrored = mirrored;
		}

		@Override
		public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
			String name = bone.getName();
			float w = (mirrored ? RIGHT : LEFT).getOrDefault(name, 0f);
			if (w <= 0) {
				return bone;
			}
			PlayerAnimBone animated;
			if (mirrored) {
				PlayerAnimBone source = new PlayerAnimBone(MIRROR.getOrDefault(name, name)).copyOtherBone(bone);
				flip(source);
				animated = super.get3DTransform(source);
				flip(animated);
			} else {
				animated = super.get3DTransform(new PlayerAnimBone(name).copyOtherBone(bone));
			}
			bone.rotX += (animated.rotX - bone.rotX) * w;
			bone.rotY += (animated.rotY - bone.rotY) * w;
			bone.rotZ += (animated.rotZ - bone.rotZ) * w;
			// Body position is the pelvis, alpha 0; limbs and items keep theirs.
			if (!name.equals("body")) {
				bone.positionX += (animated.positionX - bone.positionX) * w;
				bone.positionY += (animated.positionY - bone.positionY) * w;
				bone.positionZ += (animated.positionZ - bone.positionZ) * w;
			}
			return bone;
		}

		/** PAL's MirrorModifier transform. */
		private static void flip(PlayerAnimBone bone) {
			bone.positionX = -bone.positionX;
			bone.rotY = -bone.rotY;
			bone.rotZ = -bone.rotZ;
			bone.bend = -bone.bend;
		}
	}
}
