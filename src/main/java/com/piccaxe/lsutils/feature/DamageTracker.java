package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Helps the health bars track other players' health.
 *
 * <p>Modern servers sync a player's real health through the entity tracker, in which case the bar
 * just uses {@code getHealth()} and automatically follows damage <em>and</em> healing from every
 * source. We confirm that per-player by watching whether their real health ever changes — if it
 * does, they're flagged "live" and we trust the real value.
 *
 * <p>For servers that <em>don't</em> report it, real health sits frozen, so we fall back to an
 * estimate built from the damage you personally deal (attack-damage × cooldown × crit, minus the
 * target's visible armor), with a slow natural-regen approximation and a reset on death.
 */
public final class DamageTracker {
	private static final int REGEN_INTERVAL_TICKS = 80;   // ~4s, vanilla-ish natural regen cadence
	private static final long REGEN_DELAY_MS = 5000L;     // start regenerating after this long out of combat

	private static final Map<UUID, Float> estimate = new HashMap<>();
	private static final Map<UUID, Long> lastHit = new HashMap<>();
	private static final Map<UUID, Float> lastReal = new HashMap<>();
	private static final Set<UUID> live = new HashSet<>();
	private static int sinceRegen = 0;

	private DamageTracker() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (world.isClient() && player == mc.player && entity instanceof PlayerEntity target && target != player) {
				Config cfg = ConfigManager.get();
				if (cfg.masterEnabled && cfg.healthBars && cfg.healthBarDamageEstimate && !live.contains(target.getUuid())) {
					onHit(player, target);
				}
			}
			return ActionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(DamageTracker::tick);
	}

	/** True once the server has been observed updating this player's real health (so trust getHealth()). */
	public static boolean isLive(UUID id) {
		return live.contains(id);
	}

	/** Down-only estimate from damage you've dealt, or null if untracked. */
	public static Float estimate(UUID id) {
		return estimate.get(id);
	}

	private static void onHit(PlayerEntity attacker, PlayerEntity target) {
		UUID id = target.getUuid();
		float max = Math.max(1.0F, target.getMaxHealth());
		float current = estimate.getOrDefault(id, max);
		float dealt = estimateDamage(attacker, target);
		estimate.put(id, MathHelper.clamp(current - dealt, 0.0F, max));
		lastHit.put(id, System.currentTimeMillis());
	}

	private static float estimateDamage(PlayerEntity attacker, PlayerEntity target) {
		double base = attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
		float cooldown = attacker.getAttackCooldownProgress(0.5F);
		double dmg = base * (0.2 + cooldown * cooldown * 0.8);

		boolean crit = cooldown > 0.9F && attacker.fallDistance > 0.0F && !attacker.isOnGround()
			&& !attacker.isClimbing() && !attacker.isTouchingWater()
			&& attacker.getVehicle() == null && !attacker.isSprinting();
		if (crit) {
			dmg *= 1.5;
		}

		float armor = target.getArmor();
		double toughness = target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
		float f = 2.0F + (float) toughness / 4.0F;
		float g = MathHelper.clamp(armor - (float) dmg / f, armor * 0.2F, 20.0F);
		dmg = dmg * (1.0 - g / 25.0);

		return (float) Math.max(0.0, dmg);
	}

	private static void tick(MinecraftClient mc) {
		if (mc.world == null) {
			estimate.clear();
			lastHit.clear();
			lastReal.clear();
			live.clear();
			return;
		}

		// Detect players whose real health the server is actively syncing (it changed since last tick).
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (p == mc.player) {
				continue;
			}
			UUID id = p.getUuid();
			float real = p.getHealth();
			Float prev = lastReal.get(id);
			if (prev != null && Math.abs(real - prev) > 0.001F) {
				live.add(id);
				estimate.remove(id); // real value is authoritative now
				lastHit.remove(id);
			}
			lastReal.put(id, real);
		}

		// Slow regen of the down-only estimate for non-live players; drop it on death/when full.
		boolean regenNow = ++sinceRegen >= REGEN_INTERVAL_TICKS;
		if (regenNow) {
			sinceRegen = 0;
		}
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<UUID, Float>> it = estimate.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Float> e = it.next();
			UUID id = e.getKey();
			PlayerEntity p = mc.world.getPlayerByUuid(id);
			if (p != null && !p.isAlive()) {
				it.remove();
				lastHit.remove(id);
				continue;
			}
			if (regenNow && now - lastHit.getOrDefault(id, 0L) > REGEN_DELAY_MS) {
				float max = p != null ? Math.max(1.0F, p.getMaxHealth()) : 20.0F;
				float next = Math.min(max, e.getValue() + 1.0F);
				if (next >= max) {
					it.remove();
					lastHit.remove(id);
				} else {
					e.setValue(next);
				}
			}
		}
	}
}
