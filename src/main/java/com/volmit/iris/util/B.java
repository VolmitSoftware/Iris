package com.volmit.iris.util;

import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.type.Leaves;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisDimension;

public class B
{
	private static final FastBlockData AIR = FastBlockData.of(Material.AIR);
	private static final LoadingCache<String, FastBlockData> bdc = Caffeine.newBuilder().expireAfterAccess(60000, TimeUnit.MILLISECONDS).build((c) -> null);
	private static final KList<String> nulls = new KList<>();
	private static final KList<FastBlockData> storage = new KList<>();
	private static final KList<FastBlockData> storageChest = new KList<>();
	private static final KList<FastBlockData> lit = new KList<>();
	private static final KList<FastBlockData> updatable = new KList<>();
	private static final KList<FastBlockData> notUpdatable = new KList<>();
	private static final KList<String> canPlaceOn = new KList<>();
	private static final KList<FastBlockData> decorant = new KList<>();
	private static final IrisDimension defaultCompat = new IrisDimension();
	private static final KMap<Material, Boolean> solid = new KMap<>();
	private static final LoadingCache<String, FastBlockData> types = Caffeine.newBuilder().expireAfterAccess(30000, TimeUnit.MILLISECONDS).build((c) -> null);
	private static final LoadingCache<String, FastBlockData> typesb = Caffeine.newBuilder().expireAfterAccess(30000, TimeUnit.MILLISECONDS).build((c) -> null);
	private static IrisLock lock = new IrisLock("Typelock");

	public static FastBlockData get(String bd)
	{
		return getBlockData(bd);
	}

	public static boolean isWater(FastBlockData b)
	{
		return b.getMaterial().equals(Material.WATER);
	}

	public static FastBlockData getAir()
	{
		return AIR;
	}

	public static LoadingCache<String, FastBlockData> getBdc()
	{
		return bdc;
	}

	public static KList<String> getNulls()
	{
		return nulls;
	}

	public static KList<FastBlockData> getStorage()
	{
		return storage;
	}

	public static KList<FastBlockData> getStoragechest()
	{
		return storageChest;
	}

	public static KList<FastBlockData> getLit()
	{
		return lit;
	}

	public static KList<FastBlockData> getUpdatable()
	{
		return updatable;
	}

	public static KList<FastBlockData> getNotupdatable()
	{
		return notUpdatable;
	}

	public static KList<String> getCanplaceon()
	{
		return canPlaceOn;
	}

	public static KList<FastBlockData> getDecorant()
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

	public static LoadingCache<String, FastBlockData> getTypes()
	{
		return types;
	}

	public static LoadingCache<String, FastBlockData> getTypesb()
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

