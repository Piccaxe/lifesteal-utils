package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Honest hotbar key remapping. Vanilla's number-key handler in {@code handleInputEvents} calls
 * {@code PlayerInventory.setSelectedSlot(keyIndex)}. We rewrite that argument so pressing key
 * (k+1) selects {@code hotbarKeyMap[k]} instead — letting you reorganize your hotbar and keep
 * your muscle memory. This does NOT desync anything: the real selected slot, the highlight, and
 * the packet sent to the server all reflect the slot actually chosen. It only changes which slot
 * the key picks, exactly like rebinding a key.
 */
@Mixin(MinecraftClient.class)
public class HotbarRemapMixin {

	@ModifyArg(
		method = "handleInputEvents",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;setSelectedSlot(I)V"),
		index = 0)
	private int lsutils$remapHotbarKey(int keyIndex) {
		Config cfg = ConfigManager.get();
		if (cfg == null || !cfg.hotbarRemap || keyIndex < 0 || keyIndex > 8) {
			return keyIndex;
		}
		return cfg.hotbarKeyMapSafe()[keyIndex];
	}
}
