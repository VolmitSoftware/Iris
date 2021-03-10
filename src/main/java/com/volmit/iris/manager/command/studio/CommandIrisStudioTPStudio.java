package com.volmit.iris.manager.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.GameMode;

public class CommandIrisStudioTPStudio extends MortarCommand
{
	public CommandIrisStudioTPStudio()
	{
		super("tps", "stp", "tpstudio");
		requiresPermission(Iris.perm.studio);
		setDescription("Go to the spawn of the currently open studio world.");
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

		if(!sender.isPlayer()){
			sender.sendMessage("Cannot be ran by console.");
			return true;
		}

		if(!Iris.proj.isProjectOpen())
		{
			sender.sendMessage("There is not a studio currently loaded.");
			return true;
		}

		try
		{
			sender.sendMessage("Teleporting you to the active studio world.");
			sender.player().teleport(Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().getSpawnLocation());
			sender.player().setGameMode(GameMode.SPECTATOR);
		}

		catch(Throwable e)
		{
			sender.sendMessage("Failed to teleport to the studio world. Try re-opening the project.");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
