package com.volmit.iris.object;

import org.bukkit.inventory.ItemStack;

import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a loot table. Biomes, Regions & Objects can add or replace the virtual table with these loot tables")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisLootTable extends IrisRegistrant
{
	@Required

	@Desc("The name of this loot table")
	@DontObfuscate
	@MinNumber(2)
	private String name = "";

	@MinNumber(1)
	@DontObfuscate
	@Desc("The rarity as in 1 in X chance")
	private int rarity = 1;

	@MinNumber(1)
	@DontObfuscate
	@Desc("The maximum amount of loot that can be picked in this table at a time.")
	private int maxPicked = 5;

	@MinNumber(0)
	@DontObfuscate
	@Desc("The minimum amount of loot that can be picked in this table at a time.")
	private int minPicked = 1;

	@DontObfuscate
	@Desc("The loot in this table")
	@ArrayType(min = 1, type = IrisLoot.class)
	private KList<IrisLoot> loot = new KList<>();

	public KList<ItemStack> getLoot(boolean debug, boolean doSomething, RNG rng, InventorySlotType slot, int x, int y, int z, int gg, int ffs)
	{
		KList<ItemStack> lootf = new KList<>();

		int m = 0;

		for(IrisLoot i : loot)
		{
			if(i.getSlotTypes().equals(slot))
			{
				ItemStack item = i.get(debug, false, this, rng, x, y, z);

				if(item != null)
				{
					lootf.add(item);
				}
			}

			m++;

			if(m > maxPicked)
			{
				break;
			}
		}

		if(lootf.size() < getMinPicked())
		{
			for(int i = 0; i < getMinPicked() - lootf.size(); i++)
			{
				ItemStack item = loot.get(rng.nextParallelRNG(3945).nextInt(loot.size())).get(debug, doSomething, this, rng, x, y, z);
				if(item != null)
				{
					lootf.add(item);
				}
			}
		}

		return lootf;
	}
}