		return typesb.get(bd, (k) ->
		{
			try
			{
				return FastBlockData.of(Material.valueOf(k));
			}

			catch(Throwable e)
			{

			}

			return FastBlockData.of(defaultCompat.resolveItem(bdx));
		}).getType();
	}

	public static Material getMaterialOrNull(String bdx)
	{
		String bd = bdx.trim().toUpperCase();

		return typesb.get(bd, (k) ->
		{
			try
			{
				return FastBlockData.of(Material.valueOf(k));
			}

			catch(Throwable e)
			{

			}

			return null;
		}).getType();
	}

	public static boolean isSolid(FastBlockData mat)
	{
		return isSolid(mat.getMaterial());
	}

	public static boolean isSolid(Material mat)
	{
		if(!solid.containsKey(mat))
		{
			solid.put(mat, mat.isSolid());
		}

		return solid.get(mat);
	}

	public static FastBlockData mat(String bd)
	{
		return getBlockData(bd);
	}

	public static FastBlockData getBlockData(String bd)
	{
		return getBlockData(bd, defaultCompat).optimize();
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

	public static FastBlockData getBlockData(String bdxf, IrisDimension resolver)
	{
		try
		{
			String bd = bdxf.trim();
			FastBlockData fff = bdc.get(bd);
			if(fff != null)
			{
				return fff.clone();
			}

			FastBlockData bdx = parseBlockData(bd);

			if(bdx == null)
			{
				bdx = resolver.resolveBlock(bd);
			}

			if(bdx == null)
			{
				Iris.warn("Unknown Block Data '" + bd + "'");
				nulls.add(bd);
				return bdx;
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

	public static FastBlockData parseBlockDataOrNull(String ix)
	{
		try
		{
			FastBlockData bx = FastBlockData.of(Bukkit.createBlockData(ix));

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
			return FastBlockData.of(Material.valueOf(i));
		}

		catch(Throwable e)
		{

		}

		return null;
	}

	public static FastBlockData parseBlockData(String ix)
	{
		try
		{
			FastBlockData bx = FastBlockData.of(Bukkit.createBlockData(ix));

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
			return FastBlockData.of(Material.valueOf(i));
		}

		catch(Throwable e)
		{

		}

		return AIR;
	}

	public static boolean isStorage(FastBlockData mat)
	{
		if(storage.contains(mat))
		{
			return true;
		}

		// @NoArgsConstructor
		boolean str = mat.equals(B.mat("CHEST")) || mat.equals(B.mat("TRAPPED_CHEST")) || mat.equals(B.mat("SHULKER_BOX")) || mat.equals(B.mat("WHITE_SHULKER_BOX")) || mat.equals(B.mat("ORANGE_SHULKER_BOX")) || mat.equals(B.mat("MAGENTA_SHULKER_BOX")) || mat.equals(B.mat("LIGHT_BLUE_SHULKER_BOX")) || mat.equals(B.mat("YELLOW_SHULKER_BOX")) || mat.equals(B.mat("LIME_SHULKER_BOX")) || mat.equals(B.mat("PINK_SHULKER_BOX")) || mat.equals(B.mat("GRAY_SHULKER_BOX")) || mat.equals(B.mat("LIGHT_GRAY_SHULKER_BOX")) || mat.equals(B.mat("CYAN_SHULKER_BOX")) || mat.equals(B.mat("PURPLE_SHULKER_BOX")) || mat.equals(B.mat("BLUE_SHULKER_BOX")) || mat.equals(B.mat("BROWN_SHULKER_BOX")) || mat.equals(B.mat("GREEN_SHULKER_BOX")) || mat.equals(B.mat("RED_SHULKER_BOX")) || mat.equals(B.mat("BLACK_SHULKER_BOX")) || mat.equals(B.mat("BARREL")) || mat.equals(B.mat("DISPENSER")) || mat.equals(B.mat("DROPPER")) || mat.equals(B.mat("HOPPER")) || mat.equals(B.mat("FURNACE")) || mat.equals(B.mat("BLAST_FURNACE")) || mat.equals(B.mat("SMOKER"));
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

	public static boolean isStorageChest(FastBlockData mat)
	{
		if(storageChest.contains(mat))
		{
			return true;
		}

		// @NoArgsConstructor
		boolean str = mat.equals(B.mat("CHEST")) || mat.equals(B.mat("TRAPPED_CHEST")) || mat.equals(B.mat("SHULKER_BOX")) || mat.equals(B.mat("WHITE_SHULKER_BOX")) || mat.equals(B.mat("ORANGE_SHULKER_BOX")) || mat.equals(B.mat("MAGENTA_SHULKER_BOX")) || mat.equals(B.mat("LIGHT_BLUE_SHULKER_BOX")) || mat.equals(B.mat("YELLOW_SHULKER_BOX")) || mat.equals(B.mat("LIME_SHULKER_BOX")) || mat.equals(B.mat("PINK_SHULKER_BOX")) || mat.equals(B.mat("GRAY_SHULKER_BOX")) || mat.equals(B.mat("LIGHT_GRAY_SHULKER_BOX")) || mat.equals(B.mat("CYAN_SHULKER_BOX")) || mat.equals(B.mat("PURPLE_SHULKER_BOX")) || mat.equals(B.mat("BLUE_SHULKER_BOX")) || mat.equals(B.mat("BROWN_SHULKER_BOX")) || mat.equals(B.mat("GREEN_SHULKER_BOX")) || mat.equals(B.mat("RED_SHULKER_BOX")) || mat.equals(B.mat("BLACK_SHULKER_BOX")) || mat.equals(B.mat("BARREL")) || mat.equals(B.mat("DISPENSER")) || mat.equals(B.mat("DROPPER")) || mat.equals(B.mat("HOPPER"));
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

	public static boolean isLit(FastBlockData mat)
	{
		if(lit.contains(mat))
		{
			return true;
		}

		// @NoArgsConstructor
		boolean str = mat.equals(B.mat("GLOWSTONE")) || mat.equals(B.mat("END_ROD")) || mat.equals(B.mat("SOUL_SAND")) || mat.equals(B.mat("TORCH")) || mat.getType().equals(Material.REDSTONE_TORCH) || mat.equals(B.mat("SOUL_TORCH")) || mat.getType().equals(Material.REDSTONE_WALL_TORCH) || mat.getType().equals(Material.WALL_TORCH) || mat.equals(B.mat("SOUL_WALL_TORCH")) || mat.equals(B.mat("LANTERN")) || mat.getType().equals(Material.JACK_O_LANTERN) || mat.getType().equals(Material.REDSTONE_LAMP) || mat.getType().equals(Material.MAGMA_BLOCK) || mat.equals(B.mat("SHROOMLIGHT")) || mat.equals(B.mat("SEA_LANTERN")) || mat.equals(B.mat("SOUL_LANTERN")) || mat.getType().equals(Material.FIRE) || mat.equals(B.mat("SOUL_FIRE")) || mat.equals(B.mat("SEA_PICKLE")) || mat.getType().equals(Material.BREWING_STAND) || mat.getType().equals(Material.REDSTONE_ORE);
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

	public static boolean isUpdatable(FastBlockData mat)
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

	public static boolean isFoliage(FastBlockData d)
	{
		if(isFluid(d) || isAir(d) || isSolid(d))
		{
			return false;
		}

		FastBlockData mat = d;
		// @NoArgsConstructor
		return mat.getType().equals(Material.POPPY) || mat.getType().equals(Material.DANDELION) || mat.equals(B.mat("CORNFLOWER")) || mat.equals(B.mat("SWEET_BERRY_BUSH")) || mat.equals(B.mat("CRIMSON_ROOTS")) || mat.equals(B.mat("WARPED_ROOTS")) || mat.equals(B.mat("NETHER_SPROUTS")) || mat.equals(B.mat("ALLIUM")) || mat.equals(B.mat("AZURE_BLUET")) || mat.equals(B.mat("BLUE_ORCHID")) || mat.equals(B.mat("POPPY")) || mat.equals(B.mat("DANDELION")) || mat.equals(B.mat("OXEYE_DAISY")) || mat.equals(B.mat("LILY_OF_THE_VALLEY")) || mat.equals(B.mat("WITHER_ROSE")) || mat.getType().equals(Material.DARK_OAK_SAPLING) || mat.getType().equals(Material.ACACIA_SAPLING) || mat.getType().equals(Material.JUNGLE_SAPLING) || mat.getType().equals(Material.BIRCH_SAPLING) || mat.getType().equals(Material.SPRUCE_SAPLING) || mat.getType().equals(Material.OAK_SAPLING) || mat.getType().equals(Material.ORANGE_TULIP) || mat.getType().equals(Material.PINK_TULIP) || mat.getType().equals(Material.RED_TULIP) || mat.getType().equals(Material.WHITE_TULIP) || mat.getType().equals(Material.FERN) || mat.getType().equals(Material.LARGE_FERN) || mat.getType().equals(Material.GRASS) || mat.getType().equals(Material.TALL_GRASS);
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

	public static boolean isDecorant(FastBlockData m)
	{
		if(decorant.contains(m))
		{
			return true;
		}

		// @NoArgsConstructor
		boolean str = m.getType().equals(Material.GRASS) || m.getType().equals(Material.TALL_GRASS) || m.equals(B.mat("CORNFLOWER")) || m.getType().equals(Material.SUNFLOWER) || m.getType().equals(Material.CHORUS_FLOWER) || m.getType().equals(Material.POPPY) || m.getType().equals(Material.DANDELION) || m.getType().equals(Material.OXEYE_DAISY) || m.getType().equals(Material.ORANGE_TULIP) || m.getType().equals(Material.PINK_TULIP) || m.getType().equals(Material.RED_TULIP) || m.getType().equals(Material.WHITE_TULIP) || m.getType().equals(Material.LILAC) || m.getType().equals(Material.DEAD_BUSH) || m.equals(B.mat("SWEET_BERRY_BUSH")) || m.getType().equals(Material.ROSE_BUSH) || m.equals(B.mat("WITHER_ROSE")) || m.getType().equals(Material.ALLIUM) || m.getType().equals(Material.BLUE_ORCHID) || m.equals(B.mat("LILY_OF_THE_VALLEY")) || m.equals(B.mat("CRIMSON_FUNGUS")) || m.equals(B.mat("WARPED_FUNGUS")) || m.getType().equals(Material.RED_MUSHROOM) || m.getType().equals(Material.BROWN_MUSHROOM) || m.equals(B.mat("CRIMSON_ROOTS")) || m.equals(B.mat("AZURE_BLUET")) || m.equals(B.mat("WEEPING_VINES")) || m.equals(B.mat("WEEPING_VINES_PLANT")) || m.equals(B.mat("WARPED_ROOTS")) || m.equals(B.mat("NETHER_SPROUTS")) || m.equals(B.mat("TWISTING_VINES")) || m.equals(B.mat("TWISTING_VINES_PLANT")) || m.getType().equals(Material.SUGAR_CANE) || m.getType().equals(Material.WHEAT) || m.getType().equals(Material.POTATOES) || m.getType().equals(Material.CARROTS) || m.getType().equals(Material.BEETROOTS) || m.getType().equals(Material.NETHER_WART) || m.equals(B.mat("SEA_PICKLE")) || m.equals(B.mat("SEAGRASS")) || m.equals(B.mat("ACACIA_BUTTON")) || m.equals(B.mat("BIRCH_BUTTON")) || m.equals(B.mat("CRIMSON_BUTTON")) || m.equals(B.mat("DARK_OAK_BUTTON")) || m.equals(B.mat("JUNGLE_BUTTON")) || m.equals(B.mat("OAK_BUTTON")) || m.equals(B.mat("POLISHED_BLACKSTONE_BUTTON")) || m.equals(B.mat("SPRUCE_BUTTON")) || m.equals(B.mat("STONE_BUTTON")) || m.equals(B.mat("WARPED_BUTTON")) || m.getType().equals(Material.TORCH) || m.equals(B.mat("SOUL_TORCH"));
		//@done

		if(str)
		{
			decorant.add(m);
			return true;
		}

		return false;
	}

	public static KList<FastBlockData> getBlockData(KList<String> find)
	{
		KList<FastBlockData> b = new KList<>();

		for(String i : find)
		{
			FastBlockData bd = getBlockData(i);

			if(bd != null)
			{
				b.add(bd);
			}
		}

		return b;
	}

	public static boolean isFoliagePlantable(FastBlockData d)
	{
		return d.getMaterial().equals(Material.GRASS_BLOCK) || d.getMaterial().equals(Material.DIRT) || d.getMaterial().equals(Material.COARSE_DIRT) || d.getMaterial().equals(Material.PODZOL);
	}

	public static boolean isFluid(FastBlockData d)
	{
		return d.getMaterial().equals(Material.WATER) || d.getMaterial().equals(Material.LAVA);
	}

	public static boolean isAirOrFluid(FastBlockData d)
	{
		return isAir(d) || isFluid(d);
	}

	public static boolean isAir(FastBlockData d)
	{
		if(d == null)
		{
			return true;
		}

		return d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR) || d.getMaterial().equals(Material.VOID_AIR);
	}
}
