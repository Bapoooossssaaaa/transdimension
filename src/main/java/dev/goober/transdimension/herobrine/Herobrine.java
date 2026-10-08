package dev.goober.transdimension.herobrine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.goober.transdimension.registry.ModAttachments;

/**
 * Herobrine: the host's secret. Now and then, for the players the host picks, he stands far off watching, or right
 * behind them, and he's gone the moment they come close, stare at him or turn round.
 *
 * <p>He is never an entity. The server only tells each chosen player's client, on its own, where he stands
 * ({@link HerobrineSightingPayload}), and that client draws him (HerobrineClient). So nothing that lists entities can
 * find him: not minimap radars like Xaero's, not F3's entity count, not hitboxes, not the player list. Other players
 * (and the host, unless they choose to see him too) are never told anything.
 *
 * <p>The host sets him up in a secret screen opened with {@code /tdsettings} ({@link #COMMAND}), which only the world's
 * host (in singleplayer, or whoever opened it to LAN) or an operator can see or use. The settings live with the world
 * ({@link HerobrineSettings}, on the Overworld) and he stays away until they're saved with him turned on. Each player who
 * can see him gets a chance every second, so a sighting comes about every few minutes of play as the settings say, but
 * never in their first minute online, and never within half a minute of the last one ending.
 */
public final class Herobrine {
	/** The secret command that opens the settings. */
	public static final String COMMAND = "tdsettings";
	/** No sighting in a player's first minute online, and none within half a minute of the last one ending. */
	private static final int SETTLE_IN = 20 * 60;
	private static final int REST = 20 * 30;
	/** How much further than where he'd vanish a far-off sighting can be (up to). */
	private static final double FAR_RANGE = 36.0;
	/** How far behind a player he stands, the likeliest spots first. */
	private static final double[] BEHIND_DISTANCES = {4.0, 5.0, 3.0, 6.0};
	/** No sighting before this server tick, by player. */
	private static final Map<UUID, Integer> QUIET_UNTIL = new HashMap<>();

	private Herobrine() {
	}

