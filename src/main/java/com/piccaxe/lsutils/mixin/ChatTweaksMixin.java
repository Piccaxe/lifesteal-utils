package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Chat tweaks: optional [HH:mm] timestamps prepended to every message, and anti-spam that drops an
 * exact duplicate of the previous message within a short window. Both gate on config.
 */
@Mixin(ChatHud.class)
public class ChatTweaksMixin {
	@Unique private static final DateTimeFormatter PICCAXE_TIME = DateTimeFormatter.ofPattern("HH:mm");
	@Unique private static String piccaxe$lastMessage = "";
	@Unique private static long piccaxe$lastTime = 0L;

	@Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
		at = @At("HEAD"), cancellable = true)
	private void piccaxelsutils$antiSpam(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
		if (!ConfigManager.get().chatAntiSpam) {
			return;
		}
		String s = message.getString();
		long now = System.currentTimeMillis();
		if (s.equals(piccaxe$lastMessage) && now - piccaxe$lastTime < 800L) {
			ci.cancel();
			return;
		}
		piccaxe$lastMessage = s;
		piccaxe$lastTime = now;
	}

	@ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
		at = @At("HEAD"), argsOnly = true)
	private Text piccaxelsutils$timestamp(Text message) {
		if (!ConfigManager.get().chatTimestamps || message == null) {
			return message;
		}
		MutableText prefix = Text.literal("[" + LocalTime.now().format(PICCAXE_TIME) + "] ").formatted(Formatting.DARK_GRAY);
		return prefix.append(message);
	}
}
