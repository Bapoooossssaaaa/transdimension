package dev.goober.transdimension.herobrine;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * How Herobrine behaves in this world (a Fabric data attachment on the Overworld, set from the secret settings screen).
 * The defaults keep him rare and hard to be sure of: about every 20 minutes, far off at the edge of sight, always half
 * behind something, gone after two seconds on screen, no glowing eyes, and about every 15 minutes a sound behind you with
 * nothing there.
 *
 * @param enabled        whether he appears at all (off until the host turns him on)
 * @param host           whoever last saved the settings: "only me" means them, "other players" everyone else
 * @param audience       who sees him: {@link #OTHERS}, {@link #ONLY_HOST} or {@link #EVERYONE}
 * @param placement      where he stands: {@link #FAR} off, {@link #BEHIND} you, or either ({@link #FAR_OR_BEHIND})
 * @param distance       how far off: {@link #NEAR} (30 to 50 blocks), {@link #FARTHER} (50 to 90) or {@link #EDGE} (as far as
 *                       they can see)
 * @param cover          whether a far sighting always stands half hidden behind something (a tree, a hill, a corner)
 * @param time           {@link #ANY_TIME}, only at {@link #NIGHT} or only by {@link #DAY}
 * @param weather        {@link #ANY_WEATHER}, only under {@link #CLEAR} skies, in {@link #RAIN} or in a {@link #THUNDER}storm
 * @param where          only in the {@link #OVERWORLD}, or {@link #ANYWHERE}
 * @param minutes        about how many minutes of play between sightings, for each player who can see him (0: never, just
 *                       the sounds)
 * @param linger         how many seconds he stays at most
 * @param vanishDistance how close (in blocks) anyone can get before he's gone (walking towards him at all does it too)
 * @param seenSeconds    how many seconds he can be on someone's screen before he's gone (0: he isn't, for that)
 * @param eyes           whether his eyes glow
 * @param sounds         whether a cave sound plays where he stood when he goes
 * @param soundMinutes   about how many minutes between sounds behind the player with nothing there (0: none)
 */
public record HerobrineSettings(boolean enabled, Optional<UUID> host, int audience, int placement, int distance, boolean cover, int time,
		int weather, int where, int minutes, int linger, int vanishDistance, int seenSeconds, boolean eyes, boolean sounds,
		int soundMinutes) {
	public static final int OTHERS = 0;
	public static final int ONLY_HOST = 1;
	public static final int EVERYONE = 2;
	public static final int FAR = 0;
	public static final int BEHIND = 1;
	public static final int FAR_OR_BEHIND = 2;
	public static final int NEAR = 0;
	public static final int FARTHER = 1;
	public static final int EDGE = 2;
	public static final int ANY_TIME = 0;
	public static final int NIGHT = 1;
	public static final int DAY = 2;
	public static final int ANY_WEATHER = 0;
	public static final int CLEAR = 1;
	public static final int RAIN = 2;
	public static final int THUNDER = 3;
	public static final int OVERWORLD = 0;
	public static final int ANYWHERE = 1;

	public static final HerobrineSettings DEFAULT = new HerobrineSettings(false, Optional.empty(), OTHERS, FAR, EDGE, true, ANY_TIME,
			ANY_WEATHER, OVERWORLD, 20, 30, 24, 2, false, true, 15);

	public static final Codec<HerobrineSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("enabled", false).forGetter(HerobrineSettings::enabled),
			UUIDUtil.CODEC.optionalFieldOf("host").forGetter(HerobrineSettings::host),
			Codec.INT.optionalFieldOf("audience", OTHERS).forGetter(HerobrineSettings::audience),
			Codec.INT.optionalFieldOf("placement", FAR).forGetter(HerobrineSettings::placement),
			Codec.INT.optionalFieldOf("distance", EDGE).forGetter(HerobrineSettings::distance),
			Codec.BOOL.optionalFieldOf("cover", true).forGetter(HerobrineSettings::cover),
			Codec.INT.optionalFieldOf("time", ANY_TIME).forGetter(HerobrineSettings::time),
			Codec.INT.optionalFieldOf("weather", ANY_WEATHER).forGetter(HerobrineSettings::weather),
			Codec.INT.optionalFieldOf("where", OVERWORLD).forGetter(HerobrineSettings::where),
			Codec.INT.optionalFieldOf("minutes", 20).forGetter(HerobrineSettings::minutes),
			Codec.INT.optionalFieldOf("linger", 30).forGetter(HerobrineSettings::linger),
			Codec.INT.optionalFieldOf("vanish_distance", 24).forGetter(HerobrineSettings::vanishDistance),
			Codec.INT.optionalFieldOf("seen_seconds", 2).forGetter(HerobrineSettings::seenSeconds),
			Codec.BOOL.optionalFieldOf("eyes", false).forGetter(HerobrineSettings::eyes),
			Codec.BOOL.optionalFieldOf("sounds", true).forGetter(HerobrineSettings::sounds),
			Codec.INT.optionalFieldOf("sound_minutes", 15).forGetter(HerobrineSettings::soundMinutes)
	).apply(instance, HerobrineSettings::new));

	/** The settings as the screen edits them (the host stays on the server: it's whoever saves them). */
	public static final StreamCodec<RegistryFriendlyByteBuf, HerobrineSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeBoolean(settings.enabled());
				buf.writeVarInt(settings.audience());
				buf.writeVarInt(settings.placement());
				buf.writeVarInt(settings.distance());
				buf.writeBoolean(settings.cover());
				buf.writeVarInt(settings.time());
				buf.writeVarInt(settings.weather());
				buf.writeVarInt(settings.where());
				buf.writeVarInt(settings.minutes());
				buf.writeVarInt(settings.linger());
				buf.writeVarInt(settings.vanishDistance());
				buf.writeVarInt(settings.seenSeconds());
				buf.writeBoolean(settings.eyes());
				buf.writeBoolean(settings.sounds());
				buf.writeVarInt(settings.soundMinutes());
			},
			buf -> new HerobrineSettings(buf.readBoolean(), Optional.empty(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));

	/** Whether this player is one who sees him (and hears the sounds). */
	public boolean shows(UUID player) {
		boolean isHost = this.host.map(player::equals).orElse(false);
		return switch (this.audience) {
			case ONLY_HOST -> isHost;
			case EVERYONE -> true;
			default -> !isHost;
		};
	}

	/** These settings, saved by {@code player} (who becomes "me" in "only me"), with every value kept in range. */
	public HerobrineSettings savedBy(UUID player) {
		return new HerobrineSettings(this.enabled, Optional.of(player), Mth.clamp(this.audience, OTHERS, EVERYONE),
				Mth.clamp(this.placement, FAR, FAR_OR_BEHIND), Mth.clamp(this.distance, NEAR, EDGE), this.cover,
				Mth.clamp(this.time, ANY_TIME, DAY), Mth.clamp(this.weather, ANY_WEATHER, THUNDER), Mth.clamp(this.where, OVERWORLD, ANYWHERE),
				Mth.clamp(this.minutes, 0, 120), Mth.clamp(this.linger, 2, 300), Mth.clamp(this.vanishDistance, 4, 96),
				Mth.clamp(this.seenSeconds, 0, 30), this.eyes, this.sounds, Mth.clamp(this.soundMinutes, 0, 120));
	}
}
