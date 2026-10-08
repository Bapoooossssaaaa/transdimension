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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.goober.transdimension.registry.ModAttachments;

/**
 * Herobrine: the host's secret. Now and then, for the players the host picks, he stands far off at the edge of what they
 * can see, half hidden behind a hill, a tree or a corner, watching; or right behind them. He's never suddenly there in
 * front of them, and he's gone once they've had a glimpse of him, looked away, or come closer. And now and then they hear
 * something behind them with nothing there: footsteps on whatever the ground is made of, a creeper's hiss, a door, someone
 * mining in the rock nearby.
 *
 * <p>He is never an entity. The server only tells each chosen player's client, on its own, where he stands
 * ({@link HerobrineSightingPayload}) or what to play ({@link HerobrineSoundPayload}), and that client draws him or plays
 * the sound (HerobrineClient). So nothing that lists entities can find him: not minimap radars like Xaero's, not F3's
 * entity count, not hitboxes, not the player list. Other players (and the host, unless they choose to see him too) are
 * never told anything, and never hear a thing.
 *
 * <p>The host sets him up in a secret screen opened with {@code /tdsettings} ({@link #COMMAND}), which only the world's
 * host (in singleplayer, or whoever opened it to LAN) or an operator can see or use. The settings live with the world
 * ({@link HerobrineSettings}, on the Overworld) and he stays away until they're saved with him turned on. Each player who
 * can see him gets a chance at each every second, so a sighting or a sound comes about every few minutes of play as the
 * settings say, but never in their first minute online, no sighting within half a minute of the last one ending, and no
 * sound within a minute of the last.
 */
public final class Herobrine {
	/** The secret command that opens the settings. */
	public static final String COMMAND = "tdsettings";
	/** Nothing in a player's first minute online, no sighting within half a minute of the last, no sound within a minute. */
	private static final int SETTLE_IN = 20 * 60;
	private static final int REST = 20 * 30;
	private static final int SOUND_REST = 20 * 60;
	/** How many spots to try for a far-off sighting. */
	private static final int TRIES = 64;
	/** "As far as they can see": this much of the way to the edge of what the player sees, and in blocks, at least and at most. */
	private static final double EDGE_FROM = 0.6;
	private static final double EDGE_TO = 0.85;
	private static final double EDGE_LEAST = 48.0;
	private static final double EDGE_MOST = 200.0;
	/** Underground, as far as caves let anyone see. */
	private static final double CAVE_FROM = 16.0;
	private static final double CAVE_TO = 40.0;
	/** His face (this high above his feet), which the player must be able to see... */
	private static final double FACE = 1.75;
	/** ...and points down his body (how high, and how far to his side), of which at least {@link #HIDDEN} are behind something. */
	private static final double[][] BODY = {{1.35, 0.3}, {1.35, -0.3}, {0.9, 0.15}, {0.9, -0.15}, {0.35, 0.12}, {0.35, -0.12}};
	private static final int HIDDEN = 3;
	/** How far behind a player he stands, the likeliest spots first. */
	private static final double[] BEHIND_DISTANCES = {4.0, 5.0, 3.0, 6.0};
	private static final String[] COMPASS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
	/** No sighting before this server tick, by player; and no sound. */
	private static final Map<UUID, Integer> QUIET_UNTIL = new HashMap<>();
	private static final Map<UUID, Integer> HUSHED_UNTIL = new HashMap<>();

	private Herobrine() {
	}

