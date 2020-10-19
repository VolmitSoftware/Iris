package com.volmit.iris.command;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.provisions.ProvisionBukkit;
import com.volmit.iris.object.IrisEntity;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioSummon extends MortarCommand
{
	public CommandIrisStudioSummon()
	{
		super("summon", "spawnmob");
		setDescription("Spawn an Iris entity");
		requiresPermission(Iris.perm.studio);
		setCategory("Summon");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
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
			if(args.length == 0)
			{
				for(String i : g.getData().getEntityLoader().getPossibleKeys())
				{
					sender.sendMessage("- " + i);
				}
			}

			else
			{
				IrisEntity e = g.getData().getEntityLoader().load(args[0]);

				if(e == null)
				{
					sender.sendMessage("Couldnt find entity " + args[0] + ". Use '/iris std summon' to see a list of iris entities.");
					return true;
				}

				e.spawn(g, sender.player().getLocation().clone().add(0, 3, 0));
			}
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
