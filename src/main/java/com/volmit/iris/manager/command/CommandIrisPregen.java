package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.pregen.Pregenerator;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CommandIrisPregen extends MortarCommand
{
	public CommandIrisPregen()
	{
		super("pregen");
		setDescription("Pregen this world with optional parameters: '1k' = 1000 by 1000 blocks, '1c' = 1 by 1 chunks, and '1r' = 32 by 32 chunks");
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
		list.add("10c");
		list.add("25c");
		list.add("5r");
		list.add("10r");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args.length == 0)
		{
			sender.sendMessage("/iris pregen <blocks-wide|stop>");
			return true;
		}

		if(args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("x"))
		{
			if (Pregenerator.shutdownInstance()) {
				sender.sendMessage("Stopped Pregen.");
			} else
			{
				sender.sendMessage("No Active Pregens.");
			}
			return true;
		}
		else if(args[0].equalsIgnoreCase("pause") || args[0].equalsIgnoreCase("resume"))
		{
			if(Pregenerator.getInstance() != null)
			{
				Pregenerator.pauseResume();

				if(Pregenerator.isPaused())
				{
					sender.sendMessage("Pregen Paused");
				}

				else
				{
					sender.sendMessage("Pregen Resumed");
				}
			}

			else
			{
				sender.sendMessage("No Active Pregens");
			}

			return true;
		}

		else if(sender.isPlayer())
		{
			Player p = sender.player();
			World world = p.getWorld();
			try {
				new Pregenerator(world, getVal(args[0]));
			} catch (NumberFormatException e){
				sender.sendMessage("Invalid argument in command");
				return true;
			} catch (NullPointerException e){
				sender.sendMessage("No radius specified");
			}

			return true;
		}
		else
		{
			sender.sendMessage("Players only.");
		}
		return true;
	}

	private int getVal(String arg) {

		if(arg.toLowerCase().endsWith("c") || arg.toLowerCase().endsWith("chunks"))
		{
			return Integer.parseInt(arg.toLowerCase().replaceAll("\\Qc\\E", "").replaceAll("\\Qchunks\\E", "")) * 16;
		}

		if(arg.toLowerCase().endsWith("r") || arg.toLowerCase().endsWith("regions"))
		{
			return Integer.parseInt(arg.toLowerCase().replaceAll("\\Qr\\E", "").replaceAll("\\Qregions\\E", "")) * 512;
		}

		if(arg.toLowerCase().endsWith("k"))
		{
			return Integer.parseInt(arg.toLowerCase().replaceAll("\\Qk\\E", "")) * 1000;
		}

		return Integer.parseInt(arg.toLowerCase());
	}

	@Override
	protected String getArgsUsage()
	{
		return "[width]";
	}
}
