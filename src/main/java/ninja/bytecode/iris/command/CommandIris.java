package ninja.bytecode.iris.command;

import mortar.bukkit.command.Command;
import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.util.text.C;

public class CommandIris extends MortarCommand
{
	@Command
	private CommandTimings timings;

	@Command
	private CommandWhat what;

	@Command
	private CommandFind find;

	@Command
	private CommandObject object;

	@Command
	private CommandSelection selection;

	@Command
	private CommandReload reload;

	public CommandIris()
	{
		super("iris", "irs", "ir");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		for(MortarCommand i : getChildren())
		{
			sender.sendMessage("/iris " + C.WHITE + i.getNode() + C.GRAY + (!i.getNodes().isEmpty() ? "," : "") + i.getNodes().toString(",") + " - " + C.DARK_GREEN + i.getDescription());
		}

		return true;
	}

}
