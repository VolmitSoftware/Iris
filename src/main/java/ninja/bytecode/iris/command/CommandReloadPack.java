package ninja.bytecode.iris.command;

import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import mortar.api.sched.J;
import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.logic.queue.ChronoLatch;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.WorldReactor;
import ninja.bytecode.iris.pack.CompiledDimension;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.format.Form;
import ninja.bytecode.shuriken.logging.L;

public class CommandReloadPack extends MortarCommand
{
	public CommandReloadPack()
	{
		super("pack", "p");
		setDescription("Reloads the pack + regen");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		sender.sendMessage("=== Hotloading Pack ===");
		PackController c = Iris.pack();
		KMap<String, String> f = new KMap<>();

		for(World i : Bukkit.getWorlds())
		{
			if(i.getGenerator() instanceof IrisGenerator)
			{
				String n = ((IrisGenerator) i.getGenerator()).getDimension().getName();
				sender.sendMessage("Preparing " + n);
				f.put(i.getName(), n);
			}
		}

		if(f.isEmpty())
		{
			sender.sendMessage("No Worlds to inject!");
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
							KList<String> fx = new KList<>();
							fx.add(msg.split("\\Q|\\E"));
							fx.remove(0);
							fx.remove(0);
							mm = fx.toString("");
						}

						sender.sendMessage(mm.replaceAll("\\Q  \\E", ""));
					});
				};
				
				L.addLogConsumer(m);
				c.compile();
				L.logConsumers.remove(m);

				J.s(() ->
				{
					if(sender.isPlayer())
					{
						ChronoLatch cl = new ChronoLatch(3000);
						Player p = sender.player();
						World ww = sender.player().getWorld();

						sender.sendMessage("Regenerating View Distance");

						WorldReactor r = new WorldReactor(ww);
						r.generateRegionNormal(p, true, 200, (pct) ->
						{
							if(cl.flip())
							{
								sender.sendMessage("Regenerating " + Form.pc(pct));
							}
						}, () ->
						{
							sender.sendMessage("Done! Use F3 + A");
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
							J.s(() -> sender.sendMessage("Cannot find dimnension: " + f.get(fi)));
							return;
						}
						sender.sendMessage("Hotloaded " + i.getName());
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

		return true;
	}

}
