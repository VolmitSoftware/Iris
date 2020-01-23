package ninja.bytecode.iris.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.util.text.C;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.genobject.PlacedObject;

public class CommandFindObject extends MortarCommand
{
	public CommandFindObject()
	{
		super("object", "o");
		setDescription("Teleport to an object by name");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		World w = null;

		if(sender.isPlayer() && sender.player().getWorld().getGenerator() instanceof IrisGenerator)
		{
			w = sender.player().getWorld();
		}

		else
		{
			sender.sendMessage("Console / Non-Iris World.");
			return true;
		}

		Player p = sender.player();

		if(args.length > 0)
		{
			PlacedObject o = ((IrisGenerator) w.getGenerator()).randomObject(args[0]);

			if(o != null)
			{
				Location l = new Location(w, o.getX(), o.getY(), o.getZ());
				p.teleport(l);
				sender.sendMessage("Found Object " + C.DARK_GREEN + o.getF().replace(":", "/" + C.WHITE));
			}

			else
			{
				sender.sendMessage("Couldn't find any objects containing '" + args[0] + "' Either");
			}
		}

		else
		{
			sender.sendMessage("/iris find object <query>");
		}

		return true;
	}

}
