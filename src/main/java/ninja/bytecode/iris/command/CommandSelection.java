package ninja.bytecode.iris.command;

import mortar.bukkit.command.Command;
import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.util.text.C;

public class CommandSelection extends MortarCommand
{
	@Command
	private CommandSelectionExpand expand;

	@Command
	private CommandSelectionShift shift;

	@Command
	private CommandSelectionShrink shr;

	@Command
	private CommandSelectionXUp xip;

	@Command
	private CommandSelectionXVert xvc;

	public CommandSelection()
	{
		super("selection", "sel", "s");
		setDescription("Wand Selection Subcommands");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		for(MortarCommand i : getChildren())
		{
			sender.sendMessage("/iris sel " + C.WHITE + i.getNode() + C.GRAY + (!i.getNodes().isEmpty() ? "," : "") + i.getNodes().toString(",") + " - " + C.DARK_GREEN + i.getDescription());
		}

		return true;
	}

}
