package com.volmit.iris.manager.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.Command;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudio extends MortarCommand
{
	@Command
	private CommandIrisStudioCreate create;

	@Command
	private CommandIrisStudioOpen open;

	@Command
	private CommandIrisStudioSummon summon;

	@Command
	private CommandIrisStudioClose close;

	@Command
	private CommandIrisStudioPackage pkg;

	@Command
	private CommandIrisStudioUpdate update;

	//@Command
	//private CommandIrisStudioMap map;

	@Command
	private CommandIrisStudioGoto got0;

	@Command
	private CommandIrisStudioEditBiome ebiome;

	@Command
	private CommandIrisStudioHotload hotload;

	@Command
	private CommandIrisStudioExplorer exp;

	@Command
	private CommandIrisStudioExplorerGenerator generator;

	@Command
	private CommandIrisStudioLoot loot;

	@Command
	private CommandIrisStudioTPStudio tps;
	
	@Command
	private CommandIrisStudioConvert convert;

	public CommandIrisStudio()
	{
		super("studio", "std", "s");
		requiresPermission(Iris.perm.studio);
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

		sender.sendMessage("Iris Studio Commands:");
		printHelp(sender);
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[subcommand]";
	}
}
