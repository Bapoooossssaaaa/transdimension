package dev.goober.transdimension.client;

import java.util.Arrays;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/**
 * Glowing coloured boxes (beams, cubes, sheets of light), gathered for one frame and handed to the level renderer in one
 * go, relative to the camera. They're drawn unlit and see-through ({@code RenderTypes.debugFilledBox()}, the way Fabric's
 * own level render event tests draw in 26.2), so they shine just as bright in the dark. Used by the candle ritual
 * (RitualEffects), the Fairy Realm cutscene (FairyCutsceneCamera) and the cloud boat's line (CloudBoatRenderer).
 */
public final class GlowGeometry {
	private final Vec3 camera;
	private float[] positions = new float[3 * 24 * 32];
	private int[] colours = new int[24 * 32];
	private int vertices;

	public GlowGeometry(Vec3 camera) {
		this.camera = camera;
	}

	/** A square beam from {@code from} to {@code to}, {@code half} wide either side, turned {@code spin} radians about itself. */
	public void beam(Vec3 from, Vec3 to, double half, double spin, int argb) {
		Vec3 axis = to.subtract(from);
		double length = axis.length();
		if (length < 1.0E-4 || (argb >>> 24) == 0) {
			return;
		}
		Vec3 dir = axis.scale(1.0 / length);
		Vec3 helper = Math.abs(dir.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
		Vec3 u = dir.cross(helper).normalize();
		Vec3 v = dir.cross(u).normalize();
		double cos = Math.cos(spin);
		double sin = Math.sin(spin);
		Vec3 ex = u.scale(cos).add(v.scale(sin)).scale(half);
		Vec3 ey = v.scale(cos).subtract(u.scale(sin)).scale(half);
		this.box(from.add(to).scale(0.5), ex, ey, axis.scale(0.5), argb);
	}

	/** A cube of half-size {@code half} centred on {@code at}. */
	public void cube(Vec3 at, double half, int argb) {
		this.box(at, new Vec3(half, 0.0, 0.0), new Vec3(0.0, half, 0.0), new Vec3(0.0, 0.0, half), argb);
	}

	/** A thin sheet standing in a gate that faces {@code facing}: {@code along} and {@code up} out from the middle. */
	public void sheet(Vec3 centre, Direction facing, double along, double up, double thickness, int argb) {
		Direction across = facing.getClockWise();
		this.box(centre, new Vec3(across.getStepX() * along, 0.0, across.getStepZ() * along), new Vec3(0.0, up, 0.0),
				new Vec3(facing.getStepX() * thickness, 0.0, facing.getStepZ() * thickness), argb);
	}

	/** A box centred on {@code c} with half-axes {@code ex}, {@code ey} and {@code ez}. */
	public void box(Vec3 c, Vec3 ex, Vec3 ey, Vec3 ez, int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}
		Vec3[] k = new Vec3[8];
		for (int i = 0; i < 8; i++) {
			Vec3 p = c.add(ex.scale((i & 1) == 0 ? -1.0 : 1.0)).add(ey.scale((i & 2) == 0 ? -1.0 : 1.0)).add(ez.scale((i & 4) == 0 ? -1.0 : 1.0));
			k[i] = p;
		}
		// six faces, each corner index list going round the face
		this.quad(k[0], k[1], k[3], k[2], argb);
		this.quad(k[4], k[6], k[7], k[5], argb);
		this.quad(k[0], k[4], k[5], k[1], argb);
		this.quad(k[2], k[3], k[7], k[6], argb);
		this.quad(k[0], k[2], k[6], k[4], argb);
		this.quad(k[1], k[5], k[7], k[3], argb);
	}

	private void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
		this.vertex(a, argb);
		this.vertex(b, argb);
		this.vertex(c, argb);
		this.vertex(d, argb);
	}

	private void vertex(Vec3 p, int argb) {
		if (this.vertices == this.colours.length) {
			this.colours = Arrays.copyOf(this.colours, this.colours.length * 2);
			this.positions = Arrays.copyOf(this.positions, this.positions.length * 2);
		}
		int i = this.vertices * 3;
		this.positions[i] = (float) (p.x - this.camera.x);
		this.positions[i + 1] = (float) (p.y - this.camera.y);
		this.positions[i + 2] = (float) (p.z - this.camera.z);
		this.colours[this.vertices++] = argb;
	}

	public void submit(LevelRenderContext context) {
		this.submit(context.submitNodeCollector(), context.poseStack());
	}

	/**
	 * Hands the boxes to {@code nodeCollector}, drawn from wherever {@code poseStack} stands: the camera for the level
	 * render event, or an entity's position (with that position as this geometry's "camera") from an entity renderer.
	 */
	public void submit(SubmitNodeCollector nodeCollector, PoseStack poseStack) {
		if (this.vertices == 0) {
			return;
		}
		float[] xyz = Arrays.copyOf(this.positions, this.vertices * 3);
		int[] argb = Arrays.copyOf(this.colours, this.vertices);
		nodeCollector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
			for (int i = 0; i < argb.length; i++) {
				buffer.addVertex(pose, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]).setColor(argb[i]);
			}
		});
	}

	/** A colour with an alpha from 0 to 1. */
	public static int argb(double alpha, int rgb) {
		int a = Mth.clamp((int) Math.round(alpha * 255.0), 0, 255);
		return a << 24 | rgb & 0xFFFFFF;
	}
}
