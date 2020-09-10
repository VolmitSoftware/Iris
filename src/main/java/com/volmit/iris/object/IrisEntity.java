package com.volmit.iris.object;

import java.util.Collection;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attributable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.ParallaxTerrainProvider;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.C;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Builder
@AllArgsConstructor
@DontObfuscate
@Desc("Represents an iris entity.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisEntity extends IrisRegistrant
{
	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("The type of entity to spawn")
	private EntityType type = EntityType.PIG;

	@Builder.Default
	@DontObfuscate
	@Desc("The custom name of this entity")
	private String customName = "";

	@Builder.Default
	@DontObfuscate
	@Desc("Should the name on this entity be visible even if you arent looking at it.")
	private boolean customNameVisible = false;

	@Builder.Default
	@DontObfuscate
	@Desc("If this entity type is a mob, should it be aware of it's surroundings & interact with the world.")
	private boolean aware = true;

	@Builder.Default
	@DontObfuscate
	@Desc("If this entity type is a creature, should it have ai goals.")
	private boolean ai = true;

	@Builder.Default
	@DontObfuscate
	@Desc("Should this entity be glowing")
	private boolean glowing = false;

	@Builder.Default
	@DontObfuscate
	@Desc("Should gravity apply to this entity")
	private boolean gravity = true;

	@Builder.Default
	@DontObfuscate
	@Desc("When an entity is invulnerable it can only be damaged by players increative mode.")
	private boolean invulnerable = false;

	@Builder.Default
	@DontObfuscate
	@Desc("When an entity is silent it will not produce any sound.")
	private boolean silent = false;

	@Builder.Default
	@DontObfuscate
	@Desc("Should this entity be allowed to pickup items")
	private boolean pickupItems = false;

	@Builder.Default
	@DontObfuscate
	@Desc("Should this entity be removed when far away")
	private boolean removable = true;

	@Builder.Default
	@DontObfuscate
	@Desc("Entity helmet equipment")
	private IrisLoot helmet = null;

	@Builder.Default
	@DontObfuscate
	@Desc("Entity chestplate equipment")
	private IrisLoot chestplate = null;

	@Builder.Default
	@DontObfuscate
	@Desc("Entity boots equipment")
	private IrisLoot boots = null;

	@Builder.Default
	@DontObfuscate
	@Desc("Entity leggings equipment")
	private IrisLoot leggings = null;

	@Builder.Default
	@DontObfuscate
	@Desc("Entity main hand equipment")
	private IrisLoot mainHand = null;

	@Builder.Default
	@DontObfuscate
	@Desc("Entity off hand equipment")
	private IrisLoot offHand = null;

	@Builder.Default
	@DontObfuscate
	@Desc("Make other entities ride this entity")
	@ArrayType(min = 1, type = IrisEntity.class)
	private KList<IrisEntity> passengers = new KList<>();

	@Builder.Default
	@DontObfuscate
	@Desc("Attribute modifiers for this entity")
	@ArrayType(min = 1, type = IrisAttributeModifier.class)
	private KList<IrisAttributeModifier> attributes = new KList<>();

	@Builder.Default
	@DontObfuscate
	@Desc("Loot tables for drops")
	private IrisLootReference loot = new IrisLootReference();

	@Builder.Default
	@DontObfuscate
	@Desc("If specified, this entity will be leashed by this entity. I.e. THIS ENTITY Leashed by SPECIFIED. This has no effect on EnderDragons, Withers, Players, or Bats.Non-living entities excluding leashes will not persist as leashholders.")
	private IrisEntity leashHolder = null;

	public IrisEntity()
	{

	}

	public Entity spawn(ParallaxTerrainProvider gen, Location at)
	{
		return spawn(gen, at, new RNG(at.hashCode()));
	}

	public Entity spawn(ParallaxTerrainProvider gen, Location at, RNG rng)
	{
		Entity e = at.getWorld().spawnEntity(at, getType());
		e.setCustomName(getCustomName() != null ? C.translateAlternateColorCodes('&', getCustomName()) : null);
		e.setCustomNameVisible(isCustomNameVisible());
		e.setGlowing(isGlowing());
		e.setGravity(isGravity());
		e.setInvulnerable(isInvulnerable());
		e.setSilent(isSilent());

		int gg = 0;
		for(IrisEntity i : passengers)
		{
			e.addPassenger(i.spawn(gen, at, rng.nextParallelRNG(234858 + gg++)));
		}

		if(e instanceof Attributable)
		{
			Attributable a = (Attributable) e;

			for(IrisAttributeModifier i : getAttributes())
			{
				i.apply(rng, a);
			}
		}

		if(e instanceof Lootable)
		{
			Lootable l = (Lootable) e;

			if(getLoot().getTables().isNotEmpty())
			{
				l.setLootTable(new LootTable()
				{
					@Override
					public NamespacedKey getKey()
					{
						return new NamespacedKey(Iris.instance, "loot-" + IrisEntity.this.hashCode());
					}

					@Override
					public Collection<ItemStack> populateLoot(Random random, LootContext context)
					{
						KList<ItemStack> items = new KList<>();

						for(int t = 0; t < gen.getDimension().getLootTries(); t++)
						{
							int b = 4;
							for(String fi : getLoot().getTables())
							{
								IrisLootTable i = gen.getData().getLootLoader().load(fi);
								b++;
								items.addAll(i.getLoot(gen.isDev(), rng.nextParallelRNG(345911 * -t), InventorySlotType.STORAGE, at.getBlockX(), at.getBlockY(), at.getBlockZ(), t + b + b, b));
							}
						}

						return items;
					}

					@Override
					public void fillInventory(Inventory inventory, Random random, LootContext context)
					{
						for(ItemStack i : populateLoot(random, context))
						{
							inventory.addItem(i);
						}

						gen.getGlUpdate().scramble(inventory, rng);
					}
				});
			}
		}

		if(e instanceof LivingEntity)
		{
			LivingEntity l = (LivingEntity) e;
			l.setAI(isAi());
			l.setCanPickupItems(isPickupItems());

			if(getLeashHolder() != null)
			{
				l.setLeashHolder(getLeashHolder().spawn(gen, at, rng.nextParallelRNG(234548)));
			}

			l.setRemoveWhenFarAway(isRemovable());

			if(getHelmet() != null && rng.i(1, getHelmet().getRarity()) == 1)
			{
				l.getEquipment().setHelmet(getHelmet().get(gen.isDev(), rng));
			}

			if(getChestplate() != null && rng.i(1, getChestplate().getRarity()) == 1)
			{
				l.getEquipment().setChestplate(getChestplate().get(gen.isDev(), rng));
			}

			if(getLeggings() != null && rng.i(1, getLeggings().getRarity()) == 1)
			{
				l.getEquipment().setLeggings(getLeggings().get(gen.isDev(), rng));
			}

			if(getBoots() != null && rng.i(1, getBoots().getRarity()) == 1)
			{
				l.getEquipment().setBoots(getBoots().get(gen.isDev(), rng));
			}

			if(getMainHand() != null && rng.i(1, getMainHand().getRarity()) == 1)
			{
				l.getEquipment().setItemInMainHand(getMainHand().get(gen.isDev(), rng));
			}

			if(getOffHand() != null && rng.i(1, getOffHand().getRarity()) == 1)
			{
				l.getEquipment().setItemInOffHand(getOffHand().get(gen.isDev(), rng));
			}
		}

		if(Iris.awareEntities && e instanceof Mob)
		{
			Mob m = (Mob) e;
			m.setAware(isAware());
		}

		return e;
	}
}
