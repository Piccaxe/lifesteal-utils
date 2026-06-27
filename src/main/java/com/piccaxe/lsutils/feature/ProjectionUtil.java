package com.piccaxe.lsutils.feature;

import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Holds the world camera position + view/projection matrices (captured each frame by
 * {@code CameraMatrixMixin}) so HUD-layer code can project world positions to screen coordinates.
 * This lets us draw "above the head" text in the HUD layer, which renders reliably, instead of in the
 * world render pass (which wasn't drawing on this setup).
 */
public final class ProjectionUtil {
	private static volatile Vec3d camPos = Vec3d.ZERO;
	private static volatile Matrix4f view = new Matrix4f();
	private static volatile Matrix4f proj = new Matrix4f();

	private ProjectionUtil() {
	}

	public static void update(Vec3d cam, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
		camPos = cam;
		view = new Matrix4f(viewMatrix);
		proj = new Matrix4f(projectionMatrix);
	}

	/** Projects a world position to scaled screen coords, or null if behind the camera / off-screen. */
	public static float[] project(Vec3d worldPos, int scaledWidth, int scaledHeight) {
		Vec3d rel = worldPos.subtract(camPos);
		Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
		view.transform(v);
		proj.transform(v);
		if (v.w() <= 0.05F) {
			return null;
		}
		float ndcX = v.x() / v.w();
		float ndcY = v.y() / v.w();
		if (ndcX < -1.0F || ndcX > 1.0F || ndcY < -1.0F || ndcY > 1.0F) {
			return null;
		}
		float sx = (ndcX * 0.5F + 0.5F) * scaledWidth;
		float sy = (1.0F - (ndcY * 0.5F + 0.5F)) * scaledHeight;
		return new float[]{sx, sy};
	}
}
