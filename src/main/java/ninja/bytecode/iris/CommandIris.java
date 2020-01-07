package ninja.bytecode.iris;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.spec.IrisBiome;

public class CommandIris implements CommandExecutor
{
	public void msg(CommandSender s, String msg)
	{
		s.sendMessage(ChatColor.DARK_PURPLE + "[" + ChatColor.GRAY + "Iris" + ChatColor.DARK_PURPLE + "]" + ChatColor.GRAY + ": " + msg);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		if(args.length == 0)
		{
			msg(sender, "/iris rtp [biome] - RTP to a biome");
			msg(sender, "/iris gen - Gen a new Iris World");
			msg(sender, "/ish - Iris Schematic Commands");
		}

		if(args.length > 0)
		{
			if(args[0].equalsIgnoreCase("rtp"))
			{
				if(sender instanceof Player)
				{
					Player p = (Player) sender;
					World w = p.getWorld();

					if(w.getGenerator() instanceof IrisGenerator)
					{
						if(args.length > 1)
						{
							IrisGenerator g = (IrisGenerator) w.getGenerator();
							IrisBiome b = null;
							for(IrisBiome i : g.getLoadedBiomes())
							{
								if(args[1].toLowerCase().equals(i.getName().toLowerCase().replaceAll("\\Q \\E", "_")))
								{
									b = i;
									break;
								}
							}

							if(b == null)
							{
								msg(sender, "Unknown Biome: " + args[1]);
							}

							else
							{
								msg(sender, "Looking for " + b.getName() + "...");
								boolean f = false;
								for(int i = 0; i < 10000; i++)
								{
									int x = (int) ((int) (29999983 / 1.2) * Math.random());
									int z = (int) ((int) (29999983 / 1.2) * Math.random());

									if(g.getBiome(x, z).equals(b))
									{
										f = true;
										p.teleport(w.getHighestBlockAt(x, z).getLocation());
										break;
									}
								}

								if(!f)
								{
									msg(sender, "Looked for " + b.getName() + " in 10,000 different locations and could not find it. Try again!");
								}
							}
						}

						else
						{
							int x = (int) ((int) (29999983 / 1.2) * Math.random());
							int z = (int) ((int) (29999983 / 1.2) * Math.random());
							p.teleport(w.getHighestBlockAt(x, z).getLocation());
						}
					}
				}
			}

			if(args[0].equalsIgnoreCase("gen"))
			{
				if(sender instanceof Player)
				{
					World wold = ((Player) sender).getWorld();
					World w = Iris.instance.createIrisWorld();
					((Player) sender).teleport(new Location(w, 0, 256, 0));
					((Player) sender).setFlying(true);
					((Player) sender).setGameMode(GameMode.CREATIVE);
					wold.setAutoSave(false);
					Bukkit.unloadWorld(wold, false);
				}

				else
				{
					Iris.instance.createIrisWorld();
				}
			}
		}

		return false;
	}
}
