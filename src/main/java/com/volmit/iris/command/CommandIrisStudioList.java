package com.volmit.iris.command;

import java.io.File;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioList extends MortarCommand
{
	public CommandIrisStudioList()
	{
		super("list", "l");
		requiresPermission(Iris.perm.studio);
		setDescription("List projects that can be opened.");
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

		int m = 0;
		for(File i : Iris.globaldata.getDimensionLoader().getFolders())
		{
			for(File j : i.listFiles())
			{
				if(j.isFile() && j.getName().endsWith(".json"))
				{
					try
					{
						m++;
						IrisDimension d = Iris.globaldata.getDimensionLoader().load(j.getName().replaceAll("\\Q.json\\E", ""));
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
