package com.volmit.iris.manager.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioPackage extends MortarCommand
{
	public CommandIrisStudioPackage()
	{
		super("package", "pkg");
		requiresPermission(Iris.perm.studio);
		setDescription("Package your dimension into a compressed format.");
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
		
		if(args.length == 0)
		{
			sender.sendMessage("/iris std package <DIMENSION> [-o] [-m]");
			return true;
		}

		J.a(() ->
		{
			boolean o = false;
			boolean m = true;

			for(String i : args)
			{
				if(i.equalsIgnoreCase("-o"))
				{
					o = true;
				}
			}

			String dim = args[0];
			Iris.proj.compilePackage(sender, dim, o, m);
		});

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[dimension] [-o] [-m]";
	}
}
