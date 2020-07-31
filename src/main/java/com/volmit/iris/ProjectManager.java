package com.volmit.iris;

import java.awt.Desktop;
import java.io.File;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import com.volmit.iris.command.util.MortarSender;
import com.volmit.iris.generator.IrisChunkGenerator;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.J;
import com.volmit.iris.util.O;

import lombok.Data;

@Data
public class ProjectManager
{
	private IrisChunkGenerator currentProject;

	public ProjectManager()
	{

	}

	public boolean isProjectOpen()
	{
		return currentProject != null;
	}

	public void open(MortarSender sender, String dimm)
	{
		open(sender, dimm, () ->
		{
		});
	}

	public void open(MortarSender sender, String dimm, Runnable onDone)
	{
		IrisDimension d = Iris.data.getDimensionLoader().load(dimm);

		if(d == null)
		{
			sender.sendMessage("Can't find dimension: " + dimm);
			return;
		}

		if(isProjectOpen())
		{
			sender.sendMessage("Please Wait. Closing Current Project...");
			close();
		}

		sender.sendMessage("Loading " + dimm + "...");
		IrisChunkGenerator gx = new IrisChunkGenerator(dimm, IrisSettings.get().threads);
		currentProject = gx;
		gx.setDev(true);
		sender.sendMessage("Generating with " + IrisSettings.get().threads + " threads per chunk");
		O<Boolean> done = new O<Boolean>();
		done.set(false);

		J.a(() ->
		{
			double last = 0;
			int req = 740;
			while(!done.get())
			{
				boolean derp = false;
				double v = (double) gx.getGenerated() / (double) req;

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

		World world = Bukkit.createWorld(new WorldCreator("iris/" + UUID.randomUUID()).seed(1337).generator(gx).generateStructures(false).type(WorldType.NORMAL).environment(d.getEnvironment()));
		done.set(true);
		sender.sendMessage("Generating 100%");

		if(sender.isPlayer())
		{
			sender.player().teleport(new Location(world, 150, 150, 275));
		}

		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
		{
			sender.sendMessage("Hotloading Active! Change any files and watch them appear as you load new chunks!");

			if(sender.isPlayer())
			{
				sender.player().setGameMode(GameMode.SPECTATOR);
			}

			J.attemptAsync(() ->
			{
				try
				{
					File f = d.getLoadFile().getParentFile().getParentFile();

					for(File i : f.listFiles())
					{
						if(i.getName().endsWith(".code-workspace"))
						{
							Desktop.getDesktop().open(i);
							break;
						}
					}
				}

				catch(Throwable e)
				{
					e.printStackTrace();
				}
			});
			onDone.run();
		}, 0);
	}

	public void close()
	{
		if(isProjectOpen())
		{
			currentProject.close();
			File folder = currentProject.getWorld().getWorldFolder();
			Bukkit.unloadWorld(currentProject.getWorld(), false);
			currentProject = null;
			Iris.data.getObjectLoader().clearCache();
			Iris.data.getBiomeLoader().clearCache();
			Iris.data.getRegionLoader().clearCache();
			Iris.data.getGeneratorLoader().clearCache();
			Iris.data.getDimensionLoader().clearCache();
			J.attemptAsync(() -> IO.delete(folder));
		}
	}
}
