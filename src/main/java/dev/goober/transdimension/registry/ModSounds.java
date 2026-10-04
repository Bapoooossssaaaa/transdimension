package dev.goober.transdimension.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

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
