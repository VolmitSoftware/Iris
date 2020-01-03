package ninja.bytecode.iris.generator.populator;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import ninja.bytecode.iris.schematic.Schematic;
import ninja.bytecode.iris.util.ChancedPopulator;

public class SchematicPopulator extends ChancedPopulator
{
	protected final Schematic[] schematics;
	
	public SchematicPopulator(double chance, Schematic... schematics)
	{
		super(chance);
		this.schematics = schematics;
	}

	@Override
	public void doPopulate(World world, Random random, Chunk source, int wx, int wz)
	{
		Block b = world.getHighestBlockAt(wx, wz);
		
		if(!b.getRelative(BlockFace.DOWN).getType().isSolid())
		{
			return;
		}
		
		schematics[random.nextInt(schematics.length)].place(world, wx, b.getY() - 1, wz);
	}
}
