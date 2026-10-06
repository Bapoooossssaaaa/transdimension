package dev.goober.transdimension.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;

import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModVillagers;

/**
 * A sculk person of the pink deep dark: a quiet, humanoid trader grown of pink sculk. It lives like a villager (it is
 * one underneath, so it wanders, gossips, panics and trades like one) but it always has the sculk person profession,
 * whose trades (data/transdimension/trade_set/sculk_person/) sell end-game treasure for sculk gems.
 *
 * <p>A villager without a job site forgets an untraded profession, so a sculk person starts with a little trading
 * experience; and since there's no job site to restock at, it restocks on its own twice a day.
 */
public class SculkPerson extends Villager {
	private static final int RESTOCK_TICKS = 12000;

	public SculkPerson(EntityType<? extends Villager> entityType, Level level) {
		super(entityType, level);
	}

	/** Natural spawns: on solid ground, and no more than three in one place. */
	public static boolean checkSculkPersonSpawnRules(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		return Mob.checkMobSpawnRules(type, level, reason, pos, random)
				&& level.getEntitiesOfClass(SculkPerson.class, new AABB(pos).inflate(24.0)).size() < 3;
	}

	@Override
	public void tick() {
		super.tick();
		// Spawned by a structure, an egg or the biome: take up the profession straight away (and keep it).
		if (!this.level().isClientSide() && (this.tickCount == 1 || this.tickCount % 100 == 0)) {
			this.becomeSculkPerson();
		}
	}

	/** Gives it the sculk person profession, with a little experience so it never loses it for want of a job site. */
	public void becomeSculkPerson() {
		if (!this.getVillagerData().profession().is(ModVillagers.SCULK_PERSON)) {
			Holder<VillagerProfession> profession = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(ModVillagers.SCULK_PERSON);
			this.setVillagerData(this.getVillagerData().withProfession(profession));
		}
		if (this.getVillagerXp() == 0) {
			this.setVillagerXp(1);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % RESTOCK_TICKS == 0 && !this.isTrading()) {
			for (MerchantOffer offer : this.getOffers()) {
				offer.resetUses();
			}
		}
	}

	@Override
	@Nullable
	public SculkPerson getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return ModEntities.SCULK_PERSON.create(level, EntitySpawnReason.BREEDING);
	}

	/** A lower, hollow voice than a villager's. */
	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.7F;
	}
}
