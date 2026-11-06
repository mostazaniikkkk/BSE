package dev.soulsmode;

import dev.soulsmode.content.SoulsContent;
import dev.soulsmode.ds2.Attributes;
import dev.soulsmode.ds2.Bullets;
import dev.soulsmode.ds2.Spells;
import dev.soulsmode.entity.SoulsBullet;
import dev.soulsmode.entity.SoulsEnemy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.RegisteredFunction;
import org.squiddev.cobalt.interrupt.InterruptAction;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.squiddev.cobalt.ValueFactory.listOf;
import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

/**
 * Lua scripts of datapack spells (docs/datapacks.md, "Scripts"): {@code data/<ns>/soulsmode/script/<name>.lua}, named
 * by a spell's {@code "script"}. Server only. Each script has its own Lua state (Cobalt) with the base, string, table,
 * math and coroutine libraries and nothing that reaches files, the OS or Java; every call has a time budget.
 * <p>
 * Hooks (global functions, all optional): {@code on_cast()} at each bullet event of the cast animation (after the
 * spell's own bullet, if it has one), {@code on_hit(target, bullet)} when one of the spell's bullets hits a character,
 * {@code on_expire(bullet, x, y, z)} when one ends. The {@code bse} table is the whole API: the DS2 engine's pieces
 * (the spell's bullets and SpEffects), never Minecraft directly.
 */
