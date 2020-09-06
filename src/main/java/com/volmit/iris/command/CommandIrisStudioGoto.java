package com.volmit.iris.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.provisions.ProvisionBukkit;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import com.volmit.iris.util.RNG;

public class CommandIrisStudioGoto extends MortarCommand
{
	public CommandIrisStudioGoto()
	{
		super("goto", "find");
		setDescription("Find any biome or a biome border");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
			return true;
		}

		if(args.length < 1)
		{
			sender.sendMessage("/iris world goto " + getArgsUsage());
			return true;
		}

		if(sender.isPlayer())
		{
			Player p = sender.player();
			World world = p.getWorld();

			if(!(world.getGenerator() instanceof ProvisionBukkit))
			{
				sender.sendMessage("You must be in an iris world.");
				return true;
			}

			IrisTerrainProvider g = (IrisTerrainProvider) ((ProvisionBukkit) world.getGenerator()).getProvider();
			int tries = 10000;
			boolean cave = false;
			IrisBiome biome2 = null;
			if(args.length > 1)
			{
				if(args[1].equalsIgnoreCase("-cave"))
				{
					cave = true;
				}

				else
				{
					biome2 = g.loadBiome(args[1]);

					if(biome2 == null)
					{
						sender.sendMessage(args[1] + " is not a biome. Use the file name (without extension)");
						return true;
					}
				}
			}

			for(String i : args)
			{
				if(i.equalsIgnoreCase("-cave"))
				{
					cave = true;
				}
			}

			IrisBiome biome = args[0].equals("this") ? g.sampleTrueBiome(p.getLocation().getBlockX(), p.getLocation().getBlockZ()) : g.loadBiome(args[0]);

			if(biome == null)
			{
				sender.sendMessage(args[0] + " is not a biome. Use the file name (without extension)");
				return true;
			}

			while(tries > 0)
			{
				tries--;

				int xx = (int) (RNG.r.i(-29999970, 29999970));
				int zz = (int) (RNG.r.i(-29999970, 29999970));
				if((cave ? g.sampleCaveBiome(xx, zz) : g.sampleTrueBiome(xx, zz)).getLoadKey().equals(biome.getLoadKey()))
				{
					if(biome2 != null)
					{
						for(int i = 0; i < 64; i++)
						{
							int ax = xx + RNG.r.i(-64, 32);
							int az = zz + RNG.r.i(-64, 32);

							if((cave ? g.sampleCaveBiome(ax, az) : g.sampleTrueBiome(ax, az)).getLoadKey().equals(biome2.getLoadKey()))
							{
								tries--;
								p.teleport(new Location(world, xx, world.getHighestBlockYAt(xx, zz), zz));
								sender.sendMessage("Found border in " + (10000 - tries) + " tries!");
								return true;
							}
						}
					}

					else
					{
						p.teleport(new Location(world, xx, world.getHighestBlockYAt(xx, zz), zz));
						sender.sendMessage("Found in " + (10000 - tries) + " tries!");
						return true;
					}
				}
			}

			sender.sendMessage("Tried to find " + biome.getName() + " looked in 10,000 places no dice.");

			return true;
		}

		else
		{
			sender.sendMessage("Players only.");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[biome] [otherbiome] [-cave]";
	}
}
