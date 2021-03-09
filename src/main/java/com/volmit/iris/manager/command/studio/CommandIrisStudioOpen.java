package com.volmit.iris.manager.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioOpen extends MortarCommand
{
	public CommandIrisStudioOpen()
	{
		super("open", "o");
		requiresPermission(Iris.perm.studio);
		setDescription("Create a new temporary world to design a dimension.");
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
		
		if(args.length < 1)
		{
			sender.sendMessage("/iris std open <DIMENSION> (file name without .json)");
			return true;
		}

		Iris.proj.open(sender, args[0]);
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[dimension]";
	}
}
