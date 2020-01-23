package ninja.bytecode.iris.command;

import mortar.bukkit.command.Command;
import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.util.text.C;

public class CommandReload extends MortarCommand
{
	@Command
	private CommandReloadPack rThis;

	@Command
	private CommandReloadChunks rChunks;

	@Command
	private CommandReloadIris rIris;

	public CommandReload()
	{
		super("reload", "r");
		setDescription("Reload Chunks / Pack / Iris");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		for(MortarCommand i : getChildren())
		{
			sender.sendMessage("/iris reload " + C.WHITE + i.getNode() + C.GRAY + (!i.getNodes().isEmpty() ? "," : "") + i.getNodes().toString(",") + " - " + C.DARK_GREEN + i.getDescription());
		}

		return true;
	}

}
