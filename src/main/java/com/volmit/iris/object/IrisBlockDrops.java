package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a block drop list")
@Data
public class IrisBlockDrops
{
	@Required
	@ArrayType(min = 1, type = IrisBlockData.class)
	@Desc("The blocks that drop loot")
	private KList<IrisBlockData> blocks = new KList<IrisBlockData>();

	@DontObfuscate
	@Desc("If exact blocks is set to true, minecraft:barrel[axis=x] will only drop for that axis. When exact is false (default) any barrel will drop the defined drops.")
	private boolean exactBlocks = false;

	@DontObfuscate
	@Desc("Add in specific items to drop")
	@ArrayType(min = 1, type = IrisLoot.class)
	private KList<IrisLoot> drops = new KList<>();

	@DontObfuscate
	@Desc("If this is in a biome, setting skipParents to true will ignore the drops in the region and dimension for this block type. The default (false) will allow all three nodes to fire and add to a list of drops.")
	private boolean skipParents = false;

	@DontObfuscate
	@Desc("Removes the default vanilla block drops and only drops the given items & any parent loot tables specified for this block type.")
	private boolean replaceVanillaDrops = false;

	private final transient AtomicCache<KList<BlockData>> data = new AtomicCache<>();

	public boolean shouldDropFor(BlockData data, IrisDataManager rdata)
	{
		KList<BlockData> list = this.data.aquire(() ->
		{
			KList<BlockData> b = new KList<>();

			for(IrisBlockData i : getBlocks())
			{
				BlockData dd = i.getBlockData(rdata);

				if(dd != null)
				{
					b.add(dd);
				}
			}

			return b.removeDuplicates();
		});

		for(BlockData i : list)
		{
			if(exactBlocks ? i.equals(data) : i.getMaterial().equals(data.getMaterial()))
			{
				return true;
			}
		}

		return false;
	}

	public void fillDrops(boolean debug, KList<ItemStack> d)
	{
		for(IrisLoot i : getDrops())
		{
			if(RNG.r.i(1, i.getRarity()) == i.getRarity())
			{
				d.add(i.get(debug, RNG.r));
			}
		}
	}
}
