package ninja.bytecode.iris;

import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import mortar.api.nms.NMP;
import mortar.api.sched.J;
import mortar.util.text.C;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.WorldReactor;
import ninja.bytecode.iris.generator.genobject.PlacedObject;
import ninja.bytecode.iris.pack.CompiledDimension;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.BiomeLayer;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.format.F;
import ninja.bytecode.shuriken.logging.L;

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
			msg(sender, "/iris otp [schematic] - RTP to a specific schematic");
			msg(sender, "/iris info - Chunk info");
			msg(sender, "/iris hotload - Recompile pack & inject into worlds");
			msg(sender, "/iris reload - Reload & Recompile");
			msg(sender, "/iris clean - Clean Pack Install in Iris Folder");
			msg(sender, "/ish - Iris Schematic Commands");
		}

		if(args.length > 0)
		{
			if(args[0].equalsIgnoreCase("timings"))
			{
				if(sender instanceof Player)
				{
					Player p = (Player) sender;
					World w = p.getWorld();

					if(w.getGenerator() instanceof IrisGenerator)
					{
						((IrisGenerator) w.getGenerator()).getMetrics().send(p, (m) -> msg(p, m));
					}

					else
					{
						msg(p, "You must be in an iris world for this");
					}
				}
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
						IrisBiome biome = g.getBiome((int) g.getOffsetX(p.getLocation().getX(), p.getLocation().getZ()), (int) g.getOffsetZ(p.getLocation().getX(), p.getLocation().getZ()));
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

			if(args[0].equalsIgnoreCase("otp"))
			{
				if(sender instanceof Player)
				{
					Player p = (Player) sender;
					World w = p.getWorld();

					if(w.getGenerator() instanceof IrisGenerator)
					{
						if(args.length >= 2)
						{
							PlacedObject o = ((IrisGenerator) w.getGenerator()).randomObject(args[1]);

							if(o != null)
							{
								Location l = new Location(w, o.getX(), o.getY(), o.getZ());
								p.teleport(l);
								msg(p, "Found " + C.DARK_GREEN + o.getF().replace(":", "/" + C.WHITE));
							}

							else
							{
								msg(p, "Found Nothing");
							}
						}

						else
						{
							msg(p, "/iris otp <object/group>");
						}
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

									if(g.getBiome((int) g.getOffsetX(x, z), (int) g.getOffsetZ(x, z)).equals(b))
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

			if(args[0].equalsIgnoreCase("hotload"))
			{
				msg(sender, "=== Hotloading Pack ===");
				PackController c = Iris.getController(PackController.class);
				GMap<String, String> f = new GMap<>();

				for(World i : Bukkit.getWorlds())
				{
					if(i.getGenerator() instanceof IrisGenerator)
					{
						String n = ((IrisGenerator) i.getGenerator()).getDimension().getName();
						msg(sender, "Preparing " + n);
						f.put(i.getName(), n);
					}
				}

				if(f.isEmpty())
				{
					msg(sender, "No Worlds to inject!");
					return true;
				}

				J.a(() ->
				{
					try
					{
						Consumer<String> m = (msg) ->
						{
							J.s(() ->
							{
								String mm = msg;

								if(msg.contains("|"))
								{
									GList<String> fx = new GList<>();
									fx.add(msg.split("\\Q|\\E"));
									fx.remove(0);
									fx.remove(0);
									mm = fx.toString("");
								}

								msg(sender, mm.replaceAll("\\Q  \\E", ""));
							});
						};
						L.addLogConsumer(m);
						c.compile();
						L.logConsumers.remove(m);

						J.s(() ->
						{
							if(sender instanceof Player)
							{
								ChronoLatch cl = new ChronoLatch(3000);
								Player p = (Player) sender;
								World ww = ((Player) sender).getWorld();

								msg(p, "Regenerating View Distance");

								WorldReactor r = new WorldReactor(ww);
								r.generateRegionNormal(p, true, 200, (pct) ->
								{
									if(cl.flip())
									{
										msg(p, "Regenerating " + F.pc(pct));
									}
								}, () ->
								{
									msg(p, "Done! Use F3 + A");
								});
							}
						}, 5);

						for(String fi : f.k())
						{
							J.s(() ->
							{
								World i = Bukkit.getWorld(fi);
								CompiledDimension dim = c.getDimension(f.get(fi));

								for(String k : c.getDimensions().k())
								{
									if(c.getDimension(k).getName().equals(f.get(fi)))
									{
										dim = c.getDimension(k);
										break;
									}
								}

								if(dim == null)
								{
									J.s(() -> msg(sender, "Cannot find dimnension: " + f.get(fi)));
									return;
								}
								msg(sender, "Hotloaded " + i.getName());
								IrisGenerator g = ((IrisGenerator) i.getGenerator());
								g.inject(dim);
							});
						}
					}

					catch(Throwable e)
					{
						e.printStackTrace();
					}
				});

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
