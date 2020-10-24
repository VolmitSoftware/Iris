package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.v2.TestGen;
import com.volmit.iris.util.Command;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIris extends MortarCommand
{
	@Command
	private CommandIrisCreate create;

	@Command
	private CommandIrisStudio studio;

	@Command
	private CommandIrisStructure structure;

	@Command
	private CommandIrisObject object;

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
	private CommandIrisCTC ctc;

	@Command
	private CommandIrisLMM lmm;

	@Command
	private CommandIrisRegen regen;

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
		if(args.length == 1 && args[0].equalsIgnoreCase("test!"))
		{
			TestGen.gen(sender.player());
			return true;
		}

		sender.sendMessage("Iris v" + Iris.instance.getDescription().getVersion() + " by Volmit Software");
		printHelp(sender);
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
