package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.*;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CommandIrisMetrics extends MortarCommand
{
	public CommandIrisMetrics()
	{
		super("metrics", "stats", "mt");
		setDescription("Get timings for this world");
		requiresPermission(Iris.perm.studio);
		setCategory("Metrics");
	}


	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}
	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(sender.isPlayer())
		{
			Player p = sender.player();
			World world = p.getWorld();
			if(!IrisWorlds.isIrisWorld(world))
			{
				sender.sendMessage("You must be in an iris world.");
				return true;
			}

			IrisAccess g = IrisWorlds.access(world);

			try
			{
				g.printMetrics(sender);
			}

			catch(Throwable e)
			{
				sender.sendMessage("You must be in an iris world.");
			}

			return true;
		}

		else
		{
			sender.sendMessage("Players only.");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
