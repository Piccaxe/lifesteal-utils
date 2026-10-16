package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Works out how much health other players really have, for servers that hide it.
 *
 * <p><b>Live detection:</b> a player is "live" once the server has sent us a real change in their
 * health (a new value strictly between 0 and max). Servers that hide health send a frozen max or 0,
 * which never passes that test. For live players the server value is the truth and no estimate is kept.
 *
 * <p><b>Estimate</b> (hidden-health servers only): when you swing at a player we compute the damage
 * (attack damage × cooldown × crit + Sharpness, then the target's armor, Resistance and Protection),
 * but only apply it once the server confirms the hit landed (their hurt animation restarts). Swings
 * that miss, get blocked or land in invulnerability frames no longer count.
 */
public final class DamageTracker {
	private static final int REGEN_INTERVAL_TICKS = 80;   // ~4s, vanilla natural regen cadence
	private static final int FIRE_INTERVAL_TICKS = 20;    // ~1s, fire damage cadence
	private static final long REGEN_DELAY_MS = 5000L;     // start regenerating after this long out of combat
	private static final int CONFIRM_WINDOW_TICKS = 20;   // how long a swing waits for the server's hurt confirmation

	private record Pending(float damage, boolean crit, int tick) {
	}

	private static final Map<UUID, Float> estimate = new HashMap<>();
	private static final Map<UUID, Long> lastHit = new HashMap<>();
	private static final Map<UUID, Pending> pending = new HashMap<>();
	private static final Map<UUID, Integer> prevHurtTime = new HashMap<>();
	private static final Map<UUID, Float> lastSeenHealth = new HashMap<>();
	private static final Set<UUID> live = new HashSet<>();
	private static ClientWorld lastWorld;
	private static int tick = 0;
	private static int sinceRegen = 0;
	private static int sinceFire = 0;

	private DamageTracker() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (world.isClient() && player == mc.player && entity instanceof PlayerEntity target && target != player) {
				Config cfg = ConfigManager.get();
				if (cfg.masterEnabled && ((cfg.healthBars && cfg.healthBarDamageEstimate) || cfg.fightSummary)) {
					// Damage is computed now (cooldown/crit state at swing time) but applied on confirmation.
					pending.put(target.getUuid(), new Pending(estimateDamage(player, target), isCrit(player), tick));
				}
			}
			return ActionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(DamageTracker::tick);
	}

	/** Damage-dealt estimate, or null if untracked. Only kept for players whose health the server hides. */
	public static Float estimate(UUID id) {
		return estimate.get(id);
	}

	/** True once the server has sent a real health change for this player, so getHealth() can be trusted. */
	public static boolean isLive(UUID id) {
		return live.contains(id);
	}

	private static void applyHit(PlayerEntity target, float dealt) {
		UUID id = target.getUuid();
		float max = Math.max(1.0F, target.getMaxHealth());
		float current = estimate.getOrDefault(id, max);
		// Absorption (yellow hearts) soaks damage before real health; it's shown live by the bar.
		float absorbed = Math.min(dealt, Math.max(0.0F, target.getAbsorptionAmount()));
		estimate.put(id, MathHelper.clamp(current - (dealt - absorbed), 0.0F, max));
		lastHit.put(id, System.currentTimeMillis());
	}

	private static float estimateDamage(PlayerEntity attacker, PlayerEntity target) {
		float cooldown = attacker.getAttackCooldownProgress(0.5F);

		// Base melee damage. Strength/Weakness are ATTACK_DAMAGE modifiers, so they're already included.
		double dmg = attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) * (0.2 + cooldown * cooldown * 0.8);

		// Mace smash bonus (added after the cooldown scaling, before the crit multiplier — vanilla order):
		// 4/block for the first 3 blocks, 2/block up to 8, then 1/block; Density adds 0.5/level per block.
		ItemStack weapon = attacker.getMainHandStack();
		if (weapon.isOf(Items.MACE) && attacker.fallDistance > 1.5 && !attacker.isGliding()) {
			double fall = attacker.fallDistance;
			double bonus = fall <= 3.0 ? 4.0 * fall : fall <= 8.0 ? 12.0 + 2.0 * (fall - 3.0) : 22.0 + fall - 8.0;
			bonus += 0.5 * enchantLevel(weapon, Enchantments.DENSITY) * fall;
			dmg += bonus;
		}

		// Crit (charged jump-attack) multiplies by 1.5 — before enchant damage is added.
		if (isCrit(attacker)) {
			dmg *= 1.5;
		}

		// Sharpness: +(0.5*level + 0.5), scaled linearly by the cooldown (vanilla).
		int sharp = enchantLevel(attacker.getMainHandStack(), Enchantments.SHARPNESS);
		if (sharp > 0) {
			dmg += (0.5 * sharp + 0.5) * cooldown;
		}

		// Target armor (vanilla DamageUtil.getDamageLeft; armor + toughness are synced).
		float armor = target.getArmor();
		double toughness = target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
		float f = 2.0F + (float) toughness / 4.0F;
		float g = MathHelper.clamp(armor - (float) dmg / f, armor * 0.2F, 20.0F);
		// Breach (mace) cuts armor effectiveness by 15% per level.
		float armorFrac = g / 25.0F - 0.15F * enchantLevel(weapon, Enchantments.BREACH);
		dmg *= 1.0 - MathHelper.clamp(armorFrac, 0.0F, 1.0F);

		// Resistance: -20% per level, if the server reports the effect to us.
		StatusEffectInstance resist = target.getStatusEffect(StatusEffects.RESISTANCE);
		if (resist != null) {
			dmg *= Math.max(0.0, 1.0 - 0.2 * (resist.getAmplifier() + 1));
		}

		// Protection enchant: 1 point per level across all armor, capped at 20 (= -80%), 4% per point.
		// Equipment enchantments are synced (that's how the glint shows), so this is readable.
		int epf = 0;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			epf += enchantLevel(target.getEquippedStack(slot), Enchantments.PROTECTION);
		}
		dmg *= 1.0 - Math.min(epf, 20) / 25.0;

		return (float) Math.max(0.0, dmg);
	}

	private static boolean isCrit(PlayerEntity attacker) {
		return attacker.getAttackCooldownProgress(0.5F) > 0.9F && attacker.fallDistance > 0.0F && !attacker.isOnGround()
			&& !attacker.isClimbing() && !attacker.isTouchingWater()
			&& attacker.getVehicle() == null && !attacker.isSprinting();
	}

	private static int enchantLevel(ItemStack stack, RegistryKey<Enchantment> key) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
			if (entry.getKey().matchesKey(key)) {
				return entry.getIntValue();
			}
		}
		return 0;
	}

	private static void clearAll() {
		estimate.clear();
		lastHit.clear();
		pending.clear();
		prevHurtTime.clear();
		lastSeenHealth.clear();
		live.clear();
	}

	private static void tick(MinecraftClient mc) {
		tick++;
		if (mc.world != lastWorld) {
			lastWorld = mc.world;
			clearAll();
		}
		if (mc.world == null) {
			return;
		}

		for (PlayerEntity p : mc.world.getPlayers()) {
			if (p == mc.player) {
				continue;
			}
			UUID id = p.getUuid();
			float max = Math.max(1.0F, p.getMaxHealth());

			// Live detection: any real change to a mid-range value means the server is syncing health.
			float h = p.getHealth();
			Float prev = lastSeenHealth.put(id, h);
			if (prev != null && prev != h && h > 0.0F && h < max && !Float.isNaN(h)) {
				live.add(id);
			}

			// Hit confirmation: hurtTime jumps back up when the server says this player took damage.
			int ht = p.hurtTime;
			boolean hurtNow = ht > prevHurtTime.getOrDefault(id, 0);
			prevHurtTime.put(id, ht);
			Pending pd = pending.get(id);
			if (pd != null) {
				if (hurtNow) {
					pending.remove(id);
					FightSummary.onHit(p, pd.damage(), pd.crit());
					if (!live.contains(id)) {
						applyHit(p, pd.damage());
					}
				} else if (tick - pd.tick() > CONFIRM_WINDOW_TICKS) {
					pending.remove(id); // missed, blocked by a shield, or swallowed by invulnerability frames
				}
			}
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
			// Real data now available, or they died: the estimate is no longer needed.
			if (live.contains(id) || (p != null && !p.isAlive())) {
				it.remove();
				lastHit.remove(id);
				continue;
			}

			float max = p != null ? Math.max(1.0F, p.getMaxHealth()) : 20.0F;
			float est = e.getValue();

			// Fire/lava between hits (≈1/s fire, ≈4/s lava).
			if (fireNow && p != null && p.isOnFire() && !p.isTouchingWater()) {
				est -= p.isInLava() ? 4.0F : 1.0F;
			}
			// Natural regen out of combat.
			if (regenNow && now - lastHit.getOrDefault(id, 0L) > REGEN_DELAY_MS) {
				est += 1.0F;
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
