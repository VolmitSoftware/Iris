package com.volmit.iris.command;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisMetrics;
import com.volmit.iris.gen.IrisChunkGenerator;
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
			IrisChunkGenerator g = (IrisChunkGenerator) world.getGenerator();
			IrisMetrics m = g.getMetrics();
			sender.sendMessage("Thread Count: " + ChatColor.BOLD + "" + ChatColor.WHITE + g.getThreads());
			sender.sendMessage("Total     : " + ChatColor.BOLD + "" + ChatColor.WHITE + Form.duration(m.getTotal().getAverage(), 2));
			sender.sendMessage("  Terrain : " + ChatColor.BOLD + "" + ChatColor.WHITE + Form.duration(m.getTerrain().getAverage(), 2));
			sender.sendMessage("  Parallax: " + ChatColor.BOLD + "" + ChatColor.WHITE + Form.duration(m.getParallax().getAverage(), 2));
			sender.sendMessage("  Post    : " + ChatColor.BOLD + "" + ChatColor.WHITE + Form.duration(m.getPost().getAverage(), 2));
			sender.sendMessage("Updates   : " + ChatColor.BOLD + "" + ChatColor.WHITE + Form.duration(m.getUpdate().getAverage(), 2));

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
