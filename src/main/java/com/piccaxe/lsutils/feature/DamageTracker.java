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
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Estimates other players' health from the damage you deal them (attack-damage × cooldown × crit,
 * minus the target's visible armor). The estimate is the <em>primary</em> health value shown; the
 * bar falls back to the server's reported health only when there's no estimate (you haven't hit
 * them, or it has regenerated/reset away). The estimate ticks back up out of combat and clears on
 * death, at which point the bar reverts to the server count.
 */
public final class DamageTracker {
	private static final int REGEN_INTERVAL_TICKS = 80;   // ~4s, vanilla-ish natural regen cadence
	private static final long REGEN_DELAY_MS = 5000L;     // start regenerating after this long out of combat

	private static final Map<UUID, Float> estimate = new HashMap<>();
	private static final Map<UUID, Long> lastHit = new HashMap<>();
	private static int sinceRegen = 0;

	private DamageTracker() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (world.isClient() && player == mc.player && entity instanceof PlayerEntity target && target != player) {
				Config cfg = ConfigManager.get();
				if (cfg.masterEnabled && cfg.healthBars && cfg.healthBarDamageEstimate) {
					onHit(player, target);
				}
			}
			return ActionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(DamageTracker::tick);
	}

	/** Damage-dealt estimate (the primary health value), or null if untracked. */
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
			return;
		}
		if (estimate.isEmpty()) {
			return;
		}

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
