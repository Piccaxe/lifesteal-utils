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

	/** Like {@link #project} but keeps points that are off the sides of the screen (null only if behind). */
	public static float[] projectUnclipped(Vec3d worldPos, int scaledWidth, int scaledHeight) {
		Vector4f v = clip(worldPos);
		if (v.w() <= 0.05F) {
			return null;
		}
		return toScreen(v.x() / v.w(), v.y() / v.w(), scaledWidth, scaledHeight);
	}

	/**
	 * Always returns a screen point: on-screen targets project normally; targets behind the camera are
	 * mirrored and pushed out to the screen edge, so a tracer still points the right way.
	 */
	public static float[] projectTowards(Vec3d worldPos, int scaledWidth, int scaledHeight) {
		Vector4f v = clip(worldPos);
		float nx;
		float ny;
		if (v.w() > 0.05F) {
			nx = v.x() / v.w();
			ny = v.y() / v.w();
			if (Math.abs(nx) <= 1.0F && Math.abs(ny) <= 1.0F) {
				return toScreen(nx, ny, scaledWidth, scaledHeight);
			}
		} else {
			nx = -v.x();
			ny = -v.y();
			if (Math.abs(nx) < 1.0E-4F && Math.abs(ny) < 1.0E-4F) {
				ny = -1.0F; // directly behind: point down
			}
		}
		float k = 1.0F / Math.max(Math.abs(nx), Math.abs(ny)); // scale onto the screen border
		return toScreen(nx * k, ny * k, scaledWidth, scaledHeight);
	}

	private static Vector4f clip(Vec3d worldPos) {
		Vec3d rel = worldPos.subtract(camPos);
		Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
		view.transform(v);
		proj.transform(v);
		return v;
	}

	private static float[] toScreen(float ndcX, float ndcY, int scaledWidth, int scaledHeight) {
		return new float[]{(ndcX * 0.5F + 0.5F) * scaledWidth, (1.0F - (ndcY * 0.5F + 0.5F)) * scaledHeight};
	}

	public static Vec3d cameraPos() {
		return camPos;
	}
}
