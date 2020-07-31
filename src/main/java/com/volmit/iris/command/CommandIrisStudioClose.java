package com.volmit.iris.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.command.util.MortarCommand;
import com.volmit.iris.command.util.MortarSender;

public class CommandIrisStudioClose extends MortarCommand
{
	public CommandIrisStudioClose()
	{
		super("close", "x");
		requiresPermission(Iris.perm.studio);
		setDescription("Close the existing dimension");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
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
				if(i.getWorldFolder().getAbsolutePath().equals(Iris.proj.getCurrentProject().getWorld().getWorldFolder().getAbsolutePath()))
				{
					continue;
				}

				f = i;
				break;
			}

			if(f == null)
			{
				for(Player i : Iris.proj.getCurrentProject().getWorld().getPlayers())
				{
					i.kickPlayer("Project Closing, No other world to put you in. Rejoin Please!");
				}
			}

			else
			{
				for(Player i : Iris.proj.getCurrentProject().getWorld().getPlayers())
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
