package com.volmit.iris.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisDimension;

public class B
{
	private static final BlockData AIR = Material.AIR.createBlockData();
	private static final KMap<String, BlockData> bdc = new KMap<>();
	private static final KList<String> nulls = new KList<>();
	private static final KList<Material> storage = new KList<>();
	private static final KList<Material> storageChest = new KList<>();
	private static final KList<Material> lit = new KList<>();
	private static final KList<Material> updatable = new KList<>();
	private static final KList<Material> notUpdatable = new KList<>();
	private static final KList<String> canPlaceOn = new KList<>();
	private static final KList<Material> decorant = new KList<>();
	private static final IrisDimension defaultCompat = new IrisDimension();
	private static final KMap<Material, Boolean> solid = new KMap<>();
	private static final KMap<String, Material> types = new KMap<>();
	private static IrisLock lock = new IrisLock("Typelock");

	public static BlockData get(String bd)
	{
		return getBlockData(bd);
	}

	public static Material getMaterial(String bd)
	{
		return types.compute(bd, (k, v) ->
		{
			if(k != null && v != null)
			{
				return v;
			}

			try
			{
				return Material.valueOf(k);
			}

			catch(Throwable e)
			{

			}

			return null;
		});
	}

	public static boolean isSolid(Material mat)
	{
		if(!solid.containsKey(mat))
		{
			solid.put(mat, mat.isSolid());
		}

		return solid.get(mat);
	}

	public static Material mat(String bd)
	{
		return getBlockData(bd).getMaterial();
	}

	public static BlockData getBlockData(String bd)
	{
		return getBlockData(bd, defaultCompat);
	}

	public static BlockData getBlockData(String bdxf, IrisDimension resolver)
	{
		try
		{
			String bd = bdxf.trim();

			if(bdc.containsKey(bd))
			{
				return bdc.get(bd).clone();
			}

			BlockData bdx = parseBlockData(bd);

			if(bdx == null)
			{
				bdx = resolver.resolve(bd);
			}

			if(bdx == null)
			{
				Iris.warn("Unknown Block Data '" + bd + "'");
				nulls.add(bd);
				return bdx;
			}

			bdc.put(bd, bdx);

			return bdx;
		}

		catch(Throwable e)
		{
			Iris.warn("Unknown Block Data '" + bdxf + "'");
		}

		return AIR;
	}

	public static BlockData parseBlockData(String ix)
	{
		try
		{
			BlockData bx = Bukkit.createBlockData(ix);

			if(bx != null)
			{
				return bx;
			}
		}

		catch(Throwable e)
		{

		}

		String i = ix.toUpperCase().trim();
		i = i.equals("WOOL") ? "WHITE_WOOL" : i;
		i = i.equals("CONCRETE") ? "WHITE_CONCRETE" : i;

		try
		{
			Material m = Material.valueOf(i);

			return m.createBlockData();
		}

		catch(Throwable e)
		{

		}

		return AIR;
	}

	public static boolean isUpdatable(BlockData mat)
	{
		return isUpdatable(mat.getMaterial());
	}

