package com.volmit.iris.manager.command.object;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.WandManager;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.Sound;

public class CommandIrisObjectDust extends MortarCommand
{
	public CommandIrisObjectDust()
	{
		super("dust", "dst", "d");
		requiresPermission(Iris.perm);
		setCategory("Object");
		setDescription("Get a powder that reveals placed objects");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio Objects, please enable studio in Iris/settings.json");
			return true;
		}
		
		if(!sender.isPlayer())
		{
			sender.sendMessage("You don't have an inventory");
			return true;
		}

		sender.player().getInventory().addItem(WandManager.createDust());
		sender.player().playSound(sender.player().getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, 1f, 1.5f);

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[subcommand]";
	}
}
