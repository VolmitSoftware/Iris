package com.volmit.iris.command;

import java.io.File;

import com.volmit.iris.Iris;
import com.volmit.iris.command.util.MortarCommand;
import com.volmit.iris.command.util.MortarSender;
import com.volmit.iris.object.IrisDimension;

public class CommandIrisStudioList extends MortarCommand
{
	public CommandIrisStudioList()
	{
		super("list", "l");
		requiresPermission(Iris.perm.studio);
		setDescription("List projects that can be opened.");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		int m = 0;
		for(File i : Iris.data.getDimensionLoader().getFolders())
		{
			for(File j : i.listFiles())
			{
				if(j.isFile() && j.getName().endsWith(".json"))
				{
					try
					{
						m++;
						IrisDimension d = Iris.data.getDimensionLoader().load(j.getName().replaceAll("\\Q.json\\E", ""));
						sender.sendMessage("- " + d.getLoadKey() + " (" + d.getName() + ")");
					}
					catch(Throwable e)
					{

					}
				}
			}
		}

		sender.sendMessage("Found " + m + " project" + (m == 1 ? "" : "s"));

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
