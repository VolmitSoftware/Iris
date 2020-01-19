package ninja.bytecode.iris.controller;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.util.IrisController;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GSet;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.io.IO;

public class DebugController implements IrisController
{
	@Override
	public void onStart()
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
		{
			if(Iris.settings.performance.debugMode)
			{
				J.a(() ->
				{
					J.a(() ->
					{
						J.sleep(1000);
						while(!Iris.getController(PackController.class).isReady())
						{
							J.sleep(250);
						}

						Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
						{
							if(Iris.settings.performance.debugMode)
							{
								GSet<String> ws = new GSet<>();
								GList<World> destroy = new GList<>();

								for(World i : Bukkit.getWorlds())
								{
									if(i.getGenerator() instanceof IrisGenerator)
									{
										destroy.add(i);
									}
								}

								World w = Iris.getController(WorldController.class).createIrisWorld(null, 0, true);
								for(Player i : Bukkit.getOnlinePlayers())
								{
									Location m = i.getLocation();
									ws.add(i.getWorld().getName());
									i.teleport(new Location(w, m.getX(), m.getY(), m.getZ(), m.getYaw(), m.getPitch()));
									i.setFlying(true);
									i.setGameMode(GameMode.SPECTATOR);
								}

								for(String i : ws)
								{
									Bukkit.unloadWorld(i, false);
								}

								Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
								{
									for(World i : destroy.copy())
									{
										Bukkit.unloadWorld(i, false);
									}
								}, 20);
							}
						}, 1);
					});
					Iris.getController(PackController.class).compile();
				});
			}
		}, 1);
	}

	@Override
	public void onStop()
	{
		deleteOnExit(new File("iris-worlds"));
	}

	public static void deleteOnExit(File f)
	{
		if(f == null || !f.exists())
		{
			return;
		}

		if(f.isDirectory())
		{
			for(File i : f.listFiles())
			{
				deleteOnExit(i);
			}
		}

		f.deleteOnExit();
	}
}
