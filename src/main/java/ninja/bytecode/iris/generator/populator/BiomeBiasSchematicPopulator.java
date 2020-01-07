package ninja.bytecode.iris.generator.populator;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.World;

import ninja.bytecode.iris.schematic.Schematic;
import ninja.bytecode.iris.spec.IrisBiome;
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

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((biome == null) ? 0 : biome.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(!super.equals(obj))
			return false;
		if(getClass() != obj.getClass())
			return false;
		BiomeBiasSchematicPopulator other = (BiomeBiasSchematicPopulator) obj;
		if(biome == null)
		{
			if(other.biome != null)
				return false;
		}
		else if(!biome.equals(other.biome))
			return false;
		return true;
	}
}
