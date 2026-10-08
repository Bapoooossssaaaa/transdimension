package dev.goober.transdimension.item;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/**
 * The Holy Bow, from the Cloud Realm's heavenly ruins and ruined churches: holy gold, so it never breaks (ModItems#holy),
 * and every shot is two arrows flying side by side for the one arrow it uses. The second is like a crossbow's Multishot
 * arrows: it can't be picked up. Everything else (drawing, Power, Flame, Punch, Infinity) is the vanilla bow's.
 */
public class HolyBowItem extends BowItem {
	/** How far (in degrees) each arrow turns from where the bow points, one to either side. */
	private static final float SPREAD = 1.5F;

	public HolyBowItem(Properties properties) {
		super(properties);
	}

	/** Each arrow drawn is shot twice: the arrow itself and a twin that can't be picked up. */
	@Override
	protected void shoot(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack weapon, List<ItemStack> projectiles,
			float power, float uncertainty, boolean isCrit, @Nullable LivingEntity target) {
		List<ItemStack> twice = new ArrayList<>(projectiles.size() * 2);
		for (ItemStack projectile : projectiles) {
			twice.add(projectile);
			ItemStack twin = projectile.copy();
			twin.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
			twice.add(twin);
		}
		super.shoot(level, shooter, hand, weapon, twice, power, uncertainty, isCrit, target);
	}

	/** The pair fly a little apart, one to either side of where the bow points. */
	@Override
	protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float power, float uncertainty, float angle,
			@Nullable LivingEntity target) {
		super.shootProjectile(shooter, projectile, index, power, uncertainty, angle + (index % 2 == 0 ? -SPREAD : SPREAD), target);
	}
}
