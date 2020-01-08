package ninja.bytecode.iris.controller;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.IrisController;
import ninja.bytecode.shuriken.collections.GSet;
import ninja.bytecode.shuriken.execution.J;

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
							}
						}, 1);
					});
					Iris.getController(PackController.class).loadContent();
				});
			}
		}, 1);
	}

	@Override
	public void onStop()
	{

	}
}
