// 
// Decompiled by Procyon v0.5.36
// 

package ninja.bytecode.iris.legacy;

import java.util.EnumSet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.java.JavaPlugin;

public class IDLibrary
{
	public static final String SEPARATOR = ":";
	private static final String Spawn_Egg_Id = "383";
	private static final int Spawn_Egg_Id_I = Integer.parseInt("383");
	private static final IDList List = new IDList();

	public static boolean isInt(final String ID)
	{
		try
		{
			Integer.parseInt(ID);
			return true;
		}
		catch(Exception e)
		{
			return false;
		}
	}

	private static boolean isLegacy()
	{
		try
		{
			Material.valueOf("CONDUIT");
			return false;
		}
		catch(Exception e)
		{
			return true;
		}
	}

	public static Material getMaterial(String ID)
	{
		if(isLegacy())
		{
			final ItemStack IS = getItemStack(ID);
			return (IS == null) ? null : IS.getType();
		}
		ID = ID.replace(" ", "").toUpperCase();
		Material t = List.getMaterial(ID.contains(":") ? ID : (String.valueOf(ID) + ":" + "0"));
		if(t != null)
		{
			return t;
		}
		try
		{
			t = Material.valueOf(ID);
			if(List.getIDData(t) != null)
			{
				return t;
			}
		}
		catch(Exception ex)
		{
		}
		if(ID.startsWith("383"))
		{
			return null;
		}
		if(!ID.contains(":") && !isInt(ID))
		{
			try
			{
				t = Material.valueOf(ID);
				if(t != null)
				{
					return t;
				}
			}
			catch(Exception ex2)
			{
			}
		}
		if(ID.contains(":"))
		{
			final String[] IDs = ID.split(":");
			try
			{
				return getMaterial(Integer.parseInt(IDs[0]), Byte.parseByte(IDs[1]));
			}
			catch(Exception e)
			{
				final Material m = Material.getMaterial("LEGACY_" + IDs[0], false);
				try
				{
					return (m == null) ? m : getMaterial(m.getId(), Byte.parseByte(IDs[1]));
				}
				catch(Exception e2)
				{
					return null;
				}
			}
		}
		try
		{
			return getMaterial(Integer.parseInt(ID));
		}
		catch(Exception e3)
		{
			final Material i = Material.getMaterial("LEGACY_" + ID, false);
			return (i == null) ? i : getMaterial(i.getId(), (byte) 0);
		}
	}

	public static Material getMaterial(final int ID)
	{
		return getMaterial(ID, (byte) 0);
	}

	public static Material getMaterial(final int ID, final byte Data)
	{
		for(final Material i : EnumSet.allOf(Material.class))
		{
			try
			{
				if(i.getId() == ID)
				{
					final Material m = Bukkit.getUnsafe().fromLegacy(new MaterialData(i, Data));
					return ((m == Material.AIR && (ID != 0 || Data != 0)) || (Data != 0 && m == Bukkit.getUnsafe().fromLegacy(new MaterialData(i, (byte) 0)))) ? List.getMaterial(String.valueOf(ID) + ":" + Data) : m;
				}
				continue;
			}
			catch(IllegalArgumentException ex)
			{
			}
		}
		return null;
	}

	public static ItemStack getItemStack(String ID)
	{
		if(isLegacy())
		{
			final ItemStack IS = null;
			ID = ID.replace(" ", "").toUpperCase();
			if(!ID.contains(":"))
			{
				ID = String.valueOf(ID) + ":" + "0";
			}
			final String[] I = ID.split(":");
			int id = 0;
			try
			{
				id = Integer.parseInt(I[0]);
			}
			catch(NumberFormatException e)
			{
				try
				{
					id = Material.valueOf(I[0]).getId();
				}
				catch(IllegalArgumentException e2)
				{
					return IS;
				}
			}
			try
			{
				for(final Material i : EnumSet.allOf(Material.class))
				{
					if(i.getId() == id)
					{
						return new ItemStack(i, 1, (short) Integer.parseInt(I[1]));
					}
				}
			}
			catch(Exception ex)
			{
			}
			return IS;
		}
		final Material M = getMaterial(ID);
		return (M == null) ? null : new ItemStack(getMaterial(ID), 1);
	}

	public static String getIDData(final Material M)
	{
		final byte d = getData(M);
		return String.valueOf(getID(M)) + ((d == 0) ? "" : (":" + d));
	}

	public static int getID(final Material M)
	{
		if(isLegacy())
		{
			M.getId();
		}
		final int d = List.getID(M);
		if(d != -1)
		{
			return d;
		}
		final int i = Bukkit.getUnsafe().toLegacy(M).getId();
		return (i != Spawn_Egg_Id_I && (i != 0 || (i == 0 && M == Material.AIR))) ? i : 0;
	}

	public static byte getData(final Material M)
	{
		if(isLegacy())
		{
			return 0;
		}
		final byte d = List.getData(M);
		if(d != -1)
		{
			return d;
		}
		final int i = Bukkit.getUnsafe().toLegacy(M).getId();
		return (byte) ((i != Spawn_Egg_Id_I && (i != 0 || (i == 0 && M == Material.AIR))) ? getData(M, i, Bukkit.getUnsafe().toLegacy(M)) : 0);
	}

	private static byte getData(final Material M, final int ID, final Material O)
	{
		for(final Material i : EnumSet.allOf(Material.class))
		{
			try
			{
				if(i.getId() != ID)
				{
					continue;
				}
				for(byte i2 = 0; i2 <= 15; ++i2)
				{
					if(M.equals((Object) Bukkit.getUnsafe().fromLegacy(new MaterialData(i, i2))))
					{
						return i2;
					}
				}
			}
			catch(IllegalArgumentException ex)
			{
			}
		}
		return 0;
	}

	public static String getIDData(final ItemStack IS)
	{
		final byte d = getData(IS);
		return String.valueOf(getID(IS)) + ((d == 0) ? "" : (":" + d));
	}

	public static int getID(final ItemStack IS)
	{
		if(!isLegacy())
		{
			return getID(IS.getType());
		}
		return IS.getType().getId();
	}

	public static byte getData(final ItemStack IS)
	{
		if(!isLegacy())
		{
			return getData(IS.getType());
		}
		return IS.getData().getData();
	}
}
