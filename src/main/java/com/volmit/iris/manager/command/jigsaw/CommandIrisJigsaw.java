package com.volmit.iris.manager.command.jigsaw;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.Command;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisJigsaw extends MortarCommand
{
	@Command
	private CommandIrisJigsawNew create;

	@Command
	private CommandIrisJigsawEdit edit;

	@Command
	private CommandIrisJigsawSave save;

	@Command
	private CommandIrisJigsawPlace place;

	public CommandIrisJigsaw()
	{
		super("jigsaw", "jig", "jsw");
		requiresPermission(Iris.perm);
		setCategory("Jigsaw");
		setDescription("Iris jigsaw commands");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio Jigsaw, please enable studio in Iris/settings.json");
			return true;
		}
		
		if(!sender.isPlayer())
		{
			sender.sendMessage("Ingame only");
			return true;
		}

		sender.sendMessage("Iris Jigsaw Commands:");
		printHelp(sender);
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
