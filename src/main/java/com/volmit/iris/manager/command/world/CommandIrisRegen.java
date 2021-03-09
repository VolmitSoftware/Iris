package com.volmit.iris.manager.command.world;

import com.volmit.iris.Iris;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisRegen extends MortarCommand
{
	public CommandIrisRegen()
	{
		super("regen");
		setDescription("Regenerate chunks around you (iris worlds only)");
		requiresPermission(Iris.perm.studio);
		setCategory("Regen");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		sender.sendMessage("Iris' /regen command is currently disabled due to maintenance. Apologies.");
		return true;
		/* This is commented yes
		try
		{
			if(args.length == 0)
			{
				IrisWorlds.access(sender.player().getWorld()).regenerate(
						sender.player().getLocation().getChunk().getX(),
						sender.player().getLocation().getChunk().getZ());
				sender.sendMessage("Regenerated your current chunk");
			}

			else
			{
				try
				{
					int vx = sender.player().getLocation().getChunk().getX();
					int vz = sender.player().getLocation().getChunk().getZ();
					int rad = Integer.valueOf(args[0]);
					int m = (int) Math.pow(rad, 2);
					new Spiraler(rad, rad*2, (x,z) -> {
						IrisWorlds.access(sender.player().getWorld()).regenerate(
								vx + x,
								vz + z);
					}).drain();

					sender.sendMessage("Regenerated " + m + " chunks");
				}

				catch(NumberFormatException e)
				{
					sender.sendMessage(args[0] + " is not a number.");
				}
			}
		}

		catch(Throwable e1)
		{
			sender.sendMessage("You must be in a regen-capable iris world!");
		}

		return true;
		*/
	}

	@Override
	protected String getArgsUsage()
	{
		return "[size]";
	}
}
