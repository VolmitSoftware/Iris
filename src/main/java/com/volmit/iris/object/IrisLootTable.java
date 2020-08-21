package com.volmit.iris.object;

import org.bukkit.inventory.ItemStack;

import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Desc("Represents a loot table. Biomes, Regions & Objects can add or replace the virtual table with these loot tables")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisLootTable extends IrisRegistrant
{
	@Desc("The name of this loot table")
	@DontObfuscate
	@MinNumber(2)
	private String name;

	@MinNumber(1)
	@DontObfuscate
	@Desc("The rarity as in 1 in X chance")
	private int rarity = 1;

	@DontObfuscate
	@Desc("The loot in this table")
	@ArrayType(min = 1, type = IrisLoot.class)
	private KList<IrisLoot> loot = new KList<>();

	public KList<ItemStack> getLoot(boolean debug, RNG rng, InventorySlotType slot, int x, int y, int z)
	{
		KList<ItemStack> lootf = new KList<>();

		int m = 0;

		for(IrisLoot i : loot)
		{
			if(i.getSlotTypes().equals(slot))
			{
				ItemStack item = i.get(debug, this, rng.nextParallelRNG(294788 + x + y - z * z + (m * -4125)), x, y, z);

				if(item != null)
				{
					lootf.add(item);
				}
			}

			m++;
		}

		return lootf;
	}

	public IrisLootTable()
	{

	}
}
