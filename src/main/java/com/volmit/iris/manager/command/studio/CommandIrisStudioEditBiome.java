package com.volmit.iris.manager.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.entity.Player;

import java.awt.*;
import java.io.File;

public class CommandIrisStudioEditBiome extends MortarCommand
{
	public CommandIrisStudioEditBiome()
	{
		super("editbiome", "ebiome", "eb");
		setDescription("Open this biome file in vscode");
		requiresPermission(Iris.perm.studio);
		setCategory("Studio");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
			return true;
		}

		if(!Iris.proj.isProjectOpen())
		{
			sender.sendMessage("There is not a studio currently loaded.");
			return true;
		}

		if(sender.isPlayer())
		{
			Player p = sender.player();

			try
			{
				File f = Iris.proj.getActiveProject().getActiveProvider().getBiome(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ()).getLoadFile();
				Desktop.getDesktop().open(f);
			}

			catch(Throwable e)
			{
				sender.sendMessage("Cant find the file. Are you in an Iris Studio world?");
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
