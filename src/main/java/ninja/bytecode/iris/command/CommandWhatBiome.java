package ninja.bytecode.iris.command;

import org.bukkit.World;
import org.bukkit.entity.Player;

import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.BiomeLayer;
import ninja.bytecode.shuriken.format.Form;

public class CommandWhatBiome extends MortarCommand
{
	public CommandWhatBiome()
	{
		super("biome", "b");
		setDescription("Identify Current Biome");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		World world = null;

		if(sender.isPlayer() && Iris.isGen(sender.player().getWorld()))
		{
			world = sender.player().getWorld();
		}

		else
		{
			sender.sendMessage("Console / Non-Iris World.");
			return true;
		}

		Player p = sender.player();
		IrisGenerator g = Iris.getGen(world);
		IrisBiome biome = g.getBiome((int) g.getOffsetX(p.getLocation().getX(), p.getLocation().getZ()), (int) g.getOffsetZ(p.getLocation().getX(), p.getLocation().getZ()));
		BiomeLayer l = new BiomeLayer(g, biome);
		sender.sendMessage("Biome: " + C.BOLD + C.WHITE + biome.getName() + C.RESET + C.GRAY + " (" + C.GOLD + l.getBiome().getRarityString() + C.GRAY + ")");

		for(String i : biome.getSchematicGroups().k())
		{
			String f = "";
			double percent = biome.getSchematicGroups().get(i);

			if(percent > 1D)
			{
				f = (int) percent + " + " + Form.pc(percent - (int) percent, percent - (int) percent >= 0.01 ? 0 : 3);
			}

			else
			{
				f = Form.pc(percent, percent >= 0.01 ? 0 : 3);
			}

			sender.sendMessage("* " + C.DARK_GREEN + i + ": " + C.BOLD + C.WHITE + f + C.RESET + C.GRAY + " (" + Form.f(g.getDimension().getObjectGroup(i).size()) + " variants)");
		}

		return true;
	}

}
