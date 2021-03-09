package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.command.jigsaw.CommandIrisJigsaw;
import com.volmit.iris.manager.command.object.CommandIrisObject;
import com.volmit.iris.manager.command.studio.CommandIrisStudio;
import com.volmit.iris.manager.command.what.CommandIrisWhat;
import com.volmit.iris.manager.command.world.*;
import com.volmit.iris.util.Command;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIris extends MortarCommand
{
	@Command
	private CommandIrisCreate create;

	@Command
	private CommandIrisFix fix;

	@Command
	private CommandIrisStudio studio;

	@Command
	private CommandIrisJigsaw jigsaw;

	@Command
	private CommandIrisObject object;

	@Command
	private CommandIrisRegen regen;

	@Command
	private CommandIrisDownload download;

	@Command
	private CommandIrisUpdateProject updateProject;

	@Command
	private CommandIrisUpdateWorld updateWorld;

	@Command
	private CommandIrisWhat what;

	@Command
	private CommandIrisMetrics metrics;

	@Command
	private CommandIrisPregen pregen;

	@Command
	private CommandIrisReload reload;

	public CommandIris()
	{
		super("iris", "ir", "irs");
		requiresPermission(Iris.perm);
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		sender.sendMessage("Iris v" + Iris.instance.getDescription().getVersion() + " by Volmit Software");
		printHelp(sender);
		return true;
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
