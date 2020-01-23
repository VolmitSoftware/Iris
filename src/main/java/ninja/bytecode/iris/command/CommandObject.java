package ninja.bytecode.iris.command;

import mortar.bukkit.command.Command;
import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.util.text.C;

public class CommandObject extends MortarCommand
{
	@Command
	private CommandObjectWand oWand;
	
	@Command
	private CommandObjectLoad oLoad;

	@Command
	private CommandObjectSave oSave;

	public CommandObject()
	{
		super("object", "o");
		setDescription("Object Subcommands");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		for(MortarCommand i : getChildren())
		{
			sender.sendMessage("/iris object " + C.WHITE + i.getNode() + C.GRAY + (!i.getNodes().isEmpty() ? "," : "") + i.getNodes().toString(",") + " - " + C.DARK_GREEN + i.getDescription());
		}

		return true;
	}

}
