package ninja.bytecode.iris.command;

import mortar.bukkit.command.Command;
import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.util.text.C;

public class CommandFind extends MortarCommand
{
	@Command
	private CommandFindBiome fBiome;

	@Command
	private CommandFindObject fObject;

	public CommandFind()
	{
		super("find", "f");
		setDescription("Teleport to a specific biome / object");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		for(MortarCommand i : getChildren())
		{
			sender.sendMessage("/iris find " + C.WHITE + i.getNode() + C.GRAY + (!i.getNodes().isEmpty() ? "," : "") + i.getNodes().toString(",") + " - " + C.DARK_GREEN + i.getDescription());
		}

		return true;
	}

}