	public static boolean isStorage(Material mat)
	{
		if(storage.contains(mat))
		{
			return true;
		}

		//@builder
		boolean str = mat.equals(B.mat("CHEST")) 
				|| mat.equals(B.mat("TRAPPED_CHEST")) 
				|| mat.equals(B.mat("SHULKER_BOX")) 
				|| mat.equals(B.mat("WHITE_SHULKER_BOX")) 
				|| mat.equals(B.mat("ORANGE_SHULKER_BOX")) 
				|| mat.equals(B.mat("MAGENTA_SHULKER_BOX")) 
				|| mat.equals(B.mat("LIGHT_BLUE_SHULKER_BOX")) 
				|| mat.equals(B.mat("YELLOW_SHULKER_BOX")) 
				|| mat.equals(B.mat("LIME_SHULKER_BOX")) 
				|| mat.equals(B.mat("PINK_SHULKER_BOX")) 
				|| mat.equals(B.mat("GRAY_SHULKER_BOX")) 
				|| mat.equals(B.mat("LIGHT_GRAY_SHULKER_BOX")) 
				|| mat.equals(B.mat("CYAN_SHULKER_BOX")) 
				|| mat.equals(B.mat("PURPLE_SHULKER_BOX")) 
				|| mat.equals(B.mat("BLUE_SHULKER_BOX")) 
				|| mat.equals(B.mat("BROWN_SHULKER_BOX")) 
				|| mat.equals(B.mat("GREEN_SHULKER_BOX")) 
				|| mat.equals(B.mat("RED_SHULKER_BOX")) 
				|| mat.equals(B.mat("BLACK_SHULKER_BOX")) 
				|| mat.equals(B.mat("BARREL")) 
				|| mat.equals(B.mat("DISPENSER")) 
				|| mat.equals(B.mat("DROPPER")) 
				|| mat.equals(B.mat("HOPPER")) 
				|| mat.equals(B.mat("FURNACE")) 
				|| mat.equals(B.mat("BLAST_FURNACE")) 
				|| mat.equals(B.mat("SMOKER"));
		//@done

		if(str)
		{
			lock.lock();
			storage.add(mat);
			lock.unlock();
			return true;
		}

		return false;
	}

	public static boolean isStorageChest(Material mat)
	{
		if(storageChest.contains(mat))
		{
			return true;
		}

		//@builder
		boolean str = mat.equals(B.mat("CHEST")) 
				|| mat.equals(B.mat("TRAPPED_CHEST")) 
				|| mat.equals(B.mat("SHULKER_BOX")) 
				|| mat.equals(B.mat("WHITE_SHULKER_BOX")) 
				|| mat.equals(B.mat("ORANGE_SHULKER_BOX")) 
				|| mat.equals(B.mat("MAGENTA_SHULKER_BOX")) 
				|| mat.equals(B.mat("LIGHT_BLUE_SHULKER_BOX")) 
				|| mat.equals(B.mat("YELLOW_SHULKER_BOX")) 
				|| mat.equals(B.mat("LIME_SHULKER_BOX")) 
				|| mat.equals(B.mat("PINK_SHULKER_BOX")) 
				|| mat.equals(B.mat("GRAY_SHULKER_BOX")) 
				|| mat.equals(B.mat("LIGHT_GRAY_SHULKER_BOX")) 
				|| mat.equals(B.mat("CYAN_SHULKER_BOX")) 
				|| mat.equals(B.mat("PURPLE_SHULKER_BOX")) 
				|| mat.equals(B.mat("BLUE_SHULKER_BOX")) 
				|| mat.equals(B.mat("BROWN_SHULKER_BOX")) 
				|| mat.equals(B.mat("GREEN_SHULKER_BOX")) 
				|| mat.equals(B.mat("RED_SHULKER_BOX")) 
				|| mat.equals(B.mat("BLACK_SHULKER_BOX")) 
				|| mat.equals(B.mat("BARREL")) 
				|| mat.equals(B.mat("DISPENSER")) 
				|| mat.equals(B.mat("DROPPER")) 
				|| mat.equals(B.mat("HOPPER"));
		//@done

		if(str)
		{
			lock.lock();
			storageChest.add(mat);
			lock.unlock();
			return true;
		}

		return false;
	}

