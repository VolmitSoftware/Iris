package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import com.volmit.iris.util.RNG;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CommandIrisStudioGoto extends MortarCommand
{
	public CommandIrisStudioGoto()
	{
		super("goto", "find", "g", "tp");
		setDescription("Find any biome or a biome border");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {
		if(args.length == 0 && sender.isPlayer() && IrisWorlds.isIrisWorld(sender.player().getWorld()))
		{
			list.add(IrisWorlds.access(sender.player().getWorld()).getData().getBiomeLoader().getPreferredKeys());
		}
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		try
		{
			if(args.length < 1)
			{
				sender.sendMessage("/iris std goto " + getArgsUsage());
				return true;
			}

			if(sender.isPlayer())
			{
				Player p = sender.player();
				World world = p.getWorld();

				if(!IrisWorlds.isIrisWorld(world))
				{
					sender.sendMessage("You must be in an iris world.");
					return true;
				}

				IrisAccess g = IrisWorlds.access(world);
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
						biome2 = g.getData().getBiomeLoader().load(args[1]);

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

				IrisBiome biome = args[0].equals("this") ? g.getBiome(p.getLocation().getBlockX(), p.getLocation().getBlockZ()) : g.getData().getBiomeLoader().load(args[0]);

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
					if((cave ? g.getCaveBiome(xx, zz) : g.getBiome(xx, zz)).getLoadKey().equals(biome.getLoadKey()))
					{
						if(biome2 != null)
						{
							for(int i = 0; i < 64; i++)
							{
								int ax = xx + RNG.r.i(-64, 32);
								int az = zz + RNG.r.i(-64, 32);

								if((cave ? g.getBiome(ax, az) : g.getBiome(ax, az)).getLoadKey().equals(biome2.getLoadKey()))
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
		}

		catch(Throwable e)
		{
			Iris.error("Failed goto!");
			e.printStackTrace();
			sender.sendMessage("We cant seem to aquire a lock on the biome cache. Please report the error in the console to our github. Thanks!");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[biome] [otherbiome] [-cave]";
	}
}
