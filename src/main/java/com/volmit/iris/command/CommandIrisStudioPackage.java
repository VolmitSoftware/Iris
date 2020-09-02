package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.util.J;
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
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args.length == 0)
		{
			sender.sendMessage("/iris std package <DIMENSION> [-o]");
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
