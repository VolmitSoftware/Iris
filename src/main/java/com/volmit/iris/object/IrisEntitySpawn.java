package com.volmit.iris.object;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntitySpawnEvent;

import com.volmit.iris.gen.IrisChunkGenerator;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RegistryListEntity;
import com.volmit.iris.util.Required;

import lombok.Data;

@Desc("Represents an entity spawn")
@Data
public class IrisEntitySpawn
{
	@RegistryListEntity
	@Required
	@DontObfuscate
	@Desc("The entity")
	private String entity = "";

	@Required
	@DontObfuscate
	@Desc("If the following entity type spawns, spawn this entity. Set to unknown for any entity spawn")
	private EntityType trigger = EntityType.UNKNOWN;

	@DontObfuscate
	@Desc("If the source is triggered, cancel spawning the original entity instead of ADDING a new entity.")
	private boolean cancelSourceSpawn = false;

	@MinNumber(1)
	@DontObfuscate
	@Desc("The 1 in RARITY chance for this entity to spawn")
	private int rarity = 1;

	private transient AtomicCache<RNG> rng = new AtomicCache<>();
	private transient AtomicCache<IrisEntity> ent = new AtomicCache<>();

	public Entity on(IrisChunkGenerator g, Location at, EntityType t, EntitySpawnEvent ee)
	{
		if(!trigger.equals(EntityType.UNKNOWN))
		{
			if(!trigger.equals(t))
			{
				return null;
			}
		}

		Entity e = spawn(g, at);

		if(e != null && isCancelSourceSpawn())
		{
			ee.setCancelled(true);
			ee.getEntity().remove();
		}

		return e;
	}

	public IrisEntity getRealEntity(IrisChunkGenerator g)
	{
		return ent.aquire(() -> g.getData().getEntityLoader().load(getEntity()));
	}

	public Entity spawn(IrisChunkGenerator g, Location at)
	{
		if(getRealEntity(g) == null)
		{
			return null;
		}

		if(rng.aquire(() -> new RNG(g.getTarget().getSeed() + 4)).i(1, getRarity()) == 1)
		{
			return getRealEntity(g).spawn(g, at, rng.aquire(() -> new RNG(g.getTarget().getSeed() + 4)));
		}

		return null;
	}

	public IrisEntitySpawn()
	{

	}
}
