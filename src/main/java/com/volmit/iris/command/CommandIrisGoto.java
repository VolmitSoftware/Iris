package com.volmit.iris.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.command.util.MortarCommand;
import com.volmit.iris.command.util.MortarSender;
import com.volmit.iris.generator.IrisChunkGenerator;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.RNG;

public class CommandIrisGoto extends MortarCommand
{
	public CommandIrisGoto()
	{
		super("goto", "find");
		setDescription("Find any biome or a biome border");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args.length <= 1)
		{
			sender.sendMessage("/iris world goto " + getArgsUsage());
			return true;
		}

		if(sender.isPlayer())
		{
			Player p = sender.player();
			World world = p.getWorld();

			if(!(world.getGenerator() instanceof IrisChunkGenerator))
			{
				sender.sendMessage("You must be in an iris world.");
				return true;
			}

			IrisChunkGenerator g = (IrisChunkGenerator) world.getGenerator();
			int tries = 10000;
			boolean cave = false;
			IrisBiome biome2 = null;
			if(args.length > 2)
			{
				if(args[2].equalsIgnoreCase("-cave"))
				{
					cave = true;
				}

				else
				{
					biome2 = Iris.data.getBiomeLoader().load(args[2]);

					if(biome2 == null)
					{
						sender.sendMessage(args[2] + " is not a biome. Use the file name (without extension)");
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

			IrisBiome biome = Iris.data.getBiomeLoader().load(args[1]);

			if(biome == null)
			{
				sender.sendMessage(args[1] + " is not a biome. Use the file name (without extension)");
				return true;
			}

			while(tries > 0)
			{
				tries--;

				int xx = (int) (RNG.r.i(-29999970, 29999970));
				int zz = (int) (RNG.r.i(-29999970, 29999970));
				if((cave ? g.sampleCaveBiome(xx, zz) : g.sampleTrueBiome(xx, zz)).getBiome().getLoadKey().equals(biome.getLoadKey()))
				{
					if(biome2 != null)
					{
						for(int i = 0; i < 64; i++)
						{
							int ax = xx + RNG.r.i(-64, 32);
							int az = zz + RNG.r.i(-64, 32);

							if((cave ? g.sampleCaveBiome(ax, az) : g.sampleTrueBiome(ax, az)).getBiome().getLoadKey().equals(biome2.getLoadKey()))
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
