package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects and undoes a "Trickster"-style hotbar scramble.
 *
 * <p>Key insight: you cannot rearrange your own hotbar without an inventory screen
 * open. So if — while no screen is open — the hotbar becomes a pure permutation of the
 * previous contents (same stacks, just reordered), it must have been scrambled by the
 * server. We then swap the stacks back into their prior order with {@link SlotActionType#SWAP}
 * clicks on the player's own screen handler (no GUI required).
 *
 * <p>Legitimate changes (picking up, using, dropping items) alter the item <i>set</i>,
 * not just the order, so they are accepted as the new baseline and never reverted.
 */
public final class AntiTrickster {
	private static final int HOTBAR_SIZE = 9;

	private static List<ItemStack> lastStable = null;
	private static List<ItemStack> prev = null;
	private static long lastNotify = 0L;

	private AntiTrickster() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AntiTrickster::tick);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.antiTrickster
			|| mc.player == null || mc.world == null || mc.interactionManager == null) {
			lastStable = null;
			prev = null;
			return;
		}

		List<ItemStack> current = snapshot(mc);
		boolean dbg = cfg.antiTricksterDebug;

		if (lastStable == null) {
			lastStable = current;
			prev = current;
			return;
		}

		if (sameOrder(current, lastStable)) {
			prev = current;
			return;
		}

		// Hotbar differs from the last good order. A pure reorder (no screen open) = a scramble.
		if (mc.currentScreen == null && isPermutation(current, lastStable)) {
			int moved = displacedCount(current, lastStable);
			int min = Math.max(2, cfg.antiTricksterMinItems);
			if (moved >= min) {
				if (dbg) {
					com.piccaxe.lsutils.PiccaxeLsUtils.LOGGER.info("[trickster] scramble detected ({} moved) -> restoring {} to {}",
						moved, describe(current), describe(lastStable));
				}
				restore(mc, current, lastStable);
				notifyRestored(mc, moved);
			} else if (dbg) {
				com.piccaxe.lsutils.PiccaxeLsUtils.LOGGER.info("[trickster] sub-threshold reorder ignored ({} moved < {})", moved, min);
			}
			// Keep lastStable as the known-good order either way: if we ignored a small reorder, we still
			// want to catch a later, larger scramble measured against the original layout.
			prev = current;
			return;
		}

		// Not a reorder of the known-good set (items added/removed, or a screen is open). Only adopt it
		// as the new baseline once it has held for a tick — a multi-tick scramble passes through broken
		// intermediate states that are NOT permutations, and we must not latch onto those.
		if (sameOrder(current, prev)) {
			if (dbg) {
				com.piccaxe.lsutils.PiccaxeLsUtils.LOGGER.info("[trickster] accepting new baseline: {}", describe(current));
			}
			lastStable = current;
		} else if (dbg) {
			com.piccaxe.lsutils.PiccaxeLsUtils.LOGGER.info("[trickster] changed (not a permutation, not yet stable): {}", describe(current));
		}
		prev = current;
	}

	private static String describe(List<ItemStack> stacks) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack s = stacks.get(i);
			sb.append(i).append(':').append(s.isEmpty() ? "-" : (s.getItem() + "x" + s.getCount()));
			if (i < stacks.size() - 1) {
				sb.append(' ');
			}
		}
		return sb.append(']').toString();
	}

	private static List<ItemStack> snapshot(MinecraftClient mc) {
		PlayerInventory inv = mc.player.getInventory();
		List<ItemStack> list = new ArrayList<>(HOTBAR_SIZE);
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			list.add(inv.getStack(i).copy());
		}
		return list;
	}

	/** Swaps the scrambled hotbar back into {@code target} order with at most 8 swaps. */
	private static void restore(MinecraftClient mc, List<ItemStack> current, List<ItemStack> target) {
		ScreenHandler handler = mc.player.currentScreenHandler;
		PlayerInventory inv = mc.player.getInventory();
		List<ItemStack> working = new ArrayList<>(current);

		for (int i = 0; i < HOTBAR_SIZE; i++) {
			if (equalStacks(working.get(i), target.get(i))) {
				continue;
			}
			int j = -1;
			for (int k = i + 1; k < HOTBAR_SIZE; k++) {
				if (equalStacks(working.get(k), target.get(i))) {
					j = k;
					break;
				}
			}
			if (j == -1) {
				continue; // shouldn't happen for a true permutation
			}

			int slotId = hotbarSlotId(handler, inv, i);
			if (slotId < 0) {
				continue;
			}
			// SWAP: exchange the stack in hotbar slot i with hotbar slot j (button = j).
			mc.interactionManager.clickSlot(handler.syncId, slotId, j, SlotActionType.SWAP, mc.player);

			ItemStack tmp = working.get(i);
			working.set(i, working.get(j));
			working.set(j, tmp);
		}
	}

	/** Resolves the screen-handler slot id backing hotbar position {@code hotbarIndex} (0-8). */
	private static int hotbarSlotId(ScreenHandler handler, PlayerInventory inv, int hotbarIndex) {
		for (Slot slot : handler.slots) {
			if (slot.inventory == inv && slot.getIndex() == hotbarIndex) {
				return slot.id;
			}
		}
		return -1;
	}

	private static boolean sameOrder(List<ItemStack> a, List<ItemStack> b) {
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			if (!equalStacks(a.get(i), b.get(i))) {
				return false;
			}
		}
		return true;
	}

	/** Number of hotbar positions whose stack differs between the two orders. */
	private static int displacedCount(List<ItemStack> a, List<ItemStack> b) {
		int n = 0;
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			if (!equalStacks(a.get(i), b.get(i))) {
				n++;
			}
		}
		return n;
	}

	private static boolean isPermutation(List<ItemStack> a, List<ItemStack> b) {
		List<ItemStack> pool = new ArrayList<>(b);
		for (ItemStack stack : a) {
			boolean matched = false;
			for (int i = 0; i < pool.size(); i++) {
				if (equalStacks(stack, pool.get(i))) {
					pool.remove(i);
					matched = true;
					break;
				}
			}
			if (!matched) {
				return false;
			}
		}
		return pool.isEmpty();
	}

	private static boolean equalStacks(ItemStack a, ItemStack b) {
		if (a.isEmpty() && b.isEmpty()) {
			return true;
		}
		if (a.isEmpty() != b.isEmpty()) {
			return false;
		}
		// Compare item + count only — a server reshuffle can re-create the stacks, which makes a full
		// component compare (ItemStack.areEqual) wrongly see them as different and skip the unscramble.
		return a.getItem() == b.getItem() && a.getCount() == b.getCount();
	}

	private static void notifyRestored(MinecraftClient mc, int moved) {
		long now = System.currentTimeMillis();
		if (now - lastNotify < 2000L) {
			return;
		}
		lastNotify = now;
		if (mc.player == null) {
			return;
		}
		mc.player.sendMessage(
			Text.literal("⚠ Anti-Trickster procced — restored hotbar (" + moved + " items moved)")
				.formatted(Formatting.AQUA), false);
		mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.4F);
	}
}
