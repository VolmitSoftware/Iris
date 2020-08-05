package com.volmit.iris.command;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import com.volmit.iris.util.PregenJob;

public class CommandIrisPregen extends MortarCommand
{
	public CommandIrisPregen()
	{
		super("pregen");
		setDescription("Pregen this world");
		requiresPermission(Iris.perm.studio);
		setCategory("Pregen");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args[0].equalsIgnoreCase("stop"))
		{
			if(PregenJob.task == -1)
			{
				sender.sendMessage("No Active Pregens");
			}
			else
			{
				sender.sendMessage("Stopped all pregens.");
			}
			PregenJob.stop();
			return true;
		}

		if(sender.isPlayer())
		{
			Player p = sender.player();
			World world = p.getWorld();
			new PregenJob(world, Integer.valueOf(args[0]));

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
		return "[width]";
	}
}
