package com.volmit.iris.command;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.provisions.ProvisionBukkit;
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

			if(!(world.getGenerator() instanceof ProvisionBukkit))
			{
				sender.sendMessage("You must be in an iris world.");
				return true;
			}

			IrisTerrainProvider g = (IrisTerrainProvider) ((ProvisionBukkit) world.getGenerator()).getProvider();
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
