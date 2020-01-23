package ninja.bytecode.iris.command;

import mortar.bukkit.command.Command;
import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.util.text.C;

public class CommandWhat extends MortarCommand
{
	@Command
	private CommandWhatBiome wBiome;

	@Command
	private CommandWhatObject wObject;

	@Command
	private CommandWhatBlock wBlock;

	public CommandWhat()
	{
		super("what", "w");
		setDescription("Identify what you are looking at.");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		for(MortarCommand i : getChildren())
		{
			sender.sendMessage("/iris what " + C.WHITE + i.getNode() + C.GRAY + (!i.getNodes().isEmpty() ? "," : "") + i.getNodes().toString(",") + " - " + C.DARK_GREEN + i.getDescription());
		}

		return true;
	}

}
