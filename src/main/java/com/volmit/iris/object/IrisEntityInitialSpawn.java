package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an entity spawn during initial chunk generation")
@Data
public class IrisEntityInitialSpawn
{
	@RegistryListEntity
	@Required
	@DontObfuscate
	@Desc("The entity")
	private String entity = "";

	@MinNumber(1)
	@DontObfuscate
	@Desc("The 1 in RARITY chance for this entity to spawn")
	private int rarity = 1;

	@MinNumber(1)
	@DontObfuscate
	@Desc("The minumum of this entity to spawn")
	private int minSpawns = 1;

	@MinNumber(1)
	@DontObfuscate
	@Desc("The max of this entity to spawn")
	private int maxSpawns = 1;

	private final transient AtomicCache<RNG> rng = new AtomicCache<>();
	private final transient AtomicCache<IrisEntity> ent = new AtomicCache<>();

	public void spawn(Engine gen, Chunk c, RNG rng)
	{
		int spawns = rng.i(1, rarity) == 1 ? rng.i(minSpawns, maxSpawns) : 0;

		if(spawns > 0)
		{
			for(int i = 0; i < spawns; i++)
			{
				int x = (c.getX() * 16) + rng.i(15);
				int z = (c.getZ() * 16) + rng.i(15);
				int h = gen.getHeight(x, z) + gen.getMinHeight();
				spawn100(gen, new Location(c.getWorld(), x, h, z));
			}
		}
	}

	public IrisEntity getRealEntity(Engine g)
	{
		return ent.aquire(() -> g.getData().getEntityLoader().load(getEntity()));
	}

	public Entity spawn(Engine g, Location at)
	{
		if(getRealEntity(g) == null)
		{
			return null;
		}

		if(rng.aquire(() -> new RNG(g.getTarget().getWorld().getSeed() + 4)).i(1, getRarity()) == 1)
		{
			return spawn100(g, at);
		}

		return null;
	}

	private Entity spawn100(Engine g, Location at)
	{
		return getRealEntity(g).spawn(g, at.clone().add(0.5, 1, 0.5), rng.aquire(() -> new RNG(g.getTarget().getWorld().getSeed() + 4)));
	}
}
