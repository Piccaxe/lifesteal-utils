package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

/**
 * Warns when equipped armor or a hotbar pickaxe drops below a durability threshold (percent).
 * Fires once per downward crossing per slot (repairing/replacing re-arms it), via chat + a sound.
 */
public final class DurabilityNotifier {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Last seen remaining-durability percent per slot key, to detect the downward crossing. */
	private static final Map<String, Integer> lastPct = new HashMap<>();
	private static int sinceCheck = 0;

	private DurabilityNotifier() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DurabilityNotifier::tick);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.durabilityWarn || mc.player == null) {
			if (!lastPct.isEmpty()) {
				lastPct.clear();
			}
			return;
		}
		if (++sinceCheck < 10) { // twice a second is plenty
			return;
		}
		sinceCheck = 0;

		int threshold = Math.max(1, Math.min(99, cfg.durabilityThreshold));
		for (EquipmentSlot slot : ARMOR) {
			check(mc, cfg, "armor:" + slot.getName(), mc.player.getEquippedStack(slot), threshold);
		}
		for (int i = 0; i < 9; i++) {
			ItemStack st = mc.player.getInventory().getStack(i);
			if (!st.isEmpty() && isPickaxe(st)) {
				check(mc, cfg, "hotbar:" + i, st, threshold);
			} else {
				lastPct.remove("hotbar:" + i);
			}
		}
	}

	private static void check(MinecraftClient mc, Config cfg, String key, ItemStack stack, int threshold) {
		if (stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) {
			lastPct.remove(key);
			return;
		}
		int pct = (int) Math.round(100.0 * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage());
		Integer prev = lastPct.put(key, pct);
		if (pct <= threshold && (prev == null || prev > threshold)) {
			String name = stack.getName().getString();
			mc.player.sendMessage(Text.literal("⚠ " + name + " durability " + pct + "%").formatted(Formatting.GOLD), false);
			mc.player.sendMessage(Text.literal("⚠ " + name + " " + pct + "%").formatted(Formatting.GOLD), true);
			if (cfg.durabilityWarnSound) {
				mc.player.playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.5F, 1.4F);
			}
		}
	}

	private static boolean isPickaxe(ItemStack stack) {
		return Registries.ITEM.getId(stack.getItem()).getPath().contains("pickaxe");
	}
}
