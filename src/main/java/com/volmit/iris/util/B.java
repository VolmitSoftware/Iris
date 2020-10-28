package com.volmit.iris.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisDimension;

public class B
{
	private static final BlockData AIR = Material.AIR.createBlockData();
	private static final KMap<String, BlockData> bdc = new KMap<>();
	private static final KList<String> nulls = new KList<>();
	private static final KList<String> canPlaceOn = new KList<>();
	private static final KList<BlockData> decorant = new KList<>();
	private static final IrisDimension defaultCompat = new IrisDimension();
	private static final KMap<Material, Boolean> solid = new KMap<>();
	private static final KMap<String, BlockData> types = new KMap<>();
	private static final KMap<String, Material> typesb = new KMap<>();
	private static IrisLock lock = new IrisLock("Typelock");

	public static BlockData get(String bd)
	{
		return getBlockData(bd);
	}

	public static boolean isWater(BlockData b)
	{
		return b.getMaterial().equals(Material.WATER);
	}

	public static BlockData getAir()
	{
		return AIR;
	}

	public static KMap<String, BlockData> getBdc()
	{
		return bdc;
	}

	public static KList<String> getNulls()
	{
		return nulls;
	}

	public static KList<String> getCanplaceon()
	{
		return canPlaceOn;
	}

	public static KList<BlockData> getDecorant()
	{
		return decorant;
	}

	public static IrisDimension getDefaultcompat()
	{
		return defaultCompat;
	}

	public static KMap<Material, Boolean> getSolid()
	{
		return solid;
	}

	public static KMap<String, BlockData> getTypes()
	{
		return types;
	}

	public static KMap<String, Material> getTypesb()
	{
		return typesb;
	}

	public static IrisLock getLock()
	{
		return lock;
	}

	public static Material getMaterial(String bdx)
	{
		String bd = bdx.trim().toUpperCase();

		return typesb.compute(bd, (k, v) ->
		{
			if(v != null)
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

			return defaultCompat.resolveItem(bdx);
		});
	}

	public static Material getMaterialOrNull(String bdx)
	{
		String bd = bdx.trim().toUpperCase();

		try
		{
			return Material.valueOf(bd);
		}

		catch(Throwable e)
		{
			return null;
		}
	}

	public static boolean isSolid(BlockData mat)
	{
		return isSolid(mat.getMaterial());
	}

	public static boolean isSolid(Material mat)
	{
		return mat.isSolid();
	}

	public static BlockData mat(String bd)
	{
		return getBlockData(bd);
	}

	public static BlockData getBlockData(String bd)
	{
		return getBlockData(bd, defaultCompat);
	}

	public static String[] getBlockTypes()
	{
		KList<String> bt = new KList<String>();

		for(Material i : Material.values())
		{
			if(i.isBlock())
			{
				String v = i.createBlockData().getAsString(true);

				if(v.contains("["))
				{
					v = v.split("\\Q[\\E")[0];
				}

				if(v.contains(":"))
				{
					v = v.split("\\Q:\\E")[1];
				}

				bt.add(v);
			}
		}

		return bt.toArray(new String[bt.size()]);
	}

	public static String[] getItemTypes()
	{
		KList<String> bt = new KList<String>();

		for(Material i : Material.values())
		{
			String v = i.name().toLowerCase().trim();
			bt.add(v);
		}

		return bt.toArray(new String[bt.size()]);
	}

	public static BlockData getBlockData(String bdxf, IrisDimension resolver)
	{
		try
		{
			String bd = bdxf.trim();
			BlockData fff = bdc.get(bd);
			if(fff != null)
			{
				return fff.clone();
			}

			BlockData bdx = parseBlockData(bd);

			if(bdx == null)
			{
				bdx = resolver.resolveBlock(bd);
			}

			if(bdx == null)
			{
				Iris.warn("Unknown Block Data '" + bd + "'");
				nulls.add(bd);
				return AIR;
			}

			if(resolver.isPreventLeafDecay() && bdx instanceof Leaves)
			{
				((Leaves) bdx).setPersistent(true);
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

	public static BlockData parseBlockDataOrNull(String ix)
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
			return Material.valueOf(i).createBlockData();
		}

		catch(Throwable e)
		{

		}

		return null;
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
			return Material.valueOf(i).createBlockData();
		}

		catch(Throwable e)
		{

		}

		return AIR;
	}

