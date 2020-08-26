package com.volmit.iris.command;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisMetrics;
import com.volmit.iris.gen.IrisChunkGenerator;
import com.volmit.iris.util.C;
import com.volmit.iris.util.Command;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisWhat extends MortarCommand
{
	@Command
	private CommandIrisWhatBlock block;

	@Command
	private CommandIrisWhatHand hand;

	public CommandIrisWhat()
	{
		super("what", "w", "?");
		setDescription("Get timings for this world");
		requiresPermission(Iris.perm.studio);
		setCategory("Wut");
		setDescription("Figure out what stuff is");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(sender.isPlayer())
		{
			Player p = (Player) sender;
			World world = p.getWorld();
			IrisChunkGenerator g = (IrisChunkGenerator) world.getGenerator();
			IrisMetrics m = g.getMetrics();
			sender.sendMessage("Thread Count: " + C.BOLD + "" + C.WHITE + g.getThreads());
			sender.sendMessage("Total     : " + C.BOLD + "" + C.WHITE + Form.duration(m.getTotal().getAverage(), 2));
			sender.sendMessage("  Terrain : " + C.BOLD + "" + C.WHITE + Form.duration(m.getTerrain().getAverage(), 2));
			sender.sendMessage("  Parallax: " + C.BOLD + "" + C.WHITE + Form.duration(m.getParallax().getAverage(), 2));
			sender.sendMessage("  Post    : " + C.BOLD + "" + C.WHITE + Form.duration(m.getPost().getAverage(), 2));

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
