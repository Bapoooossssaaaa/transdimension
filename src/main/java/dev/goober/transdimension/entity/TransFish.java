package dev.goober.transdimension.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import dev.goober.transdimension.registry.ModItems;

/**
 * The trans fish: a little schooling fish striped like the flag (blue, pink, white, pink, blue). It swims in schools
 * in the realm's seas and rivers, flops on land like a cod, and can be caught in a bucket.
 */
public class TransFish extends Cod {
	public TransFish(EntityType<? extends Cod> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public ItemStack getBucketItemStack() {
		return new ItemStack(ModItems.TRANS_FISH_BUCKET);
	}
}
