package com.volmit.iris.manager.command.what;

import com.volmit.iris.Iris;
import com.volmit.iris.util.Command;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisWhat extends MortarCommand
{
	@Command
	private CommandIrisWhatBlock block;

	@Command
	private CommandIrisWhatHand hand;

	@Command
	private CommandIrisWhatBiome biome;

	@Command
	private CommandIrisWhatObjects objects;

	public CommandIrisWhat()
	{
		super("what", "w", "?");
		setDescription("Get timings for this world");
		requiresPermission(Iris.perm.studio);
		setCategory("Wut");
		setDescription("Figure out what stuff is");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		sender.sendMessage("Iris 'What' Commands:");
		printHelp(sender);
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