	public static void initialize() {
		PayloadTypeRegistry.clientboundPlay().register(HerobrineSightingPayload.TYPE, HerobrineSightingPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HerobrineSoundPayload.TYPE, HerobrineSoundPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HerobrineMenuPayload.TYPE, HerobrineMenuPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HerobrineSettingsPayload.TYPE, HerobrineSettingsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HerobrineSettingsPayload.TYPE, (payload, context) -> save(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(Herobrine::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			QUIET_UNTIL.remove(handler.player.getUUID());
			HUSHED_UNTIL.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			QUIET_UNTIL.clear();
			HUSHED_UNTIL.clear();
		});
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

	/**
	 * The host saved the settings screen; to try them out, he can also appear at once to everyone who can see him, or a
	 * sound play for each of them. The host is told how it went (and, if they see him too, roughly where he is).
	 */
	private static void save(ServerPlayer player, HerobrineSettingsPayload payload) {
		if (!mayConfigure(player.createCommandSourceStack())) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		HerobrineSettings settings = payload.settings().savedBy(player.getUUID());
		server.overworld().setAttached(ModAttachments.HEROBRINE, settings);
		if (payload.action() != HerobrineSettingsPayload.SHOW && payload.action() != HerobrineSettingsPayload.SOUND) {
			player.sendSystemMessage(Component.literal("Settings saved."));
			return;
		}
		boolean show = payload.action() == HerobrineSettingsPayload.SHOW;
		int done = 0;
		int audience = 0;
		String yours = "";
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (!settings.shows(other.getUUID()) || other.isSpectator()) {
				continue;
			}
			audience++;
			if (!show) {
				prank(other);
				done++;
				continue;
			}
			Vec3 at = appear(other, settings, server.getTickCount(), true);
			if (at != null) {
				done++;
				if (other == player) {
					double distance = at.distanceTo(player.position());
					yours = distance < 8.0 ? " For you: right behind you."
							: " For you: about " + Math.round(distance) + " blocks to the " + compass(player.position(), at) + ".";
				}
			}
		}
		String players = audience == 1 ? " player" : " players";
		player.sendSystemMessage(Component.literal(audience == 0 ? "Settings saved. Nobody online can see him (check who sees him)."
				: !show ? "Settings saved. Played a sound for " + done + " of " + audience + players + "."
				: "Settings saved. Shown to " + done + " of " + audience + players
						+ (done < audience ? " (no spot for him near the rest)." : ".") + yours));
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
				HUSHED_UNTIL.put(id, now + SETTLE_IN);
				continue;
			}
			if (!conditionsHold(settings, player)) {
				continue;
			}
			// one chance a second at each: about one sighting every `minutes` minutes of play, and a sound every `soundMinutes`
			RandomSource random = player.getRandom();
			if (settings.minutes() > 0 && now >= quiet && random.nextInt(settings.minutes() * 60) == 0) {
				appear(player, settings, now, false);
			} else if (settings.soundMinutes() > 0 && now >= HUSHED_UNTIL.getOrDefault(id, 0)
					&& random.nextInt(settings.soundMinutes() * 60) == 0) {
				prank(player);
				HUSHED_UNTIL.put(id, now + SOUND_REST);
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
	 * of spot if there's no room for the first). Where his feet are, or null if there's nowhere he could stand.
	 */
	@Nullable
	private static Vec3 appear(ServerPlayer player, HerobrineSettings settings, int now, boolean eitherWay) {
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
			return null;
		}
		float yaw = (float) (Mth.atan2(player.getZ() - feet.z, player.getX() - feet.x) * Mth.RAD_TO_DEG) - 90.0F;
		int linger = settings.linger() * 20;
		ServerPlayNetworking.send(player, new HerobrineSightingPayload(feet.x, feet.y, feet.z, yaw, linger, settings.vanishDistance(),
				behind, settings.seenSeconds() * 20, settings.eyes(), settings.sounds()));
		QUIET_UNTIL.put(player.getUUID(), now + linger + REST);
		return feet;
	}

	/**
	 * A spot as far off as the settings say where the player could only just see him: his face in plain view, but at
	 * least half his body behind something (the crest of a hill, a tree, a wall, a corner), unless the host turned that
	 * off. It's off to one side of where they're looking, or behind them, so he isn't suddenly there in front of them
	 * (HerobrineClient also waits until he's off their screen). He stands on the ground at the surface or, for a player
	 * underground, on a cave floor about level with them. Null if no such spot turns up.
	 */
	@Nullable
	private static Vec3 far(ServerLevel level, ServerPlayer player, HerobrineSettings settings, RandomSource random) {
		Vec3 eye = player.getEyePosition();
		boolean underground = underground(level, player);
		double nearest = settings.vanishDistance() + 8.0;
		double from;
		double to;
		if (underground) {
			from = Math.max(CAVE_FROM, nearest);
			to = Math.max(CAVE_TO, from);
		} else {
			double sight = sight(player);
			if (settings.distance() == HerobrineSettings.NEAR) {
				from = 30.0;
				to = 50.0;
			} else if (settings.distance() == HerobrineSettings.FARTHER) {
				from = 50.0;
				to = 90.0;
			} else {
				from = Mth.clamp(sight * EDGE_FROM, EDGE_LEAST, EDGE_MOST);
				to = Mth.clamp(sight * EDGE_TO, EDGE_LEAST, EDGE_MOST);
			}
			// never beyond what they can see, and never so near he'd be gone at once
			to = Math.max(Math.min(to, sight * EDGE_TO), nearest);
			from = Mth.clamp(from, nearest, to);
		}
		for (int attempt = 0; attempt < TRIES; attempt++) {
			float turn = (50.0F + random.nextFloat() * 130.0F) * (random.nextBoolean() ? 1.0F : -1.0F);
			float yaw = (player.getYRot() + turn) * Mth.DEG_TO_RAD;
			double distance = from + random.nextDouble() * (to - from);
			int x = Mth.floor(player.getX() - Mth.sin(yaw) * distance);
			int z = Mth.floor(player.getZ() + Mth.cos(yaw) * distance);
			BlockPos feet = underground ? caveFloor(level, x, player.getBlockY(), z) : surface(level, x, z);
			if (feet != null && glimpsed(level, player, eye, Vec3.atBottomCenterOf(feet), settings.cover())) {
				return Vec3.atBottomCenterOf(feet);
			}
		}
		return null;
	}

	/** How far (in blocks) this player can see: the nearer of their render distance and the server's view distance. */
	private static double sight(ServerPlayer player) {
		int chunks = player.level().getServer().getPlayerList().getViewDistance();
		int theirs = player.clientInformation().viewDistance();
		if (theirs > 0) {
			chunks = Math.min(chunks, theirs);
		}
		return 16.0 * Math.max(2, chunks);
	}

	/** Well under the ground (or anywhere under the Nether's roof): six or more blocks below the surface where they are. */
	private static boolean underground(ServerLevel level, ServerPlayer player) {
		return player.getBlockY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ()) - 6;
	}

