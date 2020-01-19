package ninja.bytecode.iris;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import mortar.api.nms.NMP;
import mortar.util.text.C;
import ninja.bytecode.iris.controller.TimingsController;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.BiomeLayer;
import ninja.bytecode.shuriken.format.F;

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

			if(args[0].equalsIgnoreCase("info"))
			{
				if(sender instanceof Player)
				{
					Player p = (Player) sender;
					World w = p.getWorld();

					if(w.getGenerator() instanceof IrisGenerator)
					{
						IrisGenerator g = (IrisGenerator) w.getGenerator();
						IrisBiome biome = g.getBiome((int) g.getOffsetX(p.getLocation().getX()), (int) g.getOffsetZ(p.getLocation().getZ()));
						BiomeLayer l = new BiomeLayer(g, biome);
						msg(p, "Biome: " + C.BOLD + C.WHITE + biome.getName() + C.RESET + C.GRAY + " (" + C.GOLD + l.getBiome().getRarityString() + C.GRAY + ")");

						for(String i : biome.getSchematicGroups().k())
						{
							String f = "";
							double percent = biome.getSchematicGroups().get(i);

							if(percent > 1D)
							{
								f = (int) percent + " + " + F.pc(percent - (int) percent, percent - (int) percent >= 0.01 ? 0 : 3);
							}

							else
							{
								f = F.pc(percent, percent >= 0.01 ? 0 : 3);
							}

							msg(p, "* " + C.DARK_GREEN + i + ": " + C.BOLD + C.WHITE + f + C.RESET + C.GRAY + " (" + F.f(g.getDimension().getObjectGroup(i).size()) + " variants)");
						}
					}

					else
					{
						msg(sender, "Not in an Iris World");
					}
				}
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
							for(IrisBiome i : g.getDimension().getBiomes())
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
								int t = 0;
								for(int i = 0; i < 10000; i++)
								{
									int x = (int) ((int) (29999983 / 1.2) * Math.random());
									int z = (int) ((int) (29999983 / 1.2) * Math.random());

									if(g.getBiome((int) g.getOffsetX(x), (int) g.getOffsetZ(z)).equals(b))
									{
										f = true;

										if(w.getHighestBlockYAt(x, z) > 66)
										{
											p.teleport(w.getHighestBlockAt(x, z).getLocation());
											break;
										}

										else
										{
											t++;

											if(t > 30)
											{
												msg(sender, "Checked 30 " + b.getName() + " bearing chunks. All of them were underwater. Try Again!");
												break;
											}
										}
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

					else
					{
						msg(sender, "Not in an Iris World");
					}
				}
			}

			if(args[0].equalsIgnoreCase("reload"))
			{
				msg(sender, "Reloading Iris...");
				Iris.instance.reload();
			}

			if(args[0].equalsIgnoreCase("refresh"))
			{
				msg(sender, "Sec...");
				Player p = ((Player) sender);

				for(Chunk i : p.getWorld().getLoadedChunks())
				{
					NMP.CHUNK.refresh(p, i);
				}
			}
		}

		return false;
	}
}
