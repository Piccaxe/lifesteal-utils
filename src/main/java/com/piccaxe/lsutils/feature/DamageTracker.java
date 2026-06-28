package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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
	private static final int FIRE_INTERVAL_TICKS = 20;    // ~1s, fire damage cadence
	private static final long REGEN_DELAY_MS = 5000L;     // start regenerating after this long out of combat

	private static final Map<UUID, Float> estimate = new HashMap<>();
	private static final Map<UUID, Long> lastHit = new HashMap<>();
	private static int sinceRegen = 0;
	private static int sinceFire = 0;

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
		// Absorption (yellow hearts) soaks damage before real health. Read the live value and only
		// take what gets through it off the health estimate; absorption itself is shown live by the bar.
		float absorbed = Math.min(dealt, Math.max(0.0F, target.getAbsorptionAmount()));
		float toHealth = dealt - absorbed;
		estimate.put(id, MathHelper.clamp(current - toHealth, 0.0F, max));
		lastHit.put(id, System.currentTimeMillis());
	}

	private static float estimateDamage(PlayerEntity attacker, PlayerEntity target) {
		float cooldown = attacker.getAttackCooldownProgress(0.5F);

		// Base melee damage from the attribute. Strength/Weakness are applied as ATTACK_DAMAGE
		// attribute modifiers, so they're ALREADY included here.
		double base = attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
		double dmg = base * (0.2 + cooldown * cooldown * 0.8);

		// Crit (charged jump-attack) multiplies the base by 1.5 — before enchant damage is added.
		boolean crit = cooldown > 0.9F && attacker.fallDistance > 0.0F && !attacker.isOnGround()
			&& !attacker.isClimbing() && !attacker.isTouchingWater()
			&& attacker.getVehicle() == null && !attacker.isSprinting();
		if (crit) {
			dmg *= 1.5;
		}

		// Sharpness: +(0.5*level + 0.5), added after and scaled linearly by the cooldown (vanilla).
		int sharp = sharpnessLevel(attacker.getMainHandStack());
		if (sharp > 0) {
			dmg += (0.5 * sharp + 0.5) * cooldown;
		}

		// Target armor reduction (vanilla formula; armor + toughness are synced).
		float armor = target.getArmor();
		double toughness = target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
		float f = 2.0F + (float) toughness / 4.0F;
		float g = MathHelper.clamp(armor - (float) dmg / f, armor * 0.2F, 20.0F);
		dmg = dmg * (1.0 - g / 25.0);

		// Resistance on the target (-20% per level), if the server reports the effect to us.
		StatusEffectInstance resist = target.getStatusEffect(StatusEffects.RESISTANCE);
		if (resist != null) {
			dmg *= Math.max(0.0, 1.0 - 0.2 * (resist.getAmplifier() + 1));
		}

		return (float) Math.max(0.0, dmg);
	}

	private static int sharpnessLevel(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
			if (entry.getKey().matchesKey(Enchantments.SHARPNESS)) {
				return entry.getIntValue();
			}
		}
		return 0;
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
		boolean fireNow = ++sinceFire >= FIRE_INTERVAL_TICKS;
		if (fireNow) {
			sinceFire = 0;
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

			float max = p != null ? Math.max(1.0F, p.getMaxHealth()) : 20.0F;
			float est = e.getValue();

			// Predict fire/lava damage between server updates (≈1/s fire, ≈4/s lava) so burning enemies
			// drop responsively instead of looking healthier than they are.
			if (fireNow && p != null && p.isOnFire() && !p.isTouchingWater()) {
				est -= p.isInLava() ? 4.0F : 1.0F;
			}

			// Natural regen out of combat (our own model, used when the server isn't reporting a live value).
			if (regenNow && now - lastHit.getOrDefault(id, 0L) > REGEN_DELAY_MS) {
				est += 1.0F;
			}

			// Reconcile with the server's real health — this is what captures everything we don't model
			// (fall damage, other attackers, poison/wither, saturation regen, etc.). We trust any mid-range
			// value: snap DOWN if they're lower than we think, snap UP if they've healed. We ignore a
			// reported full/zero value, since some servers send a frozen max (or 0) for other players.
			if (p != null) {
				float real = p.getHealth();
				if (real > 0.0F && real < est) {
					est = real;
				} else if (real > est && real < max) {
					est = real;
				}
			}

			est = MathHelper.clamp(est, 0.0F, max);
			if (est >= max) {
				it.remove();
				lastHit.remove(id);
			} else {
				e.setValue(est);
			}
		}
	}
}
