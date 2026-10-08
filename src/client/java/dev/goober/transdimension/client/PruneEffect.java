package dev.goober.transdimension.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.client.entity.MaddieRenderer;
import dev.goober.transdimension.world.FairyCutscene;

/**
 * Maddie being pruned, at the end of the Fairy Realm's cutscene (FairyCutscene#prune), like someone pruned in the show:
 * the moment the Trans Fairy's wand touches her heart there's a white flash, light in the flag's colours spreads over her
 * from that spot until she's nothing but light, and then she comes apart into it, the unravelling running out from her
 * heart, the pieces drifting up and away as they fade.
 *
 * <p>She's built of motes: two-pixel cubes filling every part of her model in the pose she's held up in (MaddieRenderer's
 * lifted pose), each coloured by the flag's stripe at its height (blue, pink, white, pink, blue from her feet up). Until a
 * mote lights up her own model shows there; lit, it covers her; let go, it drifts. The server takes her away once all of
 * her has lit up (FairyCutscene.PRUNED), so only the light is left to come apart. It's all worked out from the time since
 * the touch, so it plays the same at any frame rate, and it's drawn with the scene's other light
 * (FairyCutsceneCamera#renderLight, GlowGeometry).
 */
public final class PruneEffect {
	private static final int BLUE = 0x5BCEFA;
	private static final int PINK = 0xF5A9B8;
	private static final int WHITE = 0xFFFFFF;
	private static final int[] STRIPES = {BLUE, PINK, WHITE, PINK, BLUE};
	private static final double PIXEL = 1.0 / 16.0;
	/** A mote's half-size: two pixels across, and a hair more so they close up into her shape. */
	private static final double HALF = PIXEL + 0.004;
	/** How fast (blocks a tick) the light spreads over her from the wand, and the unravelling after it. */
	private static final double GLOW_SPEED = 0.25;
	private static final double UNRAVEL_SPEED = 0.12;
	/** Ticks after the touch that the unravelling starts: as the server takes her away. */
	private static final double UNRAVEL = FairyCutscene.PRUNED - FairyCutscene.TOUCH;
	/** Her height from her feet to the top of her tipped-back head, for the flag's stripes. */
	private static final double TALL = 1.95;
	/** How fast a loose mote's rise speeds up (blocks a tick, each tick). */
	private static final double RISE = 0.0012;
	/** The flash where the wand touches, and the sparks out of it: how long they last (ticks), and how many sparks. */
	private static final double FLASH = 6.0;
	private static final int SPARKS = 10;

	/**
	 * One mote: where it is in her, which way it drifts once let go (blocks a tick), when it lights and lets go (ticks after
	 * the touch), how long it lasts once loose, and its colour.
	 */
	private record Mote(Vec3 at, Vec3 drift, double lights, double lets, double life, int colour) {
	}

	/**
	 * A part of her model (MaddieRenderer's mesh, in model pixels): its pivot, how it's turned, and its box (corner and
	 * size). Turned as ModelPart turns a part: about X, then Z.
	 */
	private record Part(double px, double py, double pz, float xRot, float zRot, double bx, double by, double bz, double w, double h, double d) {
	}

	private static List<Mote> motes = List.of();
	private static List<Vec3> sparks = List.of();
	private static Vec3 touch = Vec3.ZERO;
	private static double start = Double.NaN;

	private PruneEffect() {
	}

	/**
	 * The wand touches her at scene time {@code time}: her feet at {@code feet}, her body turned to {@code yaw} (degrees,
	 * as the game counts them), facing the fairy.
	 */
	public static void start(Vec3 feet, float yaw, double time) {
		Vec3 front = facing(yaw);
		Vec3 right = new Vec3(-front.z, 0.0, front.x);
		touch = feet.add(0.0, FairyCutscene.HEART, 0.0).add(front.scale(FairyCutscene.HEART_AHEAD));
		RandomSource random = RandomSource.create(Double.doubleToLongBits(time));
		float arm = MaddieRenderer.LIFTED_ARM_Z;
		float leg = MaddieRenderer.LIFTED_LEG_Z;
		List<Part> parts = List.of(
				new Part(0.0, 0.0, 0.0, MaddieRenderer.LIFTED_HEAD_X, 0.0F, -4.0, -8.0, -4.0, 8.0, 8.0, 8.0),
				new Part(0.0, 0.0, 0.0, 0.0F, 0.0F, -4.0, 0.0, -2.0, 8.0, 12.0, 4.0),
				new Part(-5.0, 2.5, 0.0, MaddieRenderer.LIFTED_ARM_X, arm, -2.0, -2.0, -2.0, 3.0, 12.0, 4.0),
				new Part(5.0, 2.5, 0.0, MaddieRenderer.LIFTED_ARM_X, -arm, -1.0, -2.0, -2.0, 3.0, 12.0, 4.0),
				new Part(-1.9, 12.0, 0.0, 0.0F, leg, -2.0, 0.0, -2.0, 4.0, 12.0, 4.0),
				new Part(1.9, 12.0, 0.0, 0.0F, -leg, -2.0, 0.0, -2.0, 4.0, 12.0, 4.0));
		List<Mote> list = new ArrayList<>();
		for (Part part : parts) {
			int nx = Math.max(1, (int) Math.round(part.w() / 2.0));
			int ny = Math.max(1, (int) Math.round(part.h() / 2.0));
			int nz = Math.max(1, (int) Math.round(part.d() / 2.0));
			for (int i = 0; i < nx; i++) {
				for (int j = 0; j < ny; j++) {
					for (int k = 0; k < nz; k++) {
						Vec3 at = place(feet, right, front, part, part.bx() + (i + 0.5) * part.w() / nx, part.by() + (j + 0.5) * part.h() / ny,
								part.bz() + (k + 0.5) * part.d() / nz);
						list.add(mote(at, feet, front, random));
					}
				}
			}
		}
		motes = List.copyOf(list);
		List<Vec3> rays = new ArrayList<>();
		for (int i = 0; i < SPARKS; i++) {
			// out of her heart, back away from the wand and every which way round
			Vec3 out = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize();
			rays.add(out.subtract(front.scale(0.6)).normalize());
		}
		sparks = List.copyOf(rays);
		start = time;
	}

