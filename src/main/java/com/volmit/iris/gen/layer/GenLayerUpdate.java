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
		RNG rx = rng.nextParallelRNG(c.getX() + r.nextInt()).nextParallelRNG(c.getZ() + r.nextInt());

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

	public void injectTables(KList<IrisLootTable> list, IrisLootReference r)
	{
		if(r.getMode().equals(LootMode.CLEAR) || r.getMode().equals(LootMode.REPLACE))
		{
			list.clear();
		}

		list.addAll(r.getLootTables(gen));
	}

	public KList<IrisLootTable> getLootTables(RNG rng, Block b)
	{
		int rx = b.getX();
		int rz = b.getZ();
		IrisRegion region = gen.sampleRegion(rx, rz);
		IrisBiome biomeSurface = gen.sampleTrueBiome(rx, rz);
		IrisBiome biomeUnder = gen.sampleTrueBiome(rx, b.getY(), rz);
		KList<IrisLootTable> tables = new KList<IrisLootTable>();
		IrisStructureResult structure = gen.getStructure(rx, b.getY(), rz);
		double multiplier = 1D * gen.getDimension().getLoot().getMultiplier() * region.getLoot().getMultiplier() * biomeSurface.getLoot().getMultiplier() * biomeUnder.getLoot().getMultiplier();
		injectTables(tables, gen.getDimension().getLoot());
		injectTables(tables, region.getLoot());
		injectTables(tables, biomeSurface.getLoot());
		injectTables(tables, biomeUnder.getLoot());

		if(structure != null && structure.getTile() != null)
		{
			injectTables(tables, structure.getStructure().getLoot());
			injectTables(tables, structure.getTile().getLoot());
			multiplier *= structure.getStructure().getLoot().getMultiplier() * structure.getTile().getLoot().getMultiplier();
		}

		if(tables.isNotEmpty())
		{
			int target = (int) Math.round(tables.size() * multiplier);

			while(tables.size() < target && tables.isNotEmpty())
			{
				tables.add(tables.get(rng.i(tables.size() - 1)));
			}

			while(tables.size() > target && tables.isNotEmpty())
			{
				tables.remove(rng.i(tables.size() - 1));
			}
		}

		return tables;
	}

	public void addItems(boolean debug, Inventory inv, RNG rng, KList<IrisLootTable> tables, InventorySlotType slot, int x, int y, int z, int mgf)
	{
		KList<ItemStack> items = new KList<>();

		for(int t = 0; t < gen.getDimension().getLootTries(); t++)
		{
			int b = 4;
			for(IrisLootTable i : tables)
			{
				b++;
				items.addAll(i.getLoot(debug, rng.nextParallelRNG(345911 * -t), slot, x, y, z, t + b + b, mgf + b));
			}

			for(ItemStack i : items)
			{
				inv.addItem(i);
			}

			if(items.isNotEmpty())
			{
				break;
			}
		}

		scramble(inv, rng);
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
			KList<IrisLootTable> tables = getLootTables(rng.nextParallelRNG(4568111), b);

			try
			{
				InventoryHolder m = (InventoryHolder) b.getState();
				addItems(false, m.getInventory(), rng, tables, slot, rx, b.getY(), rz, 15);
			}

			catch(Throwable e)
			{
				Iris.error("NOT INVENTORY: " + data.getMaterial().name());
			}

		}
	}

	public void scramble(Inventory inventory, RNG rng)
	{
		KList<ItemStack> v = new KList<>();

		for(ItemStack i : inventory.getContents())
		{
			if(i == null)
			{
				continue;
			}

			v.add(i);
		}

		inventory.clear();
		int sz = inventory.getSize();
		int tr = 5;

		while(v.isNotEmpty())
		{
			int slot = rng.i(0, sz - 1);

			if(inventory.getItem(slot) == null)
			{
				tr = tr < 5 ? tr + 1 : tr;
				int pick = rng.i(0, v.size() - 1);
				ItemStack g = v.get(pick);

				if(g.getAmount() == 1)
				{
					v.remove(pick);
					inventory.setItem(pick, g);
				}

				else
				{
					int portion = rng.i(1, g.getAmount() - 1);
					ItemStack port = g.clone();
					port.setAmount(portion);
					g.setAmount(g.getAmount() - portion);
					v.add(g);
					inventory.setItem(slot, port);
				}
			}

			else
			{
				tr--;
			}

			if(tr <= 0)
			{
				break;
			}
		}

		for(ItemStack i : v)
		{
			inventory.addItem(i);
		}
	}

	public void updateLight(Block b, BlockData data)
	{
		b.setType(Material.AIR, false);
		b.setBlockData(data, false);
	}
}
