package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntitySpawnEvent;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an entity spawn")
@Data
public class IrisEntitySpawnOverride
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

	private final transient AtomicCache<RNG> rng = new AtomicCache<>();
	private final transient AtomicCache<IrisEntity> ent = new AtomicCache<>();


	public Entity on(Engine g, Location at, EntityType t, EntitySpawnEvent ee)
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

	public Entity spawn(Engine g, Location at)
	{
		if(getRealEntity(g) == null)
		{
			return null;
		}

		if(rng.aquire(() -> new RNG(g.getTarget().getWorld().getSeed() + 4)).i(1, getRarity()) == 1)
		{
			return getRealEntity(g).spawn(g, at, rng.aquire(() -> new RNG(g.getTarget().getWorld().getSeed() + 4)));
		}

		return null;
	}

	public IrisEntity getRealEntity(Engine g)
	{
		return ent.aquire(() -> g.getData().getEntityLoader().load(getEntity()));
	}
}
