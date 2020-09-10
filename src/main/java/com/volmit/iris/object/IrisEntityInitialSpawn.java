package com.volmit.iris.object;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.volmit.iris.gen.ParallaxTerrainProvider;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RegistryListEntity;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@AllArgsConstructor
@Desc("Represents an entity spawn during initial chunk generation")
@Data
public class IrisEntityInitialSpawn
{
	@Builder.Default
	@RegistryListEntity
	@Required
	@DontObfuscate
	@Desc("The entity")
	private String entity = "";

	@Builder.Default
	@MinNumber(1)
	@DontObfuscate
	@Desc("The 1 in RARITY chance for this entity to spawn")
	private int rarity = 1;

	@Builder.Default
	@MinNumber(1)
	@DontObfuscate
	@Desc("The minumum of this entity to spawn")
	private int minSpawns = 1;

	@Builder.Default
	@MinNumber(1)
	@DontObfuscate
	@Desc("The max of this entity to spawn")
	private int maxSpawns = 1;

	private final transient AtomicCache<RNG> rng = new AtomicCache<>();
	private final transient AtomicCache<IrisEntity> ent = new AtomicCache<>();

	public IrisEntityInitialSpawn()
	{

	}

	public void spawn(ParallaxTerrainProvider gen, Chunk c, RNG rng)
	{
		int spawns = rng.i(1, rarity) == 1 ? rng.i(minSpawns, maxSpawns) : 0;

		if(spawns > 0)
		{
			for(int i = 0; i < spawns; i++)
			{
				int x = (c.getX() * 16) + rng.i(15);
				int z = (c.getZ() * 16) + rng.i(15);
				int h = gen.getCarvedHeight(x, z, false);
				spawn100(gen, new Location(c.getWorld(), x, h, z));
			}
		}
	}

	public IrisEntity getRealEntity(ParallaxTerrainProvider g)
	{
		return ent.aquire(() -> g.getData().getEntityLoader().load(getEntity()));
	}

	public Entity spawn(ParallaxTerrainProvider g, Location at)
	{
		if(getRealEntity(g) == null)
		{
			return null;
		}

		if(rng.aquire(() -> new RNG(g.getTarget().getSeed() + 4)).i(1, getRarity()) == 1)
		{
			return spawn100(g, at);
		}

		return null;
	}

	private Entity spawn100(ParallaxTerrainProvider g, Location at)
	{
		return getRealEntity(g).spawn(g, at, rng.aquire(() -> new RNG(g.getTarget().getSeed() + 4)));
	}
}
