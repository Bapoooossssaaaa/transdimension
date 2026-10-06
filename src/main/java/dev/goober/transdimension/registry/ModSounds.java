package dev.goober.transdimension.registry;

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

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = TransDimension.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
