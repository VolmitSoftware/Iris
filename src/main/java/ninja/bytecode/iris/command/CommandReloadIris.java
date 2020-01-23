package ninja.bytecode.iris.command;

import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import ninja.bytecode.iris.Iris;

public class CommandReloadIris extends MortarCommand
{
	public CommandReloadIris()
	{
		super("iris", "i");
		setDescription("Reloads Iris");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		Iris.instance.reload();

		return true;
	}

}