	/** One mote at {@code at}: its stripe, and when it lights, lets go and fades, from how far it is from the wand. */
	private static Mote mote(Vec3 at, Vec3 feet, Vec3 front, RandomSource random) {
		double far = at.distanceTo(touch);
		int stripe = Mth.clamp((int) ((at.y - feet.y) / TALL * STRIPES.length), 0, STRIPES.length - 1);
		Vec3 out = new Vec3(at.x - feet.x, 0.0, at.z - feet.z);
		out = out.lengthSqr() < 1.0E-4 ? new Vec3(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5) : out;
		Vec3 drift = out.normalize().scale(0.012 + random.nextDouble() * 0.024)
				.add(0.0, 0.012 + random.nextDouble() * 0.018, 0.0)
				.subtract(front.scale(0.01))
				.add((random.nextDouble() - 0.5) * 0.016, (random.nextDouble() - 0.5) * 0.008, (random.nextDouble() - 0.5) * 0.016);
		double lets = UNRAVEL + far / UNRAVEL_SPEED + random.nextDouble() * 3.0;
		return new Mote(at, drift, far / GLOW_SPEED, lets, 18.0 + random.nextDouble() * 12.0, STRIPES[stripe]);
	}

	/** A point in a part of her (model pixels), where it is in the world. */
	private static Vec3 place(Vec3 feet, Vec3 right, Vec3 front, Part part, double x, double y, double z) {
		double cx = Math.cos(part.xRot());
		double sx = Math.sin(part.xRot());
		double y1 = y * cx - z * sx;
		double z1 = y * sx + z * cx;
		double cz = Math.cos(part.zRot());
		double sz = Math.sin(part.zRot());
		double mx = part.px() + x * cz - y1 * sz;
		double my = part.py() + x * sz + y1 * cz;
		double mz = part.pz() + z1;
		// The model's pixels: x to her left, y down from 1.5 blocks over her feet, z behind her.
		return feet.add(right.scale(-mx * PIXEL)).add(0.0, 1.5 - my * PIXEL, 0.0).add(front.scale(-mz * PIXEL));
	}

	public static void clear() {
		motes = List.of();
		sparks = List.of();
		start = Double.NaN;
	}

	/** Draws her light at scene time {@code time}, if she's being pruned. */
	public static void render(GlowGeometry g, double time) {
		if (Double.isNaN(start)) {
			return;
		}
		double p = time - start;
		if (p < 0.0) {
			return;
		}
		boolean left = false;
		for (Mote m : motes) {
			if (p < m.lights()) {
				left = true;
			} else if (p < m.lets()) {
				// lit where it is: a flash of white as the light reaches it, settling into its stripe
				double k = Math.min(1.0, (p - m.lights()) / 3.0);
				g.cube(m.at(), HALF, GlowGeometry.argb(0.9 * Math.max(k, 0.35), mix(WHITE, m.colour(), k)));
				left = true;
			} else {
				double age = p - m.lets();
				double f = age / m.life();
				if (f < 1.0) {
					Vec3 at = m.at().add(m.drift().scale(age)).add(0.0, 0.5 * RISE * age * age, 0.0);
					double fade = 1.0 - f;
					g.cube(at, HALF * (1.0 - 0.6 * f), GlowGeometry.argb(0.9 * fade * fade, mix(m.colour(), WHITE, 0.3 * fade)));
					left = true;
				}
			}
		}
		if (p < FLASH) {
			// the touch: a white flash at the wand's tip and a burst of thin sparks out of it
			double k = p / FLASH;
			g.cube(touch, 0.05 + 0.13 * k, GlowGeometry.argb(0.9 * (1.0 - k), WHITE));
			for (int i = 0; i < sparks.size(); i++) {
				Vec3 ray = sparks.get(i);
				g.beam(touch.add(ray.scale(0.1 + 0.4 * k)), touch.add(ray.scale(0.25 + 0.8 * k)), 0.012, 0.0,
						GlowGeometry.argb(0.85 * (1.0 - k), i % 2 == 0 ? PINK : BLUE));
			}
			left = true;
		}
		if (!left) {
			clear();
		}
	}

	/** The way something with this yaw (degrees, as the game counts them) faces, level. */
	private static Vec3 facing(float yaw) {
		float y = yaw * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(y), 0.0, Mth.cos(y));
	}

	/** {@code a} blended towards {@code b} by {@code f} (0 to 1), as 0xRRGGBB. */
	private static int mix(int a, int b, double f) {
		int r = (int) Math.round(Mth.lerp(f, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int gr = (int) Math.round(Mth.lerp(f, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = (int) Math.round(Mth.lerp(f, a & 0xFF, b & 0xFF));
		return r << 16 | gr << 8 | bl;
	}
}
