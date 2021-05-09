package com.volmit.iris.manager.command.world;

import com.volmit.iris.Iris;
import com.volmit.iris.pregen.Pregenerator;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.awt.*;

public class CommandIrisPregen extends MortarCommand
{
	public CommandIrisPregen()
	{
		super("pregen");
		setDescription(
				"Pregen this world with optional parameters: " +
				"\n'1k' = 1000 by 1000 blocks, '1c' = 1 by 1 chunks, and '1r' = 32 by 32 chunks." +
				"\nIf you are using the console or want to pregen a world you're not in:" +
				"\nalso specify the name of the world. E.g. /ir pregen 5k world"
		);
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
		for (World w : Bukkit.getServer().getWorlds()){
			list.add(w.getName());
		}
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
			World world;
			if (args.length != 2) {
				world = p.getWorld();
			} else {
				try {
					world = Bukkit.getWorld(args[1]);
				} catch (Exception e){
					sender.sendMessage("Could not find specified world");
					sender.sendMessage("Please doublecheck your command. E.g. /ir pregen 5k world");
					return true;
				}
			}
			try {
				new Pregenerator(world, getVal(args[0]));
			} catch (NumberFormatException e){
				sender.sendMessage("Invalid argument in command");
				return true;
			} catch (NullPointerException e){
				e.printStackTrace();
				sender.sendMessage("No radius specified (check error in console)");
			} catch (HeadlessException e){
				sender.sendMessage("If you are seeing this and are using a hosted server, please turn off 'useServerLaunchedGUIs' in the settings");
			}

			return true;
		}
		else
		{
			if (args.length < 1){
				sender.sendMessage("Please specify the size of the pregen and the name of the world. E.g. /ir pregen 5k world");
				return true;
			}
			if (args.length < 2){
				sender.sendMessage("Please specify the name of the world after the command. E.g. /ir pregen 5k world");
				return true;
			}
			World world = Bukkit.getWorld(args[1]);
			try {
				new Pregenerator(world, getVal(args[0]));
			} catch (NumberFormatException e){
				sender.sendMessage("Invalid argument in command");
				return true;
			} catch (NullPointerException e){
				sender.sendMessage("Not all required parameters specified");
			} catch (HeadlessException e){
				sender.sendMessage("If you are seeing this and are using a hosted server, please turn off 'useServerLaunchedGUIs' in the settings");
			}

			return true;
		}
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