	public static boolean isStorage(BlockData mat)
	{
		// @builder
		return mat.getMaterial().equals(B.mat("CHEST").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("TRAPPED_CHEST").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("WHITE_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("ORANGE_SHULKER_BOX").getMaterial())
				|| mat.getMaterial().equals(B.mat("MAGENTA_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("LIGHT_BLUE_SHULKER_BOX").getMaterial())
				|| mat.getMaterial().equals(B.mat("YELLOW_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("LIME_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("PINK_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("GRAY_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("LIGHT_GRAY_SHULKER_BOX").getMaterial())
				|| mat.getMaterial().equals(B.mat("CYAN_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("PURPLE_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("BLUE_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("BROWN_SHULKER_BOX").getMaterial())
				|| mat.getMaterial().equals(B.mat("GREEN_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("RED_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("BLACK_SHULKER_BOX").getMaterial())
				|| mat.getMaterial().equals(B.mat("BARREL").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("DISPENSER").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("DROPPER").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("HOPPER").getMaterial())
				|| mat.getMaterial().equals(B.mat("FURNACE").getMaterial())
				|| mat.getMaterial().equals(B.mat("BLAST_FURNACE").getMaterial())
				|| mat.getMaterial().equals(B.mat("SMOKER").getMaterial());
		//@done
	}

	public static boolean isStorageChest(BlockData mat)
	{
		// @builder
		return mat.getMaterial().equals(B.mat("CHEST").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("TRAPPED_CHEST").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("WHITE_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("ORANGE_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("MAGENTA_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("LIGHT_BLUE_SHULKER_BOX").getMaterial())
				|| mat.getMaterial().equals(B.mat("YELLOW_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("LIME_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("PINK_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("GRAY_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("LIGHT_GRAY_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("CYAN_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("PURPLE_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("BLUE_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("BROWN_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("GREEN_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("RED_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("BLACK_SHULKER_BOX").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("BARREL").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("DISPENSER").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("DROPPER").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("HOPPER").getMaterial());
		//@done
	}

	public static boolean isLit(BlockData mat)
	{
		// @builder
		return mat.getMaterial().equals(B.mat("GLOWSTONE").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("END_ROD").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("SOUL_SAND").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("TORCH").getMaterial()) 
				|| mat.getMaterial().equals(Material.REDSTONE_TORCH)
				|| mat.getMaterial().equals(B.mat("SOUL_TORCH").getMaterial()) 
				|| mat.getMaterial().equals(Material.REDSTONE_WALL_TORCH)
				|| mat.getMaterial().equals(Material.WALL_TORCH)
				|| mat.getMaterial().equals(B.mat("SOUL_WALL_TORCH").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("LANTERN").getMaterial()) 
				|| mat.getMaterial().equals(Material.JACK_O_LANTERN)
				|| mat.getMaterial().equals(Material.REDSTONE_LAMP)
				|| mat.getMaterial().equals(Material.MAGMA_BLOCK)
				|| mat.getMaterial().equals(B.mat("SHROOMLIGHT").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("SEA_LANTERN").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("SOUL_LANTERN").getMaterial()) 
				|| mat.getMaterial().equals(Material.FIRE)
				|| mat.getMaterial().equals(B.mat("SOUL_FIRE").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("SEA_PICKLE").getMaterial()) 
				|| mat.getMaterial().equals(Material.BREWING_STAND)
				|| mat.getMaterial().equals(Material.REDSTONE_ORE);
		//@done
	}

	public static boolean isUpdatable(BlockData mat)
	{
		return isLit(mat) || isStorage(mat);
	}

	public static boolean isFoliage(BlockData d)
	{
		if(isFluid(d) || isAir(d) || isSolid(d))
		{
			return false;
		}

		BlockData mat = d;
		// @builder
		return mat.getMaterial().equals(Material.POPPY)
				|| mat.getMaterial().equals(Material.DANDELION)
				|| mat.getMaterial().equals(B.mat("CORNFLOWER").getMaterial())
				|| mat.getMaterial().equals(B.mat("SWEET_BERRY_BUSH").getMaterial())
				|| mat.getMaterial().equals(B.mat("CRIMSON_ROOTS").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("WARPED_ROOTS").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("NETHER_SPROUTS").getMaterial())
				|| mat.getMaterial().equals(B.mat("ALLIUM").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("AZURE_BLUET").getMaterial())
				|| mat.getMaterial().equals(B.mat("BLUE_ORCHID").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("POPPY").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("DANDELION").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("OXEYE_DAISY").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("LILY_OF_THE_VALLEY").getMaterial()) 
				|| mat.getMaterial().equals(B.mat("WITHER_ROSE").getMaterial()) 
				|| mat.getMaterial().equals(Material.DARK_OAK_SAPLING)
				|| mat.getMaterial().equals(Material.ACACIA_SAPLING)
				|| mat.getMaterial().equals(Material.JUNGLE_SAPLING)
				|| mat.getMaterial().equals(Material.BIRCH_SAPLING)
				|| mat.getMaterial().equals(Material.SPRUCE_SAPLING)
				|| mat.getMaterial().equals(Material.OAK_SAPLING)
				|| mat.getMaterial().equals(Material.ORANGE_TULIP)
				|| mat.getMaterial().equals(Material.PINK_TULIP)
				|| mat.getMaterial().equals(Material.RED_TULIP)
				|| mat.getMaterial().equals(Material.WHITE_TULIP)
				|| mat.getMaterial().equals(Material.FERN)
				|| mat.getMaterial().equals(Material.LARGE_FERN)
				|| mat.getMaterial().equals(Material.GRASS)
				|| mat.getMaterial().equals(Material.TALL_GRASS);
		//@done
	}

	public static boolean canPlaceOnto(Material mat, Material onto)
	{
		String key = mat.name() + "" + onto.name();

		if(canPlaceOn.contains(key))
		{
			return false;
		}

		if(isFoliage(B.get(mat.name())))
		{
			if(!isFoliagePlantable(B.get(onto.name())))
			{
				lock.lock();
				canPlaceOn.add(key);
				lock.unlock();
				return false;
			}
		}

		if(onto.equals(Material.AIR) || onto.equals(B.mat("CAVE_AIR").getMaterial()))
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

		if(onto.equals(Material.ACACIA_LEAVES) || onto.equals(Material.BIRCH_LEAVES)
				|| onto.equals(Material.DARK_OAK_LEAVES)
				|| onto.equals(Material.JUNGLE_LEAVES)
				|| onto.equals(Material.OAK_LEAVES)
				|| onto.equals(Material.SPRUCE_LEAVES))
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

	public static boolean isDecorant(BlockData m)
	{
		if(decorant.contains(m))
		{
			return true;
		}

		// @builder
		boolean str = m.getMaterial().equals(Material.GRASS)
				|| m.getMaterial().equals(Material.TALL_GRASS)
				|| m.equals(B.mat("CORNFLOWER"))
				|| m.getMaterial().equals(Material.SUNFLOWER)
				|| m.getMaterial().equals(Material.CHORUS_FLOWER)
				|| m.getMaterial().equals(Material.POPPY)
				|| m.getMaterial().equals(Material.DANDELION)
				|| m.getMaterial().equals(Material.OXEYE_DAISY)
				|| m.getMaterial().equals(Material.ORANGE_TULIP)
				|| m.getMaterial().equals(Material.PINK_TULIP)
				|| m.getMaterial().equals(Material.RED_TULIP)
				|| m.getMaterial().equals(Material.WHITE_TULIP)
				|| m.getMaterial().equals(Material.LILAC)
				|| m.getMaterial().equals(Material.DEAD_BUSH)
				|| m.equals(B.mat("SWEET_BERRY_BUSH"))
				|| m.getMaterial().equals(Material.ROSE_BUSH)
				|| m.equals(B.mat("WITHER_ROSE"))
				|| m.getMaterial().equals(Material.ALLIUM)
				|| m.getMaterial().equals(Material.BLUE_ORCHID)
				|| m.equals(B.mat("LILY_OF_THE_VALLEY"))
				|| m.equals(B.mat("CRIMSON_FUNGUS"))
				|| m.equals(B.mat("WARPED_FUNGUS"))
				|| m.getMaterial().equals(Material.RED_MUSHROOM)
				|| m.getMaterial().equals(Material.BROWN_MUSHROOM)
				|| m.equals(B.mat("CRIMSON_ROOTS"))
				|| m.equals(B.mat("AZURE_BLUET"))
				|| m.equals(B.mat("WEEPING_VINES"))
				|| m.equals(B.mat("WEEPING_VINES_PLANT"))
				|| m.equals(B.mat("WARPED_ROOTS"))
				|| m.equals(B.mat("NETHER_SPROUTS"))
				|| m.equals(B.mat("TWISTING_VINES"))
				|| m.equals(B.mat("TWISTING_VINES_PLANT"))
				|| m.getMaterial().equals(Material.SUGAR_CANE)
				|| m.getMaterial().equals(Material.WHEAT)
				|| m.getMaterial().equals(Material.POTATOES)
				|| m.getMaterial().equals(Material.CARROTS)
				|| m.getMaterial().equals(Material.BEETROOTS)
				|| m.getMaterial().equals(Material.NETHER_WART)
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
				|| m.getMaterial().equals(Material.TORCH)
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

	public static boolean isFoliagePlantable(BlockData d)
	{
		return d.getMaterial().equals(Material.GRASS_BLOCK) || d.getMaterial().equals(Material.DIRT) || d.getMaterial().equals(Material.COARSE_DIRT) || d.getMaterial().equals(Material.PODZOL);
	}

	public static boolean isFluid(BlockData d)
	{
		return d.getMaterial().equals(Material.WATER) || d.getMaterial().equals(Material.LAVA);
	}

	public static boolean isAirOrFluid(BlockData d)
	{
		return isAir(d) || isFluid(d);
	}

	public static boolean isAir(BlockData d)
	{
		if(d == null)
		{
			return true;
		}

		return d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR) || d.getMaterial().equals(Material.VOID_AIR);
	}
}
