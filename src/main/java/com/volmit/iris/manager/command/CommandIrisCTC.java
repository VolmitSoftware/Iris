package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CommandIrisCTC extends MortarCommand
{
	public CommandIrisCTC()
	{
		super("ctc", "threads", "thread");
		setDescription("Change generator thread count");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
	}


	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {
		if(args.length == 0)
		{
			list.qadd("4").qadd("8").qadd("12").qadd("16").qadd("18").qadd("24").qadd("32");
		}
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

			if(args.length == 0){
				sender.sendMessage("Current threads: " + g.getThreadCount());
				sender.sendMessage("You can change the treadcount with /iris ctc <number>");
				return true;
			}


			int m = Math.min(Math.max(Integer.valueOf(args[0]), 2), 256);
			g.changeThreadCount(m);
			sender.sendMessage("Thread count changed to " + m);
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
		return "[thread-count]";
	}
}
