package com.volmit.plague.api.command;

import com.volmit.plague.api.annotations.*;
import com.volmit.plague.api.PlagueSender;

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

	@Plagued
	public void doKick(PlagueSender sender, 
			@Name("player")
			@Description("The player you want to kick")
			String player,

			@Name("message")
			@Description("A kick reason to display.")
			String messages,

			@Name("broadcast")
			@Description("If this kick should be broadcasted.")
			@Permission("plugin.kick.broadcast")
			@Optional(defaultBoolean = false)
			boolean broadcast)
	{
		sendHelp(sender);
	}
}
