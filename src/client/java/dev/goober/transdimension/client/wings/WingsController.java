package dev.goober.transdimension.client.wings;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import dev.goober.transdimension.item.TransWings;
import dev.goober.transdimension.network.WingActionPayload;

/**
 * How the Trans Wings fly, on the client (player movement is the client's job in Minecraft):
 *
 * <ul>
 *     <li><b>Charge and launch:</b> crouch on the ground and hold still to charge (the wings half open and tremble),
 *     then jump: you shoot up into the sky, up to about 25 blocks on a full charge, and the wings unfold into a
 *     glide at the top.</li>
 *     <li><b>Flap:</b> tap jump while gliding for a strong wingbeat that lifts you and pushes you forward. You have
 *     six flaps; landing refills them and gliding earns one back every four seconds.</li>
 *     <li><b>Soar:</b> the feathers catch the air, so you sink more slowly than with an elytra.</li>
 *     <li><b>Hover:</b> crouch while gliding to brake and drift gently down, for graceful landings.</li>
 * </ul>
 *
 * <p>Every action is reported to the server ({@link WingActionPayload}) for the sound and sparkles everyone sees.
 * Sparkle trails behind gliding players are spawned here for all players in view.
 */
public final class WingsController {
	public static final int MAX_CHARGE = 24;
	public static final int MAX_FLAPS = 6;
	private static final int MIN_CHARGE = 8;
	private static final int FLAP_COOLDOWN = 9;
	private static final int FLAP_REGEN_TICKS = 80;

	private static int charge;
	private static int flapsLeft = MAX_FLAPS;
	private static int flapCooldown;
	private static int glideTicks;
	private static boolean pendingGlide;
	private static boolean jumpWasDown;
	private static boolean wasOnGround;

	private WingsController() {
	}

	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			return;
		}
		WingAnimations.tick(level);
		spawnTrails(level);

		boolean jumpDown = client.options.keyJump.isDown();
		boolean jumpPressed = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;
		boolean onGround = player.onGround();
		boolean groundedLastTick = wasOnGround;
		wasOnGround = onGround;
		if (flapCooldown > 0) {
			flapCooldown--;
		}
		if (!TransWings.isWearing(player) || player.getAbilities().flying || player.isPassenger() || player.isInWater()) {
			charge = 0;
			pendingGlide = false;
			return;
		}

		boolean sneaking = client.options.keyShift.isDown();
		if (onGround) {
			flapsLeft = MAX_FLAPS;
			glideTicks = 0;
			Vec3 motion = player.getDeltaMovement();
			boolean still = motion.x * motion.x + motion.z * motion.z < 0.01;
			charge = sneaking && still ? Math.min(MAX_CHARGE, charge + 1) : Math.max(0, charge - 3);
		}
		if (jumpPressed && groundedLastTick && charge >= MIN_CHARGE) {
			launch(player, charge / (float) MAX_CHARGE);
			charge = 0;
			return;
		}
		if (onGround) {
			return;
		}

		if (pendingGlide && player.getDeltaMovement().y < 0.3 && !player.isFallFlying()) {
			pendingGlide = false;
			if (player.tryToStartFallFlying()) {
				ClientPlayNetworking.send(new WingActionPayload(WingActionPayload.GLIDE));
			}
		}
		if (!player.isFallFlying()) {
			return;
		}
		glideTicks++;
		if (glideTicks % FLAP_REGEN_TICKS == 0 && flapsLeft < MAX_FLAPS) {
			flapsLeft++;
		}
		if (jumpPressed && flapsLeft > 0 && flapCooldown == 0) {
			flap(player);
		}
		Vec3 v = player.getDeltaMovement();
		if (sneaking) {
			player.setDeltaMovement(v.x * 0.9, Math.max(v.y * 0.6, -0.06), v.z * 0.9);
		} else if (v.y < -0.08) {
			player.setDeltaMovement(v.x, v.y + 0.025, v.z);
		}
	}

	private static void launch(LocalPlayer player, float power) {
		Vec3 look = player.getLookAngle();
		player.setDeltaMovement(look.x * 0.4 * power, 0.9 + 1.1 * power, look.z * 0.4 * power);
		pendingGlide = true;
		ClientPlayNetworking.send(new WingActionPayload(WingActionPayload.LAUNCH));
		WingAnimations.flap(player.getId(), WingActionPayload.LAUNCH, player.level().getGameTime());
	}

	private static void flap(LocalPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 boosted = player.getDeltaMovement().add(look.x * 0.35, 0.55, look.z * 0.35);
		if (boosted.length() > 2.2) {
			boosted = boosted.normalize().scale(2.2);
		}
		player.setDeltaMovement(boosted.x, Math.min(boosted.y, 0.9), boosted.z);
		flapsLeft--;
		flapCooldown = FLAP_COOLDOWN;
		ClientPlayNetworking.send(new WingActionPayload(WingActionPayload.FLAP));
		WingAnimations.flap(player.getId(), WingActionPayload.FLAP, player.level().getGameTime());
	}

	/** Pink, blue and white sparkles streaming from the wingtips of every gliding player with Trans Wings. */
	private static void spawnTrails(ClientLevel level) {
		for (Player p : level.players()) {
			if (!p.isFallFlying() || !TransWings.isWearing(p)) {
				continue;
			}
			float yaw = p.getYRot() * Mth.DEG_TO_RAD;
			double rightX = -Math.cos(yaw);
			double rightZ = -Math.sin(yaw);
			for (int side = -1; side <= 1; side += 2) {
				int colour = TransWings.SPARKLE_COLOURS[Math.floorMod(p.tickCount + side, TransWings.SPARKLE_COLOURS.length)];
				level.addParticle(new DustParticleOptions(colour, 0.8F), p.getX() + rightX * 1.3 * side, p.getY() + 0.4,
						p.getZ() + rightZ * 1.3 * side, 0.0, 0.0, 0.0);
			}
			if (p.tickCount % 4 == 0) {
				level.addParticle(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.4, p.getZ(), 0.0, -0.02, 0.0);
			}
		}
	}

	/** A small HUD above the hotbar while wearing the wings: the launch charge, and the flaps left while airborne. */
	public static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || !TransWings.isWearing(player)) {
			return;
		}
		int cx = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() - 59;
		if (charge > 0) {
			int width = 60;
			int filled = Math.round(width * charge / (float) MAX_CHARGE);
			graphics.fill(cx - width / 2 - 1, y - 1, cx + width / 2 + 1, y + 4, 0xC0201030);
			for (int i = 0; i < filled; i++) {
				int colour = i < width / 3 ? 0xFF5BCEFA : i < 2 * width / 3 ? 0xFFF5A9B8 : 0xFFFFFFFF;
				graphics.fill(cx - width / 2 + i, y, cx - width / 2 + i + 1, y + 3, colour);
			}
		} else if (!player.onGround()) {
			for (int i = 0; i < MAX_FLAPS; i++) {
				int x = cx - MAX_FLAPS * 5 + i * 10;
				int colour = i < flapsLeft ? (i % 2 == 0 ? 0xFF5BCEFA : 0xFFF5A9B8) : 0x60FFFFFF;
				graphics.fill(x + 1, y, x + 7, y + 2, colour);
				graphics.fill(x + 2, y + 2, x + 6, y + 3, colour);
				graphics.fill(x + 3, y + 3, x + 5, y + 4, colour);
			}
		}
	}
}
