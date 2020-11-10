package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.generator.legacy.scaffold.IrisMetrics;
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
			IrisMetrics m = new IrisMetrics(20); // TODO: BROKEN
			sender.sendMessage("Thread Count: " + C.BOLD + "" + C.WHITE + g.getThreadCount());
			sender.sendMessage("Total     : " + C.BOLD + "" + C.WHITE + Form.duration(m.getTotal().getAverage(), 2));
			sender.sendMessage("  Terrain : " + C.BOLD + "" + C.WHITE + Form.duration(m.getTerrain().getAverage(), 2));
			sender.sendMessage("  Deposits  : " + C.BOLD + "" + C.WHITE + Form.duration(m.getDeposits().getAverage(), 2));
			sender.sendMessage("  Parallax: " + C.BOLD + "" + C.WHITE + Form.duration(m.getParallax().getAverage(), 2));
			sender.sendMessage("  Post    : " + C.BOLD + "" + C.WHITE + Form.duration(m.getPost().getAverage(), 2));
			sender.sendMessage("Lighting  : " + C.BOLD + "" + C.WHITE + Form.duration(m.getUpdate().getAverage(), 2));
			sender.sendMessage("Spawns    : " + C.BOLD + "" + C.WHITE + Form.duration(m.getSpawns().getAverage(), 2));

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