	public static boolean isLit(Material mat)
	{
		if(lit.contains(mat))
		{
			return true;
		}

		//@builder
		boolean str = mat.equals(B.mat("GLOWSTONE")) 
				|| mat.equals(B.mat("END_ROD")) 
				|| mat.equals(B.mat("SOUL_SAND"))
				|| mat.equals(B.mat("TORCH")) 
				|| mat.equals(Material.REDSTONE_TORCH) 
				|| mat.equals(B.mat("SOUL_TORCH")) 
				|| mat.equals(Material.REDSTONE_WALL_TORCH) 
				|| mat.equals(Material.WALL_TORCH) 
				|| mat.equals(B.mat("SOUL_WALL_TORCH")) 
				|| mat.equals(B.mat("LANTERN")) 
				|| mat.equals(Material.JACK_O_LANTERN) 
				|| mat.equals(Material.REDSTONE_LAMP) 
				|| mat.equals(Material.MAGMA_BLOCK) 
				|| mat.equals(B.mat("SHROOMLIGHT")) 
				|| mat.equals(B.mat("SEA_LANTERN")) 
				|| mat.equals(B.mat("SOUL_LANTERN")) 
				|| mat.equals(Material.FIRE) 
				|| mat.equals(B.mat("SOUL_FIRE")) 
				|| mat.equals(B.mat("SEA_PICKLE")) 
				|| mat.equals(Material.BREWING_STAND) 
				|| mat.equals(Material.REDSTONE_ORE);
		//@done
		if(str)
		{
			lock.lock();
			lit.add(mat);
			lock.unlock();
			return true;
		}

		return false;
	}

	public static boolean isUpdatable(Material mat)
	{
		if(updatable.contains(mat))
		{
			return true;
		}

		if(notUpdatable.contains(mat))
		{
			return false;
		}

		boolean str = isLit(mat) || isStorage(mat);

		if(str)
		{
			lock.lock();
			updatable.add(mat);
			lock.unlock();
			return true;
		}

		lock.lock();
		notUpdatable.add(mat);
		lock.unlock();
		return false;
	}

	public static boolean canPlaceOnto(Material mat, Material onto)
	{
		String key = mat.name() + "" + onto.name();

		if(canPlaceOn.contains(key))
		{
			return false;
		}

		if(onto.equals(Material.AIR) || onto.equals(B.mat("CAVE_AIR")))
		{
			lock.lock();
			canPlaceOn.add(key);
			lock.unlock();
			return false;
		}

		if(onto.equals(Material.GRASS_BLOCK) && mat.equals(Material.DEAD_BUSH))
		{
			lock.lock();
			canPlaceOn.add(key);
			lock.unlock();
			return false;
		}

		if(onto.equals(Material.GRASS_PATH))
		{
			if(!mat.isSolid())
			{
				lock.lock();
				canPlaceOn.add(key);
				lock.unlock();
				return false;
			}
		}

		if(onto.equals(Material.STONE) || onto.equals(Material.GRAVEL) || onto.equals(Material.GRAVEL) || onto.equals(Material.ANDESITE) || onto.equals(Material.GRANITE) || onto.equals(Material.DIORITE) || onto.equals(B.mat("BLACKSTONE")) || onto.equals(B.mat("BASALT")))
		{
			if(mat.equals(Material.POPPY) || mat.equals(Material.DANDELION) || mat.equals(B.mat("CORNFLOWER")) || mat.equals(Material.ORANGE_TULIP) || mat.equals(Material.PINK_TULIP) || mat.equals(Material.RED_TULIP) || mat.equals(Material.WHITE_TULIP) || mat.equals(Material.FERN) || mat.equals(Material.LARGE_FERN) || mat.equals(Material.GRASS) || mat.equals(Material.TALL_GRASS))
			{
				lock.lock();
				canPlaceOn.add(key);
				lock.unlock();
				return false;
			}
		}

		if(onto.equals(Material.ACACIA_LEAVES) || onto.equals(Material.BIRCH_LEAVES) || onto.equals(Material.DARK_OAK_LEAVES) || onto.equals(Material.JUNGLE_LEAVES) || onto.equals(Material.OAK_LEAVES) || onto.equals(Material.SPRUCE_LEAVES))
		{
			if(!mat.isSolid())
			{
				lock.lock();
				canPlaceOn.add(key);
				lock.unlock();
				return false;
			}
		}

		return true;
	}

