package ninja.bytecode.iris.command;

import org.bukkit.World;
import org.bukkit.entity.Player;

import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.IrisBiome;

public class CommandFindBiome extends MortarCommand
{
	public CommandFindBiome()
	{
		super("biome", "b");
		setDescription("Teleport to a biome by name");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		World w = null;

		if(sender.isPlayer() && sender.player().getWorld().getGenerator() instanceof IrisGenerator)
		{
			w = sender.player().getWorld();
		}

		else
		{
			sender.sendMessage("Console / Non-Iris World.");
			return true;
		}

		Player p = sender.player();
		IrisGenerator g = (IrisGenerator) w.getGenerator();
		if(args.length > 0)
		{
			IrisBiome b = null;
			for(IrisBiome i : g.getDimension().getBiomes())
			{
				if(args[0].toLowerCase().equals(i.getName().toLowerCase().replaceAll("\\Q \\E", "_")))
				{
					b = i;
					break;
				}
			}

			if(b == null)
			{
				sender.sendMessage("Couldn't find any biomes containing '" + args[0] + "'.");
			}

			else
			{
				sender.sendMessage("Looking for Biome " + b.getName() + "...");
				boolean f = false;

				for(int i = 0; i < 10000; i++)
				{
					int x = (int) ((int) (29999983 / 1.2) * Math.random());
					int z = (int) ((int) (29999983 / 1.2) * Math.random());

					if(g.getBiome((int) g.getOffsetX(x, z), (int) g.getOffsetZ(x, z)).equals(b))
					{
						f = true;
						p.teleport(w.getHighestBlockAt(x, z).getLocation());
						break;
					}
				}

				if(!f)
				{
					sender.sendMessage("Couldn't for " + b.getName() + " in 10,000 different locations and could not find it. Try again!");
				}
			}
		}

		else
		{
			sender.sendMessage("/iris find biome <query>");
		}

		return true;
	}

}