public final class SoulsScripts {
	/** Longest a hook may run before it is stopped (a script stuck in a loop must not freeze the server). */
	private static final long BUDGET_MS = 50;
	private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "BSE Lua watchdog");
		t.setDaemon(true);
		return t;
	});

	/**
	 * What a hook acts on: the caster, the spell and the cast's attack values; or, for an enemy's hooks, the enemy
	 * (caster null).
	 */
	private record Call(ServerPlayer caster, ResourceLocation spell, float[] attack, String catalystKey, float homingRange,
			SoulsEnemy enemy) {
		Call(ServerPlayer caster, ResourceLocation spell, float[] attack, String catalystKey, float homingRange) {
			this(caster, spell, attack, catalystKey, homingRange, null);
		}

		net.minecraft.server.level.ServerLevel level() {
			return caster != null ? caster.serverLevel() : (net.minecraft.server.level.ServerLevel) enemy.level();
		}
	}

	private static final class Script {
		final LuaState state;
		volatile long deadline;

		Script(LuaState state) {
			this.state = state;
		}
	}

	private static final Map<ResourceLocation, Script> SCRIPTS = new HashMap<>();
	private static Call current;

	private SoulsScripts() {
	}

	/** Compiles every script; a bad one is logged and skipped. Called on each datapack (re)load, server side. */
	public static synchronized void load(Map<ResourceLocation, String> sources) {
		SCRIPTS.clear();
		sources.forEach((id, source) -> {
			try {
				SCRIPTS.put(id, compile(id, source));
			} catch (Exception e) {
				SoulsMode.LOGGER.error("[BSE] skipped script {}: {}", id, e.toString());
			}
		});
		SoulsMode.LOGGER.info("[BSE] datapack scripts: {}", SCRIPTS.size());
	}

	/** Whether a script compiled and its top level ran (tests, the reload log). */
	static synchronized boolean loaded(ResourceLocation id) {
		return SCRIPTS.containsKey(id);
	}

	/** Runs a global function of a loaded script with no hook context and returns its first result as a string (tests). */
	static synchronized String callForTest(ResourceLocation id, String name) throws LuaError {
		Script script = SCRIPTS.get(id);
		return run(script, (LuaFunction) script.state.globals().rawget(name), Constants.NONE).first().toString();
	}

	private static Script compile(ResourceLocation id, String source) throws Exception {
		Script[] holder = new Script[1];
		LuaState state = LuaState.builder().interruptHandler(() -> {
			if (holder[0] != null && System.currentTimeMillis() > holder[0].deadline) {
				throw new LuaError("script ran longer than " + BUDGET_MS + " ms");
			}
			return InterruptAction.CONTINUE;
		}).build();
		Script script = new Script(state);
		holder[0] = script;
		CoreLibraries.standardGlobals(state);
		LuaTable g = state.globals();
		// Sandbox: no loading code at run time, no files.
		for (String name : List.of("load", "loadstring", "dofile", "loadfile", "require", "collectgarbage", "module",
				"debug", "io", "os", "package")) {
			g.rawset(name, Constants.NIL);
		}
		g.rawset("print", RegisteredFunction.ofV("print", (s, args) -> {
			StringBuilder b = new StringBuilder();
			for (int i = 1; i <= args.count(); i++) {
				b.append(i > 1 ? "\t" : "").append(args.arg(i).toString());
			}
			SoulsMode.LOGGER.info("[BSE] script {}: {}", id, b);
			return Constants.NONE;
		}).create());
		g.rawset("bse", api());
		LuaFunction main = LoadState.load(state, new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
				"@" + id, g);
		run(script, main, Constants.NONE);
		return script;
	}

	private static Varargs run(Script script, LuaFunction fn, Varargs args) throws LuaError {
		script.deadline = System.currentTimeMillis() + BUDGET_MS;
		// Raised again every 5 ms past the budget: the handler only stops the script once the deadline has passed.
		ScheduledFuture<?> alarm = WATCHDOG.scheduleAtFixedRate(script.state::interrupt, BUDGET_MS, 5, TimeUnit.MILLISECONDS);
		try {
			return LuaThread.runMain(script.state, fn, args);
		} finally {
			alarm.cancel(false);
		}
	}

	/** Runs a hook; false if the script or the function does not exist. */
	private static boolean hook(ResourceLocation scriptId, Call call, String name, Varargs args) {
		Script script = scriptId == null ? null : SCRIPTS.get(scriptId);
		if (script == null) {
			return false;
		}
		LuaValue fn = script.state.globals().rawget(name);
		if (!(fn instanceof LuaFunction function)) {
			return false;
		}
		Call previous = current;
		current = call;
		try {
			run(script, function, args);
		} catch (LuaError e) {
			SoulsMode.LOGGER.error("[BSE] script {} {}: {}", scriptId, name, e.getMessage());
		} finally {
			current = previous;
		}
		return true;
	}

	/**
	 * A datapack enemy's upper logic (enemy_ai.md s.10: the ESD's place): {@code on_think(self)} while the enemy has a
	 * target and is not approaching or playing an attack. False if the enemy has no such hook (the declarative table
	 * decides then).
	 */
	public static boolean onThink(SoulsEnemy enemy, ResourceLocation script) {
		return hook(script, new Call(null, null, null, null, -1, enemy), "on_think", valueOf(enemy.getId()));
	}

	private static ResourceLocation scriptOf(ResourceLocation spell) {
		Spells.Spell s = SoulsContent.spell(spell);
		return s == null ? null : s.script();
	}

	public static void onCast(ServerPlayer caster, ResourceLocation spell, float[] attack, String catalystKey,
			float homingRange) {
		hook(scriptOf(spell), new Call(caster, spell, attack, catalystKey, homingRange), "on_cast", Constants.NONE);
	}

	public static void onHit(ServerPlayer caster, ResourceLocation spell, float[] attack, String catalystKey,
			Entity target, String bullet) {
		hook(scriptOf(spell), new Call(caster, spell, attack, catalystKey, -1), "on_hit",
				varargsOf(valueOf(target.getId()), valueOf(bullet)));
	}

	public static void onExpire(ServerPlayer caster, ResourceLocation spell, float[] attack, String catalystKey,
			String bullet, Vec3 pos) {
		hook(scriptOf(spell), new Call(caster, spell, attack, catalystKey, -1), "on_expire",
				varargsOf(valueOf(bullet), valueOf(pos.x), valueOf(pos.y), valueOf(pos.z)));
	}

	// The API.

	private static Call call() throws LuaError {
		if (current == null) {
			throw new LuaError("bse functions can only be used inside a hook");
		}
		return current;
	}

	/** The call of a spell hook. */
	private static Call spellCall() throws LuaError {
		Call c = call();
		if (c.caster() == null) {
			throw new LuaError("this bse function is only for spell hooks");
		}
		return c;
	}

	/** The enemy of an enemy hook. */
	private static SoulsEnemy enemy() throws LuaError {
		Call c = call();
		if (c.enemy() == null) {
			throw new LuaError("this bse function is only for enemy hooks");
		}
		return c.enemy();
	}

	private static LuaTable vec(LuaState s, double x, double y, double z) throws LuaError {
		LuaTable t = new LuaTable();
		t.rawset("x", valueOf(x));
		t.rawset("y", valueOf(y));
		t.rawset("z", valueOf(z));
		return t;
	}

	private static LuaTable entityTable(Entity e, ServerPlayer caster) {
		LuaTable t = new LuaTable();
		t.rawset("id", valueOf(e.getId()));
		t.rawset("x", valueOf(e.getX()));
		t.rawset("y", valueOf(e.getY()));
		t.rawset("z", valueOf(e.getZ()));
		t.rawset("yaw", valueOf(e.getYRot()));
		t.rawset("player", valueOf(e instanceof net.minecraft.world.entity.player.Player));
		t.rawset("caster", valueOf(e == caster));
		if (e instanceof LivingEntity l) {
			// DS2 HP, like the rest of the engine.
			t.rawset("hp", valueOf(l.getHealth() * SoulsStats.DS2_HP_PER_HEALTH));
			t.rawset("maxHp", valueOf(l.getMaxHealth() * SoulsStats.DS2_HP_PER_HEALTH));
		}
		return t;
	}

	private static Entity entity(int id) throws LuaError {
		Entity e = call().level().getEntity(id);
		if (e == null) {
			throw new LuaError("no entity " + id);
		}
		return e;
	}

	private static LuaTable api() {
		return RegisteredFunction.bind(new RegisteredFunction[] {
				// bse.caster() -> {id, x, y, z, yaw, hp, maxHp, ...}
				RegisteredFunction.of("caster", s -> entityTable(spellCall().caster(), spellCall().caster())),
				// bse.entity(id) -> the same table for any entity, or an error.
				RegisteredFunction.of("entity", (s, id) -> entityTable(entity(id.checkInteger()), call().caster())),
				// bse.target() -> the caster's lock-on target id, or the enemy's target id; nil if none.
				RegisteredFunction.of("target", s -> {
					Call c = call();
					Entity t = c.enemy() != null ? c.enemy().getTarget() : SoulsMagic.lockTarget(c.caster());
					return t == null ? Constants.NIL : valueOf(t.getId());
				}),
				// bse.stat(name) -> the caster's effective attribute ("vigor" ... "adaptability").
				RegisteredFunction.of("stat", (s, name) -> {
					int slot = Attributes.NAMES.indexOf(name.checkLuaString().toString());
					if (slot < 0) {
						throw new LuaError("unknown stat " + name);
					}
					return valueOf(SoulsPlayer.data(spellCall().caster()).map(d -> d.toAttributes().get(slot)).orElse(0));
				}),
				// bse.near(radius) -> ids of the living entities within radius of the caster (not the caster).
				RegisteredFunction.of("near", (s, radius) -> {
					Call call = call();
					Entity c = call.caster() != null ? call.caster() : call.enemy();
					double r = Math.min(32, radius.checkDouble());
					List<Entity> list = call.level().getEntities(c, c.getBoundingBox().inflate(r),
							e -> e instanceof LivingEntity l && l.isAlive() && e.distanceTo(c) <= r);
					LuaValue[] ids = new LuaValue[list.size()];
					for (int i = 0; i < ids.length; i++) {
						ids[i] = valueOf(list.get(i).getId());
					}
					return listOf(ids);
				}),
				// bse.emit(bullet [, x, y, z [, dx, dy, dz]]) -> emits one of the spell's bullets (its BulletParam
				// emitter: fan, shots, homing on the lock-on target) from the caster's chest, along its facing, or
				// from / along the given position / direction.
				RegisteredFunction.ofV("emit", (s, args) -> {
					Call c = spellCall();
					Spells.Spell spell = SoulsContent.spell(c.spell());
					String id = args.arg(1).checkLuaString().toString();
					Bullets.Def def = spell == null ? null : spell.bullets().get(id);
					if (def == null) {
						throw new LuaError("the spell has no bullet " + id);
					}
					ServerPlayer p = c.caster();
					double yaw = Math.toRadians(p.getYRot());
					Vec3 pos = args.count() >= 4 ? new Vec3(args.arg(2).checkDouble(), args.arg(3).checkDouble(),
							args.arg(4).checkDouble()) : p.position().add(0, p.getBbHeight() * 0.6, 0);
					Vec3 dir = args.count() >= 7 ? new Vec3(args.arg(5).checkDouble(), args.arg(6).checkDouble(),
							args.arg(7).checkDouble()) : new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
					Entity target = SoulsMagic.lockTarget(p);
					SoulsBullet.emit(p.serverLevel(), p, def, spell.bullets(), () -> new SoulsBullet.Frame(pos, dir),
							c.attack().clone(), c.catalystKey(), target, c.homingRange(), c.spell());
					return Constants.NONE;
				}),
				// bse.effect(id, spEffect) -> applies one of the spell's SpEffects to an entity.
				RegisteredFunction.of("effect", (s, id, effect) -> {
					Call c = spellCall();
					Spells.Spell spell = SoulsContent.spell(c.spell());
					if (spell == null || !spell.spEffects().containsKey(effect.checkInteger())) {
						throw new LuaError("the spell has no SpEffect " + effect);
					}
					if (!(entity(id.checkInteger()) instanceof LivingEntity victim)) {
						throw new LuaError("entity " + id + " is not living");
					}
					SoulsSpEffects.apply(c.caster(), victim, c.spell(), effect.checkInteger(), spell.bullets(),
							c.attack(), c.catalystKey(), 0, 0);
					return Constants.NIL;
				}),
				// Enemy hooks (enemy_ai.md s.5, s.10).
				// bse.self() -> the enemy's table.
				RegisteredFunction.of("self", s -> entityTable(enemy(), null)),
				// bse.distance() -> f7001: distance to the target in blocks, 9999 without one.
				RegisteredFunction.of("distance", s -> valueOf(enemy().targetDistance())),
				// bse.angle() -> f7002: absolute angle to the target in degrees.
				RegisteredFunction.of("angle", s -> valueOf(enemy().targetAngle())),
				// bse.height() -> f7007: the target's height minus the enemy's.
				RegisteredFunction.of("height", s -> valueOf(enemy().targetHeight())),
				// bse.attack(name) -> cmd 5204 through the attack subroutine (approach, turn, give up); false if
				// the enemy has no such attack or is playing one.
				RegisteredFunction.of("attack", (s, name) -> valueOf(enemy().requestAttack(name.checkLuaString().toString()))),
				// bse.busy() -> an attack is being approached or played (f5001).
				RegisteredFunction.of("busy", s -> valueOf(enemy().busy())),
				// bse.var(i) / bse.set_var(i, v) -> the enemy's logic variables 0..7 (f5900 / cmd 5300).
				RegisteredFunction.of("var", (s, i) -> valueOf(enemy().var(i.checkInteger()))),
				RegisteredFunction.of("set_var", (s, i, v) -> {
					enemy().setVar(i.checkInteger(), v.checkInteger());
					return Constants.NIL;
				}),
				// bse.time() -> seconds since the enemy appeared (for state times and timers, f3 / f2).
				RegisteredFunction.of("time", s -> valueOf(enemy().tickCount / 20.0)),
		});
	}
}
