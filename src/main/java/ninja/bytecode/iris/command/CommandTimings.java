package ninja.bytecode.iris.command;

import org.bukkit.Bukkit;
import org.bukkit.World;

import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;

public class CommandTimings extends MortarCommand
{
	public CommandTimings()
	{
		super("timings", "t");
		setDescription("Tick use on a per chunk basis");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		World world = null;

		if(sender.isPlayer() && Iris.isGen(sender.player().getWorld()))
		{
			world = sender.player().getWorld();
		}

		else if(args.length >= 1)
		{
			World t = Bukkit.getWorld(args[0]);

			if(t == null)
			{
				sender.sendMessage("Unknown world " + args[0]);
				return true;
			}

			else if(t.getGenerator() instanceof IrisGenerator)
			{
				world = t;
			}
		}

		else
		{
			sender.sendMessage("Console / Non-Iris World. " + C.WHITE + "Use /iris timings <world>");
			return true;
		}

		Iris.getGen(world).getMetrics().send(sender, (m) -> sender.sendMessage(m));

		return true;
	}

}
