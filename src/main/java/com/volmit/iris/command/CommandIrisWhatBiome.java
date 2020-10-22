package com.volmit.iris.command;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.scaffold.IrisWorlds;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisWhatBiome extends MortarCommand
{
	public CommandIrisWhatBiome()
	{
		super("biome", "bi");
		setDescription("Get the biome data you are in.");
		requiresPermission(Iris.perm.studio);
		setCategory("Wut");
		setDescription("What biome am i In");
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

				IrisTerrainProvider g = IrisWorlds.getProvider(w);
				IrisBiome b = g.sampleTrueBiome(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
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
