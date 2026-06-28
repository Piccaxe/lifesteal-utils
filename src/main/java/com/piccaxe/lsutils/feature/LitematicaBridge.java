package com.piccaxe.lsutils.feature;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.World;

import java.lang.reflect.Method;

/**
 * Minimal reflection bridge into Litematica — no compile-time dependency, so the build stays
 * self-contained. We only need the schematic world: {@code WorldSchematic extends World} and holds
 * the schematic's blocks at their real world coordinates (placement origin already applied), so the
 * builder can just compare {@code schematicWorld.getBlockState(pos)} against the real world.
 */
public final class LitematicaBridge {
	private static boolean resolved = false;
	private static Method getSchematicWorld;

	private LitematicaBridge() {
	}

	public static boolean isPresent() {
		return FabricLoader.getInstance().isModLoaded("litematica");
	}

	private static void resolve() {
		if (resolved) {
			return;
		}
		resolved = true;
		try {
			Class<?> handler = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
			getSchematicWorld = handler.getMethod("getSchematicWorld");
		} catch (Throwable t) {
			getSchematicWorld = null;
		}
	}

	/** The current Litematica schematic world, or null if Litematica is absent / no schematic loaded. */
	public static World getSchematicWorld() {
		if (!isPresent()) {
			return null;
		}
		resolve();
		if (getSchematicWorld == null) {
			return null;
		}
		try {
			Object world = getSchematicWorld.invoke(null);
			return world instanceof World w ? w : null;
		} catch (Throwable t) {
			return null;
		}
	}
}
