package ninja.bytecode.iris.command;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;

public class CommandWhatBlock extends MortarCommand
{
	public CommandWhatBlock()
	{
		super("block", "id", "i");
		setDescription("Identify Current Block Looking at");
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!sender.isPlayer())
		{
			sender.sendMessage("Not sure where you are looking.");
		}

		Player p = sender.player();
		Block b = p.getTargetBlock(null, 64);
		sender.sendMessage(b.getType().getId() + ":" + b.getData() + " (" + b.getType().toString() + ":" + b.getData() + ")");

		return true;
	}

}
