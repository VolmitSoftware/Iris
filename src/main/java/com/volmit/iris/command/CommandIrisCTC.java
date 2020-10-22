package com.volmit.iris.command;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.scaffold.IrisWorlds;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisCTC extends MortarCommand
{
	public CommandIrisCTC()
	{
		super("ctc");
		setDescription("Change generator thread count");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args.length == 0)
		{
			sender.sendMessage("/iris ctc " + getArgsUsage());
			return true;
		}

		if(sender.isPlayer())
		{
			Player p = sender.player();
			World world = p.getWorld();

			if(!IrisWorlds.isIrisWorld(world))
			{
				sender.sendMessage("You must be in an iris world.");
				return true;
			}

			IrisTerrainProvider g = IrisWorlds.getProvider(world);
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
