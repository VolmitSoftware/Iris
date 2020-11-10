package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.generator.legacy.nms.INMS;
import com.volmit.iris.object.IrisDimension;
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
		super("create", "new", "+");
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
			sender.sendMessage("/iris create <NAME> [type=overworld] [seed=1337] [pregen=5000] [-zip]");
			return true;
		}

		String worldName = args[0];
		String type = "overworld";
		long seed = 1337;
		int pregen = 0;
		File folder = new File(worldName);

		if(folder.exists())
		{
			sender.sendMessage("That world folder already exists!");
			return true;
		}

		File iris = new File(folder, "iris");
		iris.mkdirs();

		for(String i : args)
		{
			type = i.startsWith("type=") ? i.split("\\Q=\\E")[1] : type;
			seed = i.startsWith("seed=") ? Long.valueOf(i.split("\\Q=\\E")[1]) : seed;
			pregen = i.startsWith("pregen=") ? Integer.parseInt(i.split("\\Q=\\E")[1]) : pregen;
		}

		IrisDimension dim = Iris.proj.installIntoWorld(sender, type, folder);

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

		World world = INMS.get().createWorld(wc, false);

		done.set(true);
		sender.sendMessage(worldName + " Spawn Area generated.");

		O<Boolean> b = new O<Boolean>();
		b.set(true);

		if(pregen > 0)
		{
			b.set(false);
			sender.sendMessage("Pregenerating " + worldName + " " + pregen + " x " + pregen);
			sender.sendMessage("Expect Extreme server lag during this time. Use '/iris world pregen stop' to cancel");

			new PregenJob(world, pregen, sender, () ->
			{
				b.set(true);
			});
		}

		IrisDimension dimm = dim;
		long seedd = seed;

		if(Iris.linkMultiverseCore.supported())
		{
			Iris.linkMultiverseCore.addWorld(worldName, dimm, seedd + "");
			sender.sendMessage("Added " + worldName + " to MultiverseCore.");
		}

		J.a(() ->
		{
			while(!b.get())
			{
				J.sleep(1000);
			}

			Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
			{
				world.save();

				sender.sendMessage("All Done!");
			});
		});

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "<name> [type=overworld] [seed=1337] [pregen=5000] [-zip]";
	}
}
