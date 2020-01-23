package ninja.bytecode.iris.command;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import mortar.api.nms.NMP;
import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;

public class CommandReloadChunks extends MortarCommand
{
	public CommandReloadChunks()
	{
		super("chunks", "c");
		setDescription("Resends chunk packets.");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{

		if(!sender.isPlayer())
		{
			sender.sendMessage("Again, You don't have a position. Stop it.");
		}

		sender.sendMessage("Resending Chunks in your view distance.");
		Player p = ((Player) sender);

		for(Chunk i : p.getWorld().getLoadedChunks())
		{
			NMP.CHUNK.refresh(p, i);
		}

		return true;
	}

}