	public static void initialize() {
		PayloadTypeRegistry.clientboundPlay().register(HerobrineSightingPayload.TYPE, HerobrineSightingPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HerobrineMenuPayload.TYPE, HerobrineMenuPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HerobrineSettingsPayload.TYPE, HerobrineSettingsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HerobrineSettingsPayload.TYPE, (payload, context) -> save(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(Herobrine::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> QUIET_UNTIL.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> QUIET_UNTIL.clear());
		// Only the host and operators get this command at all: everyone else's game never hears of it.
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(Commands.literal(COMMAND).requires(Herobrine::mayConfigure).executes(context -> {
					ServerPlayer player = context.getSource().getPlayerOrException();
					ServerPlayNetworking.send(player, new HerobrineMenuPayload(settings(player.level().getServer())));
					return 1;
				})));
	}

	public static HerobrineSettings settings(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(ModAttachments.HEROBRINE, HerobrineSettings.DEFAULT);
	}

	/** The world's host (in singleplayer, or whoever opened it to LAN), or an operator. */
	private static boolean mayConfigure(CommandSourceStack source) {
		Predicate<CommandSourceStack> operator = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
		if (operator.test(source)) {
			return true;
		}
		ServerPlayer player = source.getPlayer();
		return player != null && source.getServer().isSingleplayerOwner(new NameAndId(player.getGameProfile()));
	}

	/** The host saved the settings screen; with "show him now", he appears to everyone who can see him, at once. */
	private static void save(ServerPlayer player, HerobrineSettingsPayload payload) {
		if (!mayConfigure(player.createCommandSourceStack())) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		HerobrineSettings settings = payload.settings().savedBy(player.getUUID());
		server.overworld().setAttached(ModAttachments.HEROBRINE, settings);
		if (!payload.showNow()) {
			player.sendSystemMessage(Component.literal("Settings saved."));
			return;
		}
		int shown = 0;
		int audience = 0;
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (settings.shows(other.getUUID()) && !other.isSpectator()) {
				audience++;
				if (appear(other, settings, server.getTickCount(), true)) {
					shown++;
				}
			}
		}
		player.sendSystemMessage(Component.literal(audience == 0 ? "Settings saved. Nobody online can see him (check who sees him)."
				: "Settings saved. Shown to " + shown + " of " + audience + " player" + (audience == 1 ? "" : "s")
						+ (shown < audience ? " (no spot for him near the rest)." : ".")));
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % 20 != 0) {
			return;
		}
		HerobrineSettings settings = settings(server);
		if (!settings.enabled()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (!settings.shows(id) || player.isSpectator()) {
				continue;
			}
			Integer quiet = QUIET_UNTIL.get(id);
			if (quiet == null) {
				QUIET_UNTIL.put(id, now + SETTLE_IN);
				continue;
			}
			// one chance a second: about one sighting every `minutes` minutes of play
			if (now >= quiet && conditionsHold(settings, player) && player.getRandom().nextInt(settings.minutes() * 60) == 0) {
				appear(player, settings, now, false);
			}
		}
	}

	/** The time of day, the weather and the place are ones he comes in. */
	private static boolean conditionsHold(HerobrineSettings settings, ServerPlayer player) {
		ServerLevel level = player.level();
		if (settings.where() == HerobrineSettings.OVERWORLD && level.dimension() != Level.OVERWORLD) {
			return false;
		}
		// Daytime is when the sun burns zombies (the day timeline's monsters_burn); everywhere without a sun, it's night.
		boolean day = level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, player.position());
		if (settings.time() == HerobrineSettings.NIGHT && day || settings.time() == HerobrineSettings.DAY && !day) {
			return false;
		}
		return switch (settings.weather()) {
			case HerobrineSettings.CLEAR -> !level.isRaining();
			case HerobrineSettings.RAIN -> level.isRaining();
			case HerobrineSettings.THUNDER -> level.isThundering();
			default -> true;
		};
	}

	/**
	 * Shows him to one player, far off or right behind them as the settings say ({@code eitherWay}: trying the other kind
	 * of spot if there's no room for the first). False if there's nowhere he could stand.
	 */
	private static boolean appear(ServerPlayer player, HerobrineSettings settings, int now, boolean eitherWay) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		boolean behind = settings.placement() == HerobrineSettings.BEHIND
				|| settings.placement() == HerobrineSettings.FAR_OR_BEHIND && random.nextInt(3) == 0;
		Vec3 feet = behind ? behind(level, player) : far(level, player, settings, random);
		if (feet == null && eitherWay) {
			behind = !behind;
			feet = behind ? behind(level, player) : far(level, player, settings, random);
		}
		if (feet == null) {
			return false;
		}
		float yaw = (float) (Mth.atan2(player.getZ() - feet.z, player.getX() - feet.x) * Mth.RAD_TO_DEG) - 90.0F;
		int linger = settings.linger() * 20;
		ServerPlayNetworking.send(player, new HerobrineSightingPayload(feet.x, feet.y, feet.z, yaw, linger, settings.vanishDistance(),
				behind, settings.stare(), settings.sounds()));
		QUIET_UNTIL.put(player.getUUID(), now + linger + REST);
		return true;
	}

	/**
	 * A spot well beyond where he'd vanish, where the player could see him: on the ground at the surface (on a hill, at
	 * the edge of a wood) or, for a player underground, on a cave floor about level with them. Null if none turns up.
	 */
	@Nullable
	private static Vec3 far(ServerLevel level, ServerPlayer player, HerobrineSettings settings, RandomSource random) {
		Vec3 eye = player.getEyePosition();
		boolean underground = !level.canSeeSky(player.blockPosition());
		double near = settings.vanishDistance() + 8.0;
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = near + random.nextDouble() * (underground ? 12.0 : FAR_RANGE);
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			BlockPos feet = underground ? caveFloor(level, x, player.getBlockY(), z) : surface(level, x, z);
			if (feet != null && clearView(level, eye, Vec3.atBottomCenterOf(feet).add(0.0, 1.6, 0.0))) {
				return Vec3.atBottomCenterOf(feet);
			}
		}
		return null;
	}

	/** A spot a few blocks straight behind the player (behind their back, wherever they look). */
	@Nullable
	private static Vec3 behind(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 back = new Vec3(-look.x, 0.0, -look.z);
		back = back.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : back.normalize();
		Vec3 eye = player.getEyePosition();
		for (double distance : BEHIND_DISTANCES) {
			Vec3 at = player.position().add(back.scale(distance));
			for (int dy = 1; dy >= -2; dy--) {
				BlockPos feet = BlockPos.containing(at.x, player.getY() + dy, at.z);
				if (standable(level, feet) && clearView(level, eye, Vec3.atBottomCenterOf(feet).add(0.0, 1.6, 0.0))) {
					return Vec3.atBottomCenterOf(feet);
				}
			}
		}
		return null;
	}

	@Nullable
	private static BlockPos surface(ServerLevel level, int x, int z) {
		if (!level.isLoaded(new BlockPos(x, 0, z))) {
			return null;
		}
		BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		return standable(level, feet) ? feet : null;
	}

	@Nullable
	private static BlockPos caveFloor(ServerLevel level, int x, int y, int z) {
		for (int dy = 4; dy >= -8; dy--) {
			BlockPos feet = new BlockPos(x, y + dy, z);
			if (standable(level, feet)) {
				return feet;
			}
		}
		return null;
	}

	/** Solid, dry ground to stand on and two open blocks of air (or plants) to stand in. */
	private static boolean standable(ServerLevel level, BlockPos feet) {
		BlockPos ground = feet.below();
		if (!level.isLoaded(feet)) {
			return false;
		}
		BlockState below = level.getBlockState(ground);
		return below.isFaceSturdy(level, ground, Direction.UP) && below.getFluidState().isEmpty() && open(level, feet) && open(level, feet.above());
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	/** Whether nothing solid stands between {@code from} and {@code to} (a step every 0.4 blocks along the way). */
	private static boolean clearView(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 way = to.subtract(from);
		int steps = Math.max(1, Mth.ceil(way.length() / 0.4));
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int i = 1; i < steps; i++) {
			Vec3 at = from.add(way.scale((double) i / steps));
			cursor.set(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
			if (!level.isLoaded(cursor) || !level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
