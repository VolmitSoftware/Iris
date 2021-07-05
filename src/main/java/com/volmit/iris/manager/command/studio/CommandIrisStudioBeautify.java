package com.volmit.iris.manager.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

public class CommandIrisStudioBeautify extends MortarCommand
{
	public CommandIrisStudioBeautify()
	{
		super("beautify", "prettify");
		requiresPermission(Iris.perm.studio);
		setDescription("Prettify the project by cleaning up json.");
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

		File clean = null;

		if(args.length == 0)
		{
			if(!Iris.proj.isProjectOpen())
			{
				sender.sendMessage("No open project. Either use /iris std beautify <project> or have a project open.");
				return true;
			}

			clean = Iris.proj.getActiveProject().getPath();
		}

		else
		{
			clean = Iris.instance.getDataFolder("packs", args[0]);

			if(!clean.exists())
			{
				sender.sendMessage("Not a valid project.");
				return true;
			}
		}

		sender.sendMessage("Cleaned " + Form.f(clean(sender, clean)) + " JSON Files");

		return true;
	}

	private int clean(MortarSender s, File clean) {
		int c = 0;
		if(clean.isDirectory())
		{
			for(File i : clean.listFiles())
			{
				c+=clean(s, i);
			}
		}

		else if(clean.getName().endsWith(".json"))
		{
			try {
				IO.writeAll(clean, new JSONObject(IO.readAll(clean)).toString(4));
			} catch (Throwable e) {
				Iris.error("Failed to beautify " + clean.getAbsolutePath() + " You may have errors in your json!");
			}

			c++;
		}

		return c;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[project]";
	}
}
