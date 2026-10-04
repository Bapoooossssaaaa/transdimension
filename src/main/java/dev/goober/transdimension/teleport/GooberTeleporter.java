package dev.goober.transdimension.teleport;

import java.util.Locale;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModBlocks;

/**
 * Say "Goober" in chat (or run /goober) to travel to the Trans Realm.
 * Say it again inside the realm to go back to exactly where you left.
 */
public final class GooberTeleporter {
	/** Where the player was standing before entering the realm. Saved with the player and kept on death. */
	public static final AttachmentType<GlobalPos> RETURN_POINT = AttachmentRegistry.create(
			TransDimension.id("return_point"),
			builder -> builder.persistent(GlobalPos.CODEC).copyOnDeath());

	private GooberTeleporter() {
	}

	public static void initialize() {
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, boundChatType) -> {
			if (isMagicWord(message.signedContent())) {
				// Run on the server thread after the chat message has been broadcast.
				sender.level().getServer().execute(() -> goober(sender));
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(Commands.literal("goober").executes(context -> {
					goober(context.getSource().getPlayerOrException());
					return 1;
				})));
	}

	/** "Goober", "goober!", "GOOBER!!!" all count. */
	static boolean isMagicWord(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").equals("goober");
	}

	public static void goober(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel realm = server.getLevel(TransDimension.TRANS_REALM);
		if (realm == null) {
			player.sendSystemMessage(Component.translatable("message.transdimension.realm_missing"));
			return;
		}

		if (player.isPassenger()) {
			player.stopRiding();
		}

		if (player.level().dimension().equals(TransDimension.TRANS_REALM)) {
			goHome(player, server);
		} else {
			enterRealm(player, realm);
		}
	}

	private static void enterRealm(ServerPlayer player, ServerLevel realm) {
		player.setAttached(RETURN_POINT, GlobalPos.of(player.level().dimension(), player.blockPosition()));

		BlockPos arrival = findArrival(realm, player.getBlockX(), player.getBlockZ());
		player.teleport(new TeleportTransition(realm, Vec3.atBottomCenterOf(arrival), Vec3.ZERO,
				player.getYRot(), 0.0F, TeleportTransition.DO_NOTHING));

		realm.sendParticles(ParticleTypes.HEART,
				arrival.getX() + 0.5, arrival.getY() + 1.0, arrival.getZ() + 0.5,
				24, 1.2, 0.8, 1.2, 0.1);
		player.sendSystemMessage(Component.translatable("message.transdimension.welcome"));
	}

	private static void goHome(ServerPlayer player, MinecraftServer server) {
		GlobalPos home = player.getAttached(RETURN_POINT);
		ServerLevel target = home != null ? server.getLevel(home.dimension()) : null;
		BlockPos pos;

		if (target == null || target.dimension().equals(TransDimension.TRANS_REALM)) {
			// No saved spot (or it was inside the realm): fall back to the world spawn.
			target = server.overworld();
			BlockPos spawn = target.getRespawnData().pos();
			target.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
			pos = new BlockPos(spawn.getX(), target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), spawn.getZ());
		} else {
			pos = home.pos();
		}

		player.teleport(new TeleportTransition(target, Vec3.atBottomCenterOf(pos), Vec3.ZERO,
				player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
		player.removeAttached(RETURN_POINT);
	}

	/** Finds the surface at x/z. Over the ocean, builds a tiny pride island so you never land in the water. */
	private static BlockPos findArrival(ServerLevel realm, int x, int z) {
		realm.getChunk(x >> 4, z >> 4); // make sure the chunk is generated
		int y = realm.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos feet = new BlockPos(x, y, z);

		boolean overFluid = !realm.getFluidState(feet.below()).isEmpty();
		if (!overFluid && y > -60) {
			return feet;
		}

		int surface = Math.max(y, 64);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
					continue; // round the corners
				}
				realm.setBlockAndUpdate(new BlockPos(x + dx, surface - 2, z + dz), ModBlocks.TRANS_DIRT.defaultBlockState());
				realm.setBlockAndUpdate(new BlockPos(x + dx, surface - 1, z + dz), ModBlocks.TRANS_GRASS_BLOCK.defaultBlockState());
				for (int dy = 0; dy < 3; dy++) {
					realm.setBlockAndUpdate(new BlockPos(x + dx, surface + dy, z + dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
		realm.setBlockAndUpdate(new BlockPos(x + 1, surface, z + 1), ModBlocks.PRIDE_BLOSSOM.defaultBlockState());
		return new BlockPos(x, surface, z);
	}
}
