package com.volmit.iris.command;

import java.awt.Desktop;
import java.io.File;

import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisEditBiome extends MortarCommand
{
	public CommandIrisEditBiome()
	{
		super("editbiome", "ebiome", "eb");
		setDescription("Open this biome file in vscode");
		requiresPermission(Iris.perm.studio);
		setCategory("Studio");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(sender.isPlayer())
		{
			Player p = sender.player();

			try
			{
				File f = Iris.proj.getCurrentProject().sampleTrueBiome(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ()).getLoadFile();
				Desktop.getDesktop().open(f);
			}

			catch(Throwable e)
			{
				sender.sendMessage("Cant find the file. Are you in an Iris world?");
			}

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
