package com.volmit.plague.api.command;

import org.bukkit.Bukkit;

import com.volmit.plague.api.Description;
import com.volmit.plague.api.Name;
import com.volmit.plague.api.Optional;
import com.volmit.plague.api.Permission;
import com.volmit.plague.api.PlagueSender;
import com.volmit.plague.api.Plagued;

@Permission("plugin.kick")
@Description("A proper kick command")
public class KickCommand extends PlagueCommand
{
	@Plagued
	private KickAllCommand all; // So you can use /kick all (fires this child)

	@Override
	public String[] getNodes()
	{
		return new String[] {"kick", "kk"};
	}

	//@builder
	@Plagued
	public void doKick(PlagueSender sender, 
			@Name("player")
			@Description("The player you want to kick") 
			String player,

			@Name("message")
			@Description("A kick reason to display.") 
			@Optional(defaultString = "for no reason") 
			String message,

			@Name("broadcast")
			@Description("If this kick should be broadcasted.") 
			@Optional(defaultBoolean = false) @Permission("plugin.kick.broadcast") 
			boolean broadcast)
	//@done
	{
		sendHelp(sender);
	}
}
