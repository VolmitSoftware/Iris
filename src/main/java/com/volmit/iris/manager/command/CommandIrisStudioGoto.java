package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CommandIrisStudioGoto extends MortarCommand
{
	public CommandIrisStudioGoto()
	{
		super("goto", "find", "g", "tp");
		setDescription("Find any biome or a biome border");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {
		if(args.length == 0 && sender.isPlayer() && IrisWorlds.isIrisWorld(sender.player().getWorld()))
		{
			list.add(IrisWorlds.access(sender.player().getWorld()).getData().getBiomeLoader().getPossibleKeys());
		}
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		try
		{
			if(args.length < 1)
			{
				sender.sendMessage("/iris std goto " + getArgsUsage());
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

				IrisAccess g = IrisWorlds.access(world);
				IrisBiome b = IrisDataManager.loadAnyBiome(args[0]);
				IrisRegion r = IrisDataManager.loadAnyRegion(args[0]);

				if(b != null)
				{
					J.a(() -> {
						Location l = g.lookForBiome(b, 10000, (v) -> sender.sendMessage("Looking for " + C.BOLD + C.WHITE + b.getName() + C.RESET + C.GRAY + ": Checked " + Form.f(v) + " Places"));

						if(l == null)
						{
							sender.sendMessage("Couldn't find " + b.getName() + ".");
						}

						else
						{
							sender.sendMessage("Found " + b.getName() + "!");
							J.s(() -> sender.player().teleport(l));
						}
					});
				}

				else if(r != null)
				{
					J.a(() -> {
						Location l = g.lookForRegion(r, 60000, (v) -> sender.sendMessage(C.BOLD +""+ C.WHITE + r.getName() + C.RESET + C.GRAY + ": Checked " + Form.f(v) + " Places"));

						if(l == null)
						{
							sender.sendMessage("Couldn't find " + r.getName() + ".");
						}

						else
						{
							sender.sendMessage("Found " + r.getName() + "!");
							J.s(() -> sender.player().teleport(l));
						}
					});
				}

				else
				{
					sender.sendMessage(args[0] + " is not a biome or region in this dimension.");
				}

				return true;
			}

			else
			{
				sender.sendMessage("Players only.");
			}
		}

		catch(Throwable e)
		{
			Iris.error("Failed goto!");
			e.printStackTrace();
			sender.sendMessage("We cant seem to aquire a lock on the biome cache. Please report the error in the console to our github. Thanks!");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[biome/region]";
	}
}
