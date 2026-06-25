package com.piccaxe.lsutils;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.gui.HudEditScreen;
import com.piccaxe.lsutils.gui.SettingsScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers the mod's keybinds and polls them every client tick.
 * In 1.21.11 keybind categories are typed ({@link KeyBinding.Category}), not strings.
 */
public final class KeyBindings {
	public static final KeyBinding.Category CATEGORY =
		KeyBinding.Category.create(Identifier.of(PiccaxeLsUtils.MOD_ID, "main"));

	public static KeyBinding openSettings;
	public static KeyBinding openHudEditor;
	public static KeyBinding toggleMaster;
	public static KeyBinding toggleFullbright;
	public static KeyBinding toggleAntiTrickster;
	public static KeyBinding toggleTraps;

	private KeyBindings() {
	}

	public static void register() {
		openSettings = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.piccaxelsutils.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY));
		openHudEditor = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.piccaxelsutils.hud_editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
		toggleMaster = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.piccaxelsutils.toggle_master", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
		toggleFullbright = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.piccaxelsutils.toggle_fullbright", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
		toggleAntiTrickster = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.piccaxelsutils.toggle_anti_trickster", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
		toggleTraps = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.piccaxelsutils.toggle_traps", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(KeyBindings::onTick);
	}

	private static void onTick(MinecraftClient client) {
		Config cfg = ConfigManager.get();
		boolean changed = false;

		while (openSettings.wasPressed()) {
			client.setScreen(new SettingsScreen(client.currentScreen));
		}

		while (openHudEditor.wasPressed()) {
			client.setScreen(new HudEditScreen(client.currentScreen));
		}

		while (toggleMaster.wasPressed()) {
			cfg.masterEnabled = !cfg.masterEnabled;
			changed = true;
			actionBar(client, "All features " + (cfg.masterEnabled ? "ON" : "OFF"),
				cfg.masterEnabled ? Formatting.GREEN : Formatting.RED);
		}

		while (toggleFullbright.wasPressed()) {
			cfg.fullbright = !cfg.fullbright;
			changed = true;
			actionBar(client, "Fullbright " + (cfg.fullbright ? "ON" : "OFF"),
				cfg.fullbright ? Formatting.GREEN : Formatting.RED);
		}

		while (toggleAntiTrickster.wasPressed()) {
			cfg.antiTrickster = !cfg.antiTrickster;
			changed = true;
			actionBar(client, "Anti-Trickster " + (cfg.antiTrickster ? "ON" : "OFF"),
				cfg.antiTrickster ? Formatting.GREEN : Formatting.RED);
		}

		while (toggleTraps.wasPressed()) {
			cfg.trapOutliner = !cfg.trapOutliner;
			changed = true;
			actionBar(client, "Trap Outlines " + (cfg.trapOutliner ? "ON" : "OFF"),
				cfg.trapOutliner ? Formatting.GREEN : Formatting.RED);
		}

		if (changed) {
			ConfigManager.save();
		}
	}

	private static void actionBar(MinecraftClient client, String msg, Formatting color) {
		if (client.player != null) {
			client.player.sendMessage(Text.literal(msg).formatted(color), true);
		}
	}
}
