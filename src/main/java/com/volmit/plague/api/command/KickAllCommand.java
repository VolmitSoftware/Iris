package com.volmit.plague.api.command;

import com.volmit.plague.api.Description;
import com.volmit.plague.api.PlagueSender;

@Description("A proper kick command")
public class KickAllCommand extends PlagueCommand
{
	@Override
	public String[] getNodes()
	{
		return new String[] {"all", "*"};
	}

	public void doExample(PlagueSender sender)
	{
		// Kick all people
	}
}
