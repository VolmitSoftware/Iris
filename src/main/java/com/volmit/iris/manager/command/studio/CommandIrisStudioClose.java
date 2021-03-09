package com.volmit.iris.manager.command.studio;

import com.volmit.iris.util.KList;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioClose extends MortarCommand
{
	public CommandIrisStudioClose()
	{
		super("close", "x");
		requiresPermission(Iris.perm.studio);
		setDescription("Close the existing dimension");
		setCategory("Studio");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
			return true;
		}

		if(!Iris.proj.isProjectOpen())
		{
			sender.sendMessage("No open projects.");
			return true;
		}

		if(sender.isPlayer())
		{
			World f = null;

			for(World i : Bukkit.getWorlds())
			{
				if(i.getWorldFolder().getAbsolutePath().equals(Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().getWorldFolder().getAbsolutePath()))
				{
					continue;
				}

				f = i;
				break;
			}

			if(f == null)
			{
				for(Player i : Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().getPlayers())
				{
					i.kickPlayer("Project Closing, No other world to put you in. Rejoin Please!");
				}
			}

			else
			{
				for(Player i : Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().getPlayers())
				{
					i.teleport(f.getSpawnLocation());
				}
			}
		}

		Iris.proj.close();
		sender.sendMessage("Projects Closed & Caches Cleared!");
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
