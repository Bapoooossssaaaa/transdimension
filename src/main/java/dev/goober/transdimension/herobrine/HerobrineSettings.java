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
 *
 * @param enabled        whether he appears at all (off until the host turns him on)
 * @param host           whoever last saved the settings: "only me" means them, "other players" everyone else
 * @param audience       who sees him: {@link #OTHERS}, {@link #ONLY_HOST} or {@link #EVERYONE}
 * @param placement      where he stands: {@link #FAR} off, {@link #BEHIND} you, or either ({@link #FAR_OR_BEHIND})
 * @param time           {@link #ANY_TIME}, only at {@link #NIGHT} or only by {@link #DAY}
 * @param weather        {@link #ANY_WEATHER}, only under {@link #CLEAR} skies, in {@link #RAIN} or in a {@link #THUNDER}storm
 * @param where          only in the {@link #OVERWORLD}, or {@link #ANYWHERE}
 * @param minutes        about how many minutes of play between sightings, for each player who can see him
 * @param linger         how many seconds he stays if nobody comes close
 * @param vanishDistance how close (in blocks) anyone can get before he's gone
 * @param stare          whether he also goes when stared at for a moment
 * @param sounds         whether a cave sound plays where he stood when he goes
 */
public record HerobrineSettings(boolean enabled, Optional<UUID> host, int audience, int placement, int time, int weather, int where,
		int minutes, int linger, int vanishDistance, boolean stare, boolean sounds) {
	public static final int OTHERS = 0;
	public static final int ONLY_HOST = 1;
	public static final int EVERYONE = 2;
	public static final int FAR = 0;
	public static final int BEHIND = 1;
	public static final int FAR_OR_BEHIND = 2;
	public static final int ANY_TIME = 0;
	public static final int NIGHT = 1;
	public static final int DAY = 2;
	public static final int ANY_WEATHER = 0;
	public static final int CLEAR = 1;
	public static final int RAIN = 2;
	public static final int THUNDER = 3;
	public static final int OVERWORLD = 0;
	public static final int ANYWHERE = 1;

	public static final HerobrineSettings DEFAULT = new HerobrineSettings(false, Optional.empty(), OTHERS, FAR, ANY_TIME, ANY_WEATHER,
			OVERWORLD, 10, 20, 16, true, true);

	public static final Codec<HerobrineSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("enabled", false).forGetter(HerobrineSettings::enabled),
			UUIDUtil.CODEC.optionalFieldOf("host").forGetter(HerobrineSettings::host),
			Codec.INT.optionalFieldOf("audience", OTHERS).forGetter(HerobrineSettings::audience),
			Codec.INT.optionalFieldOf("placement", FAR).forGetter(HerobrineSettings::placement),
			Codec.INT.optionalFieldOf("time", ANY_TIME).forGetter(HerobrineSettings::time),
			Codec.INT.optionalFieldOf("weather", ANY_WEATHER).forGetter(HerobrineSettings::weather),
			Codec.INT.optionalFieldOf("where", OVERWORLD).forGetter(HerobrineSettings::where),
			Codec.INT.optionalFieldOf("minutes", 10).forGetter(HerobrineSettings::minutes),
			Codec.INT.optionalFieldOf("linger", 20).forGetter(HerobrineSettings::linger),
			Codec.INT.optionalFieldOf("vanish_distance", 16).forGetter(HerobrineSettings::vanishDistance),
			Codec.BOOL.optionalFieldOf("stare", true).forGetter(HerobrineSettings::stare),
			Codec.BOOL.optionalFieldOf("sounds", true).forGetter(HerobrineSettings::sounds)
	).apply(instance, HerobrineSettings::new));

	/** The settings as the screen edits them (the host stays on the server: it's whoever saves them). */
	public static final StreamCodec<RegistryFriendlyByteBuf, HerobrineSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeBoolean(settings.enabled());
				buf.writeVarInt(settings.audience());
				buf.writeVarInt(settings.placement());
				buf.writeVarInt(settings.time());
				buf.writeVarInt(settings.weather());
				buf.writeVarInt(settings.where());
				buf.writeVarInt(settings.minutes());
				buf.writeVarInt(settings.linger());
				buf.writeVarInt(settings.vanishDistance());
				buf.writeBoolean(settings.stare());
				buf.writeBoolean(settings.sounds());
			},
			buf -> new HerobrineSettings(buf.readBoolean(), Optional.empty(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
					buf.readBoolean()));

	/** Whether this player is one who sees him. */
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
				Mth.clamp(this.placement, FAR, FAR_OR_BEHIND), Mth.clamp(this.time, ANY_TIME, DAY), Mth.clamp(this.weather, ANY_WEATHER, THUNDER),
				Mth.clamp(this.where, OVERWORLD, ANYWHERE), Mth.clamp(this.minutes, 1, 120), Mth.clamp(this.linger, 2, 300),
				Mth.clamp(this.vanishDistance, 4, 64), this.stare, this.sounds);
	}
}
