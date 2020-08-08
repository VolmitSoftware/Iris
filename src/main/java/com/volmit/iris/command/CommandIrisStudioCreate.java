package com.volmit.iris.command;

import java.io.IOException;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.object.InterpolationMethod;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeGeneratorLink;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisNoiseGenerator;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.JSONException;
import com.volmit.iris.util.JSONObject;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

import net.md_5.bungee.api.ChatColor;

public class CommandIrisStudioCreate extends MortarCommand
{
	public CommandIrisStudioCreate()
	{
		super("create", "new");
		requiresPermission(Iris.perm.studio);
		setDescription("Create a new project & open it.");
		setCategory("Studio");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args.length != 1)
		{
			sender.sendMessage("Please use a lowercase name with hyphens (-) for spaces.");
			sender.sendMessage("I.e. /iris std new " + ChatColor.BOLD + "aether");
			return true;
		}

		Iris.proj.create(sender, args[0]);

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[dimension]";
	}
}
