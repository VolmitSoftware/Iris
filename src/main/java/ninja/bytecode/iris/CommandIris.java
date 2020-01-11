package ninja.bytecode.iris;

import java.io.File;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import ninja.bytecode.iris.controller.TimingsController;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.format.F;
import ninja.bytecode.shuriken.io.IO;

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
			msg(sender, "/iris timings - Iris Timings");
			msg(sender, "/iris rtp [biome] - RTP to a biome");
			msg(sender, "/iris reload - Reload & Recompile");
			msg(sender, "/iris clean - Clean Pack Install in Iris Folder");
			msg(sender, "/ish - Iris Schematic Commands");
		}

		if(args.length > 0)
		{
			if(args[0].equalsIgnoreCase("timings"))
			{
				double t = Iris.getController(TimingsController.class).getResult("terrain");
				double d = Iris.getController(TimingsController.class).getResult("decor");
				msg(sender, "Generation: " + ChatColor.BOLD + ChatColor.WHITE + F.duration(t + d, 2));
				msg(sender, " \\Terrain: " + ChatColor.BOLD + ChatColor.WHITE + F.duration(t, 2));
				msg(sender, " \\Decor: " + ChatColor.BOLD + ChatColor.WHITE + F.duration(d, 2));
			}

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

			if(args[0].equalsIgnoreCase("reload"))
			{
				msg(sender, "Reloading Iris...");
				Iris.instance.reload();
			}

			if(args[0].equalsIgnoreCase("clean"))
			{
				msg(sender, "Poof!");

				if(sender instanceof Player)
				{
					((Player) sender).playSound(((Player) sender).getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.33f, (float) 1.65);
				}

				J.attempt(() -> IO.delete(new File(Iris.instance.getDataFolder(), "pack")));
			}
		}

		return false;
	}
}
