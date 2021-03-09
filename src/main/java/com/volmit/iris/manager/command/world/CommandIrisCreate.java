package com.volmit.iris.manager.command.world;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.manager.link.MultiverseCoreLink;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.pregen.Pregenerator;
import com.volmit.iris.scaffold.IrisWorldCreator;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;

public class CommandIrisCreate extends MortarCommand
{
	public CommandIrisCreate()
	{
		super("create", "c", "cr", "new", "+");
		requiresPermission(Iris.perm.studio);
		setCategory("Create");
		setDescription("Create a new Iris World!");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args.length < 1)
		{
			sender.sendMessage("/iris create <NAME> [type=overworld] [seed=1337] [pregen=5000]");
			return true;
		}

		String worldName = args[0];
		String type = IrisSettings.get().getGenerator().getDefaultWorldType();
		long seed = 1337;
		int pregen = 0;
		boolean multiverse = Iris.linkMultiverseCore.supported();

		for(String i : args)
		{
			type = i.startsWith("type=") ? i.split("\\Q=\\E")[1] : type;
			seed = i.startsWith("seed=") ? Long.valueOf(i.split("\\Q=\\E")[1]) : seed;
			pregen = i.startsWith("pregen=") ? getVal(i.split("\\Q=\\E")[1]) : pregen;
		}

		Iris.linkMultiverseCore.assignWorldType(worldName, type);
		World world = null;
		IrisDimension dim;
		File folder = new File(worldName);
		if(multiverse)
		{
			dim = IrisDataManager.loadAnyDimension(type);

			if(dim == null)
			{
				sender.sendMessage("Cant find dimension type: " + type + ". Did you forget to /ir download " + type + "?");
				return true;
			}

			if(dim.getEnvironment() == null)
			{
				dim.setEnvironment(World.Environment.NORMAL);
			}

			if(Iris.linkMultiverseCore == null)
			{
				Iris.linkMultiverseCore = new MultiverseCoreLink();
			}

			String command = "mv create " + worldName + " " + Iris.linkMultiverseCore.envName(dim.getEnvironment());
			command += " -s " + seed;
			command += " -g Iris:" + dim.getLoadKey();
			sender.sendMessage("Delegating " + command);
			Bukkit.dispatchCommand(sender, command);
			world= Bukkit.getWorld(worldName);
		}

		else
		{
			if(folder.exists())
			{
				sender.sendMessage("That world folder already exists!");
				return true;
			}

			File iris = new File(folder, "iris");
			iris.mkdirs();

			dim = Iris.proj.installIntoWorld(sender, type, folder);

			WorldCreator wc = new IrisWorldCreator().dimension(dim).name(worldName)
					.productionMode().seed(seed).create();
			sender.sendMessage("Generating with " + Iris.getThreadCount() + " threads per chunk");
			O<Boolean> done = new O<Boolean>();
			done.set(false);

			J.a(() ->
			{
				double last = 0;
				int req = 800;
				while(!done.get())
				{
					boolean derp = false;
					double v = (double) ((IrisAccess) wc.generator()).getGenerated() / (double) req;

					if(last > v || v > 1)
					{
						derp = true;
						v = last;
					}

					else
					{
						last = v;
					}

					sender.sendMessage("Generating " + Form.pc(v) + (derp ? " (Waiting on Server...)" : ""));
					J.sleep(3000);
				}
			});

			world = wc.createWorld();

			done.set(true);
		}


		sender.sendMessage(worldName + " Spawn Area generated.");
		sender.sendMessage("You must remember to either have multiverse installed or use the Bukkit method, otherwise the world will go corrupt!");
		sender.sendMessage("Wiki: https://volmitsoftware.gitbook.io/iris/getting-started");

		O<Boolean> b = new O<Boolean>();
		b.set(true);

		if(sender.isPlayer())
		{
			try
			{
				sender.player().teleport(world.getSpawnLocation());
			}

			catch(Throwable e)
			{

			}
		}

		if(pregen > 0)
		{
			b.set(false);
			sender.sendMessage("Pregenerating " + worldName + " " + pregen + " x " + pregen);
			sender.sendMessage("Expect server lag during this time. Use '/iris pregen stop' to cancel");

			new Pregenerator(world, pregen, () ->
			{
				b.set(true);
			});
		}

		World ww = world;
		if (ww == null){
			sender.sendMessage("World not created, can not finish");
			return true;
		}
		J.a(() ->
		{
			while(!b.get())
			{
				J.sleep(1000);
			}


			Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
			{
				ww.save();
				sender.sendMessage("All Done!");
			});
		});

		return true;
	}

	private int getVal(String arg) {

		if(arg.toLowerCase().endsWith("c") || arg.toLowerCase().endsWith("chunks"))
		{
			return Integer.parseInt(arg.toLowerCase().replaceAll("\\Qc\\E", "").replaceAll("\\Qchunks\\E", "")) * 16;
		}

		if(arg.toLowerCase().endsWith("r") || arg.toLowerCase().endsWith("regions"))
		{
			return Integer.parseInt(arg.toLowerCase().replaceAll("\\Qr\\E", "").replaceAll("\\Qregions\\E", "")) * 512;
		}

		if(arg.toLowerCase().endsWith("k"))
		{
			return Integer.parseInt(arg.toLowerCase().replaceAll("\\Qk\\E", "")) * 1000;
		}

		return Integer.parseInt(arg.toLowerCase());
	}

	@Override
	protected String getArgsUsage()
	{
		return "<name> [type=overworld] [seed=1337] [pregen=5000]";
	}
}