	/**
	 * Whether the player could just about see him standing here: his face in plain view from their eyes and, with
	 * {@code cover}, at least {@link #HIDDEN} of the points down his body hidden behind something.
	 */
	private static boolean glimpsed(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 feet, boolean cover) {
		if (!loadedAlong(level, eye, feet) || !sees(level, player, eye, feet.add(0.0, FACE, 0.0))) {
			return false;
		}
		if (!cover) {
			return true;
		}
		// level, and across the player's line of sight: his left and right as they see him
		Vec3 across = new Vec3(eye.z - feet.z, 0.0, feet.x - eye.x);
		across = across.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : across.normalize();
		int hidden = 0;
		for (int i = 0; i < BODY.length; i++) {
			Vec3 point = feet.add(across.scale(BODY[i][1])).add(0.0, BODY[i][0], 0.0);
			if (!sees(level, player, eye, point) && ++hidden >= HIDDEN) {
				return true;
			}
			if (hidden + BODY.length - 1 - i < HIDDEN) {
				return false;
			}
		}
		return false;
	}

	/** Whether nothing that blocks the view (anything solid: not glass, plants or water) stands between the two points. */
	private static boolean sees(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
	}

	/** Every chunk on the way is loaded (so looking along it never loads one). */
	private static boolean loadedAlong(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 way = to.subtract(from);
		int steps = Math.max(1, Mth.ceil(way.horizontalDistance() / 8.0));
		for (int i = 0; i <= steps; i++) {
			Vec3 at = from.add(way.scale((double) i / steps));
			if (!level.hasChunk(Mth.floor(at.x) >> 4, Mth.floor(at.z) >> 4)) {
				return false;
			}
		}
		return true;
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
				if (standable(level, feet) && sees(level, player, eye, Vec3.atBottomCenterOf(feet).add(0.0, FACE, 0.0))) {
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

	/**
	 * A sound behind the player with nothing there ({@link HerobrineSoundPayload}; their client picks the spot and the
	 * block): mostly footsteps, now and then a creeper or a door, and underground someone mining too.
	 */
	private static void prank(ServerPlayer player) {
		int roll = player.getRandom().nextInt(10);
		int kind;
		if (underground(player.level(), player)) {
			kind = roll < 4 ? HerobrineSoundPayload.FOOTSTEPS : roll < 7 ? HerobrineSoundPayload.MINING
					: roll < 9 ? HerobrineSoundPayload.CREEPER : HerobrineSoundPayload.DOOR;
		} else {
			kind = roll < 6 ? HerobrineSoundPayload.FOOTSTEPS : roll < 8 ? HerobrineSoundPayload.CREEPER : HerobrineSoundPayload.DOOR;
		}
		ServerPlayNetworking.send(player, new HerobrineSoundPayload(kind));
	}

	/** Which way {@code to} lies from {@code from}, as a compass point. */
	private static String compass(Vec3 from, Vec3 to) {
		double degrees = Math.toDegrees(Math.atan2(to.x - from.x, from.z - to.z));
		return COMPASS[Math.floorMod((int) Math.round(degrees / 45.0), COMPASS.length)];
	}
}
