package com.volmit.iris.manager.command.what;

import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CommandIrisWhatBiome extends MortarCommand
{
	public CommandIrisWhatBiome()
	{
		super("biome", "bi", "b");
		setDescription("Get the biome data you are in.");
		requiresPermission(Iris.perm.studio);
		setCategory("Wut");
		setDescription("What biome am I in");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(sender.isPlayer())
		{
			Player p = sender.player();
			World w = p.getWorld();

			try
			{

				IrisAccess g = IrisWorlds.access(w);
				assert g != null;
				IrisBiome b = g.getBiome(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
				sender.sendMessage("IBiome: " + b.getLoadKey() + " (" + b.getDerivative().name() + ")");
			}

			catch(Throwable e)
			{
				sender.sendMessage("Non-Iris Biome: " + p.getLocation().getBlock().getBiome().name());
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