	public static boolean isDecorant(Material m)
	{
		if(decorant.contains(m))
		{
			return true;
		}

		//@builder
		boolean str = m.equals(Material.GRASS) 
				|| m.equals(Material.TALL_GRASS)
				|| m.equals(B.mat("CORNFLOWER"))
				|| m.equals(Material.SUNFLOWER)
				|| m.equals(Material.CHORUS_FLOWER)
				|| m.equals(Material.POPPY)
				|| m.equals(Material.DANDELION)
				|| m.equals(Material.OXEYE_DAISY)
				|| m.equals(Material.ORANGE_TULIP)
				|| m.equals(Material.PINK_TULIP)
				|| m.equals(Material.RED_TULIP)
				|| m.equals(Material.WHITE_TULIP)
				|| m.equals(Material.LILAC)
				|| m.equals(Material.DEAD_BUSH)
				|| m.equals(B.mat("SWEET_BERRY_BUSH"))
				|| m.equals(Material.ROSE_BUSH)
				|| m.equals(B.mat("WITHER_ROSE"))
				|| m.equals(Material.ALLIUM)
				|| m.equals(Material.BLUE_ORCHID)
				|| m.equals(B.mat("LILY_OF_THE_VALLEY"))
				|| m.equals(B.mat("CRIMSON_FUNGUS"))
				|| m.equals(B.mat("WARPED_FUNGUS"))
				|| m.equals(Material.RED_MUSHROOM)
				|| m.equals(Material.BROWN_MUSHROOM)
				|| m.equals(B.mat("CRIMSON_ROOTS"))
				|| m.equals(B.mat("AZURE_BLUET"))
				|| m.equals(B.mat("WEEPING_VINES"))
				|| m.equals(B.mat("WEEPING_VINES_PLANT"))
				|| m.equals(B.mat("WARPED_ROOTS"))
				|| m.equals(B.mat("NETHER_SPROUTS"))
				|| m.equals(B.mat("TWISTING_VINES"))
				|| m.equals(B.mat("TWISTING_VINES_PLANT"))
				|| m.equals(Material.SUGAR_CANE)
				|| m.equals(Material.WHEAT)
				|| m.equals(Material.POTATOES)
				|| m.equals(Material.CARROTS)
				|| m.equals(Material.BEETROOTS)
				|| m.equals(Material.NETHER_WART)
				|| m.equals(B.mat("SEA_PICKLE"))
				|| m.equals(B.mat("SEAGRASS"))
				|| m.equals(B.mat("ACACIA_BUTTON"))
				|| m.equals(B.mat("BIRCH_BUTTON"))
				|| m.equals(B.mat("CRIMSON_BUTTON"))
				|| m.equals(B.mat("DARK_OAK_BUTTON"))
				|| m.equals(B.mat("JUNGLE_BUTTON"))
				|| m.equals(B.mat("OAK_BUTTON"))
				|| m.equals(B.mat("POLISHED_BLACKSTONE_BUTTON"))
				|| m.equals(B.mat("SPRUCE_BUTTON"))
				|| m.equals(B.mat("STONE_BUTTON"))
				|| m.equals(B.mat("WARPED_BUTTON"))
				|| m.equals(Material.TORCH)
				|| m.equals(B.mat("SOUL_TORCH"));
		//@done

		if(str)
		{
			decorant.add(m);
			return true;
		}

		return false;
	}

	public static KList<BlockData> getBlockData(KList<String> find)
	{
		KList<BlockData> b = new KList<>();

		for(String i : find)
		{
			BlockData bd = getBlockData(i);

			if(bd != null)
			{
				b.add(bd);
			}
		}

		return b;
	}

	public static boolean isAir(BlockData blockData)
	{
		return blockData.getMaterial().isAir();
	}
}
