package dev.goober.transdimension.client.herobrine;

import java.util.Arrays;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/**
 * A figure drawn straight from a 64x64 player skin: textured boxes gathered for one frame, relative to the camera, and
 * handed to the renderer as custom geometry with the skin as a cut-out entity texture (see-through pixels, such as an
 * empty hat layer, simply aren't drawn). Nothing here is an entity.
 *
 * <p>Each box is laid out on the skin the way the game lays out its model boxes: from its texture corner, the right
 * side, then the front, the left side and the back in one strip, with the top above the front and the bottom beside it.
 */
final class SkinFigure {
	/** One skin pixel, in blocks. */
	static final double PIXEL = 1.0 / 16.0;
	private static final float SKIN = 64.0F;

	private final Vec3 camera;
	/** Per vertex: x, y, z (relative to the camera), u, v, and the normal's x, y, z. */
	private float[] data = new float[8 * 4 * 6 * 16];
	private int vertices;

	SkinFigure(Vec3 camera) {
		this.camera = camera;
	}

	/**
	 * One box of the skin at texture corner ({@code u}, {@code v}), {@code w} by {@code h} by {@code d} pixels, centred on
	 * {@code centre}, with the figure's {@code right}, {@code up} and {@code front} (unit vectors), grown {@code inflate}
	 * pixels each way (the skin's outer layer).
	 */
	void box(Vec3 centre, Vec3 right, Vec3 up, Vec3 front, int u, int v, int w, int h, int d, double inflate) {
		Vec3 r = right.scale((w / 2.0 + inflate) * PIXEL);
		Vec3 t = up.scale((h / 2.0 + inflate) * PIXEL);
		Vec3 f = front.scale((d / 2.0 + inflate) * PIXEL);
		// the front, seen from in front: its left edge is the figure's right
		this.face(centre.add(f).add(r).add(t), centre.add(f).subtract(r).add(t), centre.add(f).subtract(r).subtract(t),
				centre.add(f).add(r).subtract(t), u + d, v + d, u + d + w, v + d + h, front);
		this.face(centre.subtract(f).subtract(r).add(t), centre.subtract(f).add(r).add(t), centre.subtract(f).add(r).subtract(t),
				centre.subtract(f).subtract(r).subtract(t), u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h, front.scale(-1.0));
		// the right side, seen from the right: the back is on its left
		this.face(centre.add(r).subtract(f).add(t), centre.add(r).add(f).add(t), centre.add(r).add(f).subtract(t),
				centre.add(r).subtract(f).subtract(t), u, v + d, u + d, v + d + h, right);
		this.face(centre.subtract(r).add(f).add(t), centre.subtract(r).subtract(f).add(t), centre.subtract(r).subtract(f).subtract(t),
				centre.subtract(r).add(f).subtract(t), u + d + w, v + d, u + 2 * d + w, v + d + h, right.scale(-1.0));
		// the top, its front edge along the bottom of its patch
		this.face(centre.add(t).add(r).subtract(f), centre.add(t).subtract(r).subtract(f), centre.add(t).subtract(r).add(f),
				centre.add(t).add(r).add(f), u + d, v, u + d + w, v + d, up);
		this.face(centre.subtract(t).add(r).add(f), centre.subtract(t).subtract(r).add(f), centre.subtract(t).subtract(r).subtract(f),
				centre.subtract(t).add(r).subtract(f), u + d + w, v, u + d + 2 * w, v + d, up.scale(-1.0));
	}

	/** A face with corners top left, top right, bottom right, bottom left and its patch of the skin, in pixels. */
	private void face(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int u0, int v0, int u1, int v1, Vec3 normal) {
		this.vertex(a, u0, v0, normal);
		this.vertex(b, u1, v0, normal);
		this.vertex(c, u1, v1, normal);
		this.vertex(d, u0, v1, normal);
	}

	private void vertex(Vec3 p, int u, int v, Vec3 normal) {
		int i = this.vertices * 8;
		if (i + 8 > this.data.length) {
			this.data = Arrays.copyOf(this.data, this.data.length * 2);
		}
		this.data[i] = (float) (p.x - this.camera.x);
		this.data[i + 1] = (float) (p.y - this.camera.y);
		this.data[i + 2] = (float) (p.z - this.camera.z);
		this.data[i + 3] = u / SKIN;
		this.data[i + 4] = v / SKIN;
		this.data[i + 5] = (float) normal.x;
		this.data[i + 6] = (float) normal.y;
		this.data[i + 7] = (float) normal.z;
		this.vertices++;
	}

	/** Draws the figure with {@code skin} at {@code light} (packed block and sky light, as the game packs it). */
	void submit(LevelRenderContext context, Identifier skin, int light) {
		if (this.vertices == 0) {
			return;
		}
		float[] v = Arrays.copyOf(this.data, this.vertices * 8);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(skin), (pose, buffer) -> {
			for (int i = 0; i < v.length; i += 8) {
				buffer.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(-1).setUv(v[i + 3], v[i + 4])
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, v[i + 5], v[i + 6], v[i + 7]);
			}
		});
	}
}
