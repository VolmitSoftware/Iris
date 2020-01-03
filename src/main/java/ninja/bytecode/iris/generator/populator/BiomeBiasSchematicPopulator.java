package ninja.bytecode.iris.generator.populator;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.World;

import ninja.bytecode.iris.generator.biome.IrisBiome;
import ninja.bytecode.iris.schematic.Schematic;
import ninja.bytecode.iris.util.MB;

public class BiomeBiasSchematicPopulator extends SurfaceBiasSchematicPopulator
{
	protected IrisBiome biome;
	public BiomeBiasSchematicPopulator(double chance, IrisBiome biome, Schematic... schematics)
	{
		super(chance, schematics);
		this.biome = biome;
		
		for(MB i : biome.getSurface())
		{
			surface(i.material);
		}
	}
	
	public void doPopulate(World world, Random random, Chunk source, int wx, int wz)
	{
		if(world.getBiome(wx, wz).equals(biome.getRealBiome()))
		{
			super.doPopulate(world, random, source, wx, wz);
		}
	}
}
