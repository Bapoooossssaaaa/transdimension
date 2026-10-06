package dev.goober.transdimension.registry;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;

import dev.goober.transdimension.TransDimension;

/**
 * The mod's own sound events. They are defined in {@code assets/transdimension/sounds.json} and mostly
 * point at vanilla sound files (pitched and mixed), so no audio has to be shipped and nothing depends on
 * vanilla's Java sound constants, which move around between versions.
 */
public final class ModSounds {
	public static final SoundEvent SILLY_CAT_AMBIENT = register("entity.silly_cat.ambient");
	public static final SoundEvent SILLY_CAT_PURR = register("entity.silly_cat.purr");
	public static final SoundEvent SILLY_CAT_LICK = register("entity.silly_cat.lick");
	public static final SoundEvent SILLY_CAT_HURT = register("entity.silly_cat.hurt");
	public static final SoundEvent SILLY_CAT_DEATH = register("entity.silly_cat.death");
	public static final SoundEvent PRIDE_OVEN_CRACKLE = register("block.pride_oven.crackle");
	public static final SoundEvent INTRO_BELL = register("ui.intro.bell");
	public static final SoundEvent INTRO_CHIME = register("ui.intro.chime");
	public static final SoundEvent INTRO_WHOOSH = register("ui.intro.whoosh");

	// The pink sculk family sounds soft and sparkly rather than creepy (assets/transdimension/sounds.json).
	public static final SoundEvent PINK_SCULK_BREAK = register("block.pink_sculk.break");
	public static final SoundEvent PINK_SCULK_STEP = register("block.pink_sculk.step");
	public static final SoundEvent PINK_SCULK_PLACE = register("block.pink_sculk.place");
	public static final SoundEvent PINK_SCULK_HIT = register("block.pink_sculk.hit");
	public static final SoundEvent PINK_SCULK_FALL = register("block.pink_sculk.fall");
	/** A pink sculk sensor chimes where vanilla's clicks (SculkSensorBlockMixin)... */
	public static final SoundEvent PINK_SCULK_SENSOR_CLICKING = register("block.pink_sculk_sensor.clicking");
	public static final SoundEvent PINK_SCULK_SENSOR_CLICKING_STOP = register("block.pink_sculk_sensor.clicking_stop");
	/** ...a pink shrieker sings where vanilla's shrieks (SculkShriekerBlockEntityMixin)... */
	public static final SoundEvent PINK_SCULK_SHRIEKER_SHRIEK = register("block.pink_sculk_shrieker.shriek");
	/** ...and a pink catalyst rings as it blooms. */
	public static final SoundEvent PINK_SCULK_CATALYST_BLOOM = register("block.pink_sculk_catalyst.bloom");
	/** How every pink sculk block sounds to walk on, break and place: soft moss, now and then a crystal tinkle. */
	public static final SoundType PINK_SCULK = new SoundType(1.0F, 1.0F, PINK_SCULK_BREAK, PINK_SCULK_STEP, PINK_SCULK_PLACE,
			PINK_SCULK_HIT, PINK_SCULK_FALL);

	// The pink warden's voice: gentle chimes and moss where a warden roars, sniffs, booms and stomps (PinkWarden#playSound).
	public static final SoundEvent PINK_WARDEN_AMBIENT = register("entity.pink_warden.ambient");
	public static final SoundEvent PINK_WARDEN_HURT = register("entity.pink_warden.hurt");
	public static final SoundEvent PINK_WARDEN_DEATH = register("entity.pink_warden.death");
	public static final SoundEvent PINK_WARDEN_STEP = register("entity.pink_warden.step");
	public static final SoundEvent PINK_WARDEN_ROAR = register("entity.pink_warden.roar");
	public static final SoundEvent PINK_WARDEN_SNIFF = register("entity.pink_warden.sniff");
	public static final SoundEvent PINK_WARDEN_SONIC_CHARGE = register("entity.pink_warden.sonic_charge");
	public static final SoundEvent PINK_WARDEN_SONIC_BOOM = register("entity.pink_warden.sonic_boom");
	public static final SoundEvent PINK_WARDEN_ATTACK = register("entity.pink_warden.attack");
	public static final SoundEvent PINK_WARDEN_DIG = register("entity.pink_warden.dig");
	public static final SoundEvent PINK_WARDEN_EMERGE = register("entity.pink_warden.emerge");
	public static final SoundEvent PINK_WARDEN_LISTENING = register("entity.pink_warden.listening");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = TransDimension.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/**
	 * The soft sound that stands in for one of vanilla's sculk or warden sounds, or null for any other sound. Used by the
	 * pink warden for everything it says, and by the client for any sculk or warden sound heard in the Trans Realm
	 * (ClientPacketListenerMixin), where every sculk block and warden is pink.
	 */
	public static @Nullable SoundEvent soften(SoundEvent sound) {
		String path = sound.location().getPath();
		if (path.startsWith("entity.warden.")) {
			String what = path.substring("entity.warden.".length());
			if (what.contains("roar")) {
				return PINK_WARDEN_ROAR;
			} else if (what.contains("sniff")) {
				return PINK_WARDEN_SNIFF;
			} else if (what.contains("sonic_charge")) {
				return PINK_WARDEN_SONIC_CHARGE;
			} else if (what.contains("sonic_boom")) {
				return PINK_WARDEN_SONIC_BOOM;
			} else if (what.contains("step")) {
				return PINK_WARDEN_STEP;
			} else if (what.contains("hurt")) {
				return PINK_WARDEN_HURT;
			} else if (what.contains("death")) {
				return PINK_WARDEN_DEATH;
			} else if (what.contains("attack")) {
				return PINK_WARDEN_ATTACK;
			} else if (what.contains("dig")) {
				return PINK_WARDEN_DIG;
			} else if (what.contains("emerge")) {
				return PINK_WARDEN_EMERGE;
			} else if (what.contains("listening")) {
				return PINK_WARDEN_LISTENING;
			}
			return PINK_WARDEN_AMBIENT;
		}
		if (!path.startsWith("block.sculk") && !path.startsWith("block.calibrated_sculk_sensor")) {
			return null;
		}
		if (path.endsWith("clicking_stop")) {
			return PINK_SCULK_SENSOR_CLICKING_STOP;
		} else if (path.endsWith("clicking")) {
			return PINK_SCULK_SENSOR_CLICKING;
		} else if (path.endsWith("shriek")) {
			return PINK_SCULK_SHRIEKER_SHRIEK;
		} else if (path.endsWith("bloom")) {
			return PINK_SCULK_CATALYST_BLOOM;
		} else if (path.endsWith("break")) {
			return PINK_SCULK_BREAK;
		} else if (path.endsWith("place")) {
			return PINK_SCULK_PLACE;
		} else if (path.endsWith("hit")) {
			return PINK_SCULK_HIT;
		} else if (path.endsWith("fall")) {
			return PINK_SCULK_FALL;
		}
		return PINK_SCULK_STEP;
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
