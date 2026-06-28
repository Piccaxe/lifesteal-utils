package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/**
 * Auto-swaps armor sets based on your health, matching pieces by their lore (or name).
 *
 * <p>When health drops to/below {@code armorSwapLowHp} it equips the "defense" set; once you recover
 * to/above {@code armorSwapHighHp} it swaps back to the "normal" set. Each set is described by a
 * lore keyword (matches any piece) plus optional per-slot keyword overrides. Pieces are found in your
 * inventory and moved onto your body via normal slot clicks (like an auto-totem), so it only runs
 * when no other screen is open and your cursor is empty.
 */
public final class ArmorSwapper {
	private enum Active {NONE, DEFENSE, NORMAL}

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private static Active active = Active.NONE;

	private ArmorSwapper() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ArmorSwapper::tick);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.armorSwapper || mc.player == null || mc.world == null || mc.interactionManager == null) {
			return;
		}
		// Only act with no GUI open and an empty cursor, to avoid disturbing chests/trades.
		if (mc.currentScreen != null || !mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
			return;
		}

		float hp = mc.player.getHealth();
		if (hp <= cfg.armorSwapLowHp && active != Active.DEFENSE) {
			report(mc, cfg, "defense", equipSet(mc, cfg.armorDefenseSet));
			active = Active.DEFENSE;
		} else if (hp >= cfg.armorSwapHighHp && active == Active.DEFENSE) {
			report(mc, cfg, "normal", equipSet(mc, cfg.armorNormalSet));
			active = Active.NORMAL;
		}
	}

	/** Equips a set; returns {configuredSlots, swapped, missing}. */
	private static int[] equipSet(MinecraftClient mc, Config.ArmorSet set) {
		int configured = 0;
		int swapped = 0;
		int missing = 0;
		for (EquipmentSlot slot : ARMOR) {
			String keyword = keywordFor(set, slot);
			if (keyword.isEmpty()) {
				continue;
			}
			configured++;
			int r = equipSlot(mc, slot, keyword);
			if (r > 0) {
				swapped++;
			} else if (r < 0) {
				missing++;
			}
		}
		return new int[]{configured, swapped, missing};
	}

	/** @return 1 = swapped in, 0 = already wearing a match, -1 = no matching piece in inventory. */
	private static int equipSlot(MinecraftClient mc, EquipmentSlot slot, String keyword) {
		ScreenHandler handler = mc.player.playerScreenHandler;
		int armorIndex = armorSlotIndex(slot);

		// Already wearing a matching piece? Nothing to do.
		if (matches(handler.getSlot(armorIndex).getStack(), keyword)) {
			return 0;
		}

		// Find a matching piece for this slot anywhere in the main inventory + hotbar (handler 9..44).
		int source = -1;
		for (int i = 9; i <= 44; i++) {
			ItemStack stack = handler.getSlot(i).getStack();
			if (!stack.isEmpty() && isForSlot(stack, slot) && matches(stack, keyword)) {
				source = i;
				break;
			}
		}
		if (source < 0) {
			return -1;
		}

		// Pickup-based swap: new -> cursor, swap into armor (cursor = old), old -> source.
		int syncId = handler.syncId;
		mc.interactionManager.clickSlot(syncId, source, 0, SlotActionType.PICKUP, mc.player);
		mc.interactionManager.clickSlot(syncId, armorIndex, 0, SlotActionType.PICKUP, mc.player);
		mc.interactionManager.clickSlot(syncId, source, 0, SlotActionType.PICKUP, mc.player);
		return 1;
	}

	private static void report(MinecraftClient mc, Config cfg, String setName, int[] res) {
		if (!cfg.armorSwapFeedback || mc.player == null) {
			return;
		}
		int configured = res[0];
		int swapped = res[1];
		int missing = res[2];
		if (configured == 0) {
			send(mc, "Armor-swap: \"" + setName + "\" set has no items configured (set a keyword).", Formatting.RED);
		} else if (swapped > 0) {
			send(mc, "Armor-swap → " + setName + " (" + swapped + " piece" + (swapped == 1 ? "" : "s") + ")", Formatting.AQUA);
		} else if (missing > 0) {
			send(mc, "Armor-swap: couldn't find " + missing + " \"" + setName + "\" piece(s) in your inventory.", Formatting.YELLOW);
		}
		// swapped == 0 && missing == 0 → already wearing the set; stay quiet.
	}

	private static void send(MinecraftClient mc, String text, Formatting color) {
		mc.player.sendMessage(Text.literal(text).formatted(color), false);
	}

	private static String keywordFor(Config.ArmorSet set, EquipmentSlot slot) {
		String per = switch (slot) {
			case HEAD -> set.helmet;
			case CHEST -> set.chest;
			case LEGS -> set.legs;
			case FEET -> set.boots;
			default -> "";
		};
		if (per != null && !per.isBlank()) {
			return per.trim();
		}
		return set.keyword == null ? "" : set.keyword.trim();
	}

	private static boolean isForSlot(ItemStack stack, EquipmentSlot slot) {
		EquippableComponent eq = stack.get(DataComponentTypes.EQUIPPABLE);
		return eq != null && eq.slot() == slot;
	}

	private static boolean matches(ItemStack stack, String keyword) {
		if (stack.isEmpty() || keyword.isEmpty()) {
			return false;
		}
		String kw = keyword.toLowerCase(Locale.ROOT);
		if (stack.getName().getString().toLowerCase(Locale.ROOT).contains(kw)) {
			return true;
		}
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore != null) {
			for (var line : lore.lines()) {
				if (line.getString().toLowerCase(Locale.ROOT).contains(kw)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Player screen handler armor slots: HEAD=5, CHEST=6, LEGS=7, FEET=8. */
	private static int armorSlotIndex(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> 5;
			case CHEST -> 6;
			case LEGS -> 7;
			default -> 8;
		};
	}
}
