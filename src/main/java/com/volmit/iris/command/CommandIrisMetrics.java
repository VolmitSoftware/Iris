package com.volmit.iris.command;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.provisions.ProvisionBukkit;
import com.volmit.iris.gen.scaffold.IrisMetrics;
import com.volmit.iris.gen.scaffold.IrisWorlds;
import com.volmit.iris.util.C;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

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
	public boolean handle(MortarSender sender, String[] args)
	{
		if(sender.isPlayer())
		{
			Player p = sender.player();
			World world = p.getWorld();
			if(!(world.getGenerator() instanceof ProvisionBukkit))
			{
				sender.sendMessage("You must be in an iris world.");
				return true;
			}

			IrisTerrainProvider g = IrisWorlds.getProvider(world);
			IrisMetrics m = g.getMetrics();
			sender.sendMessage("Thread Count: " + C.BOLD + "" + C.WHITE + g.getThreads());
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
