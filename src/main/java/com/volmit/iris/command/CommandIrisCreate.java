package com.volmit.iris.command;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.nms.NMSCreator;
import com.volmit.iris.gen.provisions.ProvisionBukkit;
import com.volmit.iris.gen.scaffold.IrisGenConfiguration;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.J;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import com.volmit.iris.util.O;
import com.volmit.iris.util.PregenJob;

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
	public boolean handle(MortarSender sender, String[] args)
	{
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

		//@builder
		ProvisionBukkit gen = Iris.instance.createProvisionBukkit(
			IrisGenConfiguration.builder()
				.threads(Iris.getThreadCount())
				.dimension(dim.getLoadKey())
				.target(TerrainTarget
					.builder()
					.environment(dim.getEnvironment())
					.folder(folder)
					.name(worldName)
					.seed(seed)
				.build()
			).build());
		//@done

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
				double v = (double) ((IrisTerrainProvider) gen.getProvider()).getGenerated() / (double) req;

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

		WorldCreator wc = new WorldCreator(worldName).seed(seed).generator(gen).type(WorldType.NORMAL).environment(dim.getEnvironment());

		World world = NMSCreator.createWorld(wc, false);

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
