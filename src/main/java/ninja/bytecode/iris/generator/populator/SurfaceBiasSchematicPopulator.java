package ninja.bytecode.iris.generator.populator;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import ninja.bytecode.iris.schematic.Schematic;
import ninja.bytecode.shuriken.collections.GList;

public class SurfaceBiasSchematicPopulator extends SchematicPopulator
{
	private GList<Material> bias;
	
	public SurfaceBiasSchematicPopulator(double chance, Schematic... schematics)
	{
		super(chance, schematics);
		this.bias = new GList<>();
	}
	
	public SurfaceBiasSchematicPopulator surface(Material mb)
	{
		bias.add(mb);
		return this;
	}
	
	@Override
	public void doPopulate(World world, Random random, Chunk source, int wx, int wz)
	{
		Block b = world.getHighestBlockAt(wx, wz);
		
		for(Material i : bias)
		{
			if(b.getRelative(BlockFace.DOWN).getType().equals(i))
			{
				schematics[random.nextInt(schematics.length)].place(world, wx, b.getY() - 1, wz);
				break;
			}
		}
	}
}
