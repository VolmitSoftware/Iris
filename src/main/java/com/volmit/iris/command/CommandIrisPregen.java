package com.volmit.iris.command;

import com.volmit.iris.util.KList;
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
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {
		list.add("stop");
		list.add("pause");
		list.add("resume");
		list.add("500");
		list.add("1000");
		list.add("10k");
		list.add("25k");
		list.add("chunksAcross=20");
		list.add("regionsAcross=8");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args.length == 0)
		{
			sender.sendMessage("/iris pregen <blocks-wide|stop>");
			return true;
		}

		if(args[0].equalsIgnoreCase("stop"))
		{
			if(PregenJob.task == -1)
			{
				sender.sendMessage("No Active Pregens");
			}
			else
			{
				sender.sendMessage("Stopped All Pregens.");
				PregenJob.stop();
			}
			return true;
		}
		else if(args[0].equalsIgnoreCase("pause"))
		{
			if(PregenJob.task == -1)
			{
				sender.sendMessage("No Active Pregens");
			}

			else
			{
				PregenJob.pauseResume();

				if(PregenJob.isPaused())
				{
					sender.sendMessage("Pregen Paused");
				}

				else
				{
					sender.sendMessage("Pregen Resumed");
				}
			}

			return true;
		}

		if(sender.isPlayer())
		{
			Player p = sender.player();
			World world = p.getWorld();
			new PregenJob(world, getVal(args[0]), sender, () ->
			{
			});

			return true;
		}
		else
		{
			sender.sendMessage("Players only.");
		}

		return true;
	}

	private int getVal(String arg) {
		if(arg.toLowerCase().endsWith("k"))
		{
			return Integer.valueOf(arg.toLowerCase().replaceAll("\\Qk\\E", "")) * 1000;
		}

		if(arg.toLowerCase().contains("chunksAcross="))
		{
			return Integer.valueOf(arg.toLowerCase().replaceAll("\\QchunksAcross=\\E", "")) * 16;
		}

		if(arg.toLowerCase().endsWith("regionsAcross="))
		{
			return Integer.valueOf(arg.toLowerCase().replaceAll("\\QregionsAcross=\\E", "")) * 16;
		}

		else
		{
			return Integer.valueOf(arg.toLowerCase());
		}
	}

	@Override
	protected String getArgsUsage()
	{
		return "[width]";
	}
}
