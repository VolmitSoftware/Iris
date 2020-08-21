package com.volmit.iris.gen.layer;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.ParallaxChunkGenerator;
import com.volmit.iris.gen.atomics.AtomicSliverMap;
import com.volmit.iris.object.InventorySlotType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisLootReference;
import com.volmit.iris.object.IrisLootTable;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.LootMode;
import com.volmit.iris.util.B;
import com.volmit.iris.util.IrisStructureResult;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

public class GenLayerUpdate extends BlockPopulator
{
	private ParallaxChunkGenerator gen;
	private RNG rng;

	public GenLayerUpdate(ParallaxChunkGenerator gen, World w)
	{
		this.gen = gen;
		this.rng = new RNG(w.getSeed() + 4996788).nextParallelRNG(-98618289);
	}

	@Override
	public void populate(World w, Random r, Chunk c)
	{
		PrecisionStopwatch p = PrecisionStopwatch.start();
		AtomicSliverMap map = gen.getParallaxChunk(c.getX(), c.getZ());
		RNG rx = rng.nextParallelRNG(c.getX()).nextParallelRNG(c.getZ());

		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				for(int k : map.getSliver(i, j).getUpdatables())
				{
					if(k > 255 || k < 0)
					{
						continue;
					}

					update(c, i, k, j, i + (c.getX() << 4), i + (c.getZ() << 4), rx);
				}
			}
		}
		p.end();
		gen.getMetrics().getUpdate().put(p.getMilliseconds());
	}

	public void update(Chunk c, int x, int y, int z, int rx, int rz, RNG rng)
	{
		Block b = c.getBlock(x, y, z);
		BlockData d = b.getBlockData();

		if(B.isLit(d.getMaterial()))
		{
			updateLight(b, d);
		}

		else if(B.isStorage(d.getMaterial()))
		{
			updateStorage(b, d, rx, rz, rng);
		}
	}

	public void injectTables(KSet<IrisLootTable> list, IrisLootReference r)
	{
		if(r.getMode().equals(LootMode.CLEAR) || r.getMode().equals(LootMode.REPLACE))
		{
			list.clear();
		}

		list.addAll(r.getLootTables(gen));
	}

	public KSet<IrisLootTable> getLootTables(Block b)
	{
		int rx = b.getX();
		int rz = b.getZ();
		IrisRegion region = gen.sampleRegion(rx, rz);
		IrisBiome biomeSurface = gen.sampleTrueBiome(rx, rz).getBiome();
		IrisBiome biomeUnder = gen.sampleTrueBiome(rx, b.getY(), rz).getBiome();
		KSet<IrisLootTable> tables = new KSet<>();
		IrisStructureResult structure = gen.getStructure(rx, b.getY(), rz);
		injectTables(tables, gen.getDimension().getLoot());
		injectTables(tables, region.getLoot());
		injectTables(tables, biomeSurface.getLoot());
		injectTables(tables, biomeUnder.getLoot());

		if(structure != null && structure.getTile() != null)
		{
			injectTables(tables, structure.getStructure().getLoot());
			injectTables(tables, structure.getTile().getLoot());
		}

		return tables;
	}

	public void addItems(boolean debug, Inventory inv, RNG rng, KSet<IrisLootTable> tables, InventorySlotType slot, int x, int y, int z)
	{
		KList<ItemStack> items = new KList<>();

		for(IrisLootTable i : tables)
		{
			items.addAll(i.getLoot(debug, rng, slot, x, y, z));
		}

		for(ItemStack i : items)
		{
			inv.addItem(i);
		}
	}

	public void updateStorage(Block b, BlockData data, int rx, int rz, RNG rng)
	{
		InventorySlotType slot = null;

		if(B.isStorageChest(data.getMaterial()))
		{
			slot = InventorySlotType.STORAGE;
		}

		if(slot != null)
		{
			KSet<IrisLootTable> tables = getLootTables(b);

			try
			{
				InventoryHolder m = (InventoryHolder) b.getState();
				addItems(false, m.getInventory(), rng, tables, slot, rx, b.getY(), rz);
			}

			catch(Throwable e)
			{
				Iris.error("NOT INVENTORY: " + data.getMaterial().name());
			}

		}
	}

	public void updateLight(Block b, BlockData data)
	{
		b.setType(Material.AIR, false);
		b.setBlockData(data, false);
	}
}
