package com.volmit.iris.gen.layer;

import java.util.Arrays;
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

import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.ParallaxTerrainProvider;
import com.volmit.iris.gen.atomics.AtomicSliverMap;
import com.volmit.iris.gen.scaffold.ChunkWrapper;
import com.volmit.iris.object.InventorySlotType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDepositGenerator;
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
	private final ParallaxTerrainProvider gen;
	private final RNG rng;

	public GenLayerUpdate(ParallaxTerrainProvider gen)
	{
		this.gen = gen;
		this.rng = new RNG(gen.getTarget().getSeed() + 4996788).nextParallelRNG(-98618289);
	}

	@Override
	public void populate(World w, Random r, Chunk c)
	{
		AtomicSliverMap map = null;

		try
		{
			map = gen.getParallaxChunk(c.getX(), c.getZ());
		}

		catch(Throwable e)
		{
			map = new AtomicSliverMap();
		}

		RNG rx = rng.nextParallelRNG(c.getX() + r.nextInt()).nextParallelRNG(c.getZ() + r.nextInt());

		if(gen.getDimension().isVanillaCaves())
		{
			generateDepositsWithVanillaSaftey(w, rx, c);
		}

		updateBlocks(rx, c, map);
		spawnInitials(c, rx);
	}

	public void spawnInitials(Chunk c, RNG rx)
	{
		if(!IrisSettings.get().isSystemEntityInitialSpawns())
		{
			return;
		}

		PrecisionStopwatch p = PrecisionStopwatch.start();
		((IrisTerrainProvider) gen).spawnInitials(c, rx);
		p.end();
		gen.getMetrics().getSpawns().put(p.getMilliseconds());
	}

	public void generateDepositsWithVanillaSaftey(World w, RNG rx, Chunk c)
	{
		PrecisionStopwatch p = PrecisionStopwatch.start();
		int x = c.getX();
		int z = c.getZ();
		RNG ro = rx.nextParallelRNG((x * x * x) - z);
		IrisRegion region = gen.sampleRegion((x * 16) + 7, (z * 16) + 7);
		IrisBiome biome = gen.sampleTrueBiome((x * 16) + 7, (z * 16) + 7);
		ChunkWrapper terrain = new ChunkWrapper(c);

		for(IrisDepositGenerator k : gen.getDimension().getDeposits())
		{
			k.generate(terrain, ro, gen, x, z, true);
		}

		for(IrisDepositGenerator k : region.getDeposits())
		{
			for(int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
			{
				k.generate(terrain, ro, gen, x, z, true);
			}
		}

		for(IrisDepositGenerator k : biome.getDeposits())
		{
			for(int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
			{
				k.generate(terrain, ro, gen, x, z, true);
			}
		}
		p.end();
		gen.getMetrics().getDeposits().put(p.getMilliseconds());
	}

	private void updateBlocks(RNG rx, Chunk c, AtomicSliverMap map)
	{
		PrecisionStopwatch p = PrecisionStopwatch.start();
		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				for(byte kv : map.getSliver(i, j).getUpdatables())
				{
					byte k = (byte) (kv - Byte.MIN_VALUE);
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

		if(B.isLit(d))
		{
			updateLight(b, d);
		}

		else if(B.isStorage(d))
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
		KList<IrisLootTable> tables = new KList<>();
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

		int b = 4;
		for(IrisLootTable i : tables)
		{
			b++;
			items.addAll(i.getLoot(debug, items.isEmpty(), rng.nextParallelRNG(345911), slot, x, y, z, b + b, mgf + b));
		}

		for(ItemStack i : items)
		{
			inv.addItem(i);
		}

		scramble(inv, rng);
	}

	public void updateStorage(Block b, BlockData data, int rx, int rz, RNG rng)
	{
		InventorySlotType slot = null;

		if(B.isStorageChest(data))
		{
			slot = InventorySlotType.STORAGE;
		}

		if(slot != null)
		{
			KList<IrisLootTable> tables = getLootTables(rng.nextParallelRNG(4568111), b);
			InventorySlotType slott = slot;

			try
			{
				InventoryHolder m = (InventoryHolder) b.getState();
				addItems(false, m.getInventory(), rng, tables, slott, rx, b.getY(), rz, 15);
			}

			catch(Throwable ignored)
			{

			}
		}
	}

	public void scramble(Inventory inventory, RNG rng)
	{
		ItemStack[] items = inventory.getContents();
		ItemStack[] nitems = new ItemStack[inventory.getSize()];
		System.arraycopy(items, 0, nitems, 0, items.length);
		boolean packedFull = false;

		splitting: for(int i = 0; i < nitems.length; i++)
		{
			ItemStack is = nitems[i];

			if(is != null && is.getAmount() > 1 && !packedFull)
			{
				for(int j = 0; j < nitems.length; j++)
				{
					if(nitems[j] == null)
					{
						int take = rng.nextInt(is.getAmount());
						take = take == 0 ? 1 : take;
						is.setAmount(is.getAmount() - take);
						nitems[j] = is.clone();
						nitems[j].setAmount(take);
						continue splitting;
					}
				}

				packedFull = true;
			}
		}

		for(int i = 0; i < 4; i++)
		{
			try
			{
				Arrays.parallelSort(nitems, (a, b) -> rng.nextInt());
				break;
			}

			catch(Throwable e)
			{

			}
		}

		inventory.setContents(nitems);
	}

	public void updateLight(Block b, BlockData data)
	{
		b.setType(Material.AIR, false);
		b.setBlockData(data, false);
	}
}
