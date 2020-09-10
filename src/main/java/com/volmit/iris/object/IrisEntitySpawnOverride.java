package com.volmit.iris.object;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntitySpawnEvent;

import com.volmit.iris.gen.IrisTerrainProvider;
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
@Desc("Represents an entity spawn")
@Data
public class IrisEntitySpawnOverride
{
	@Builder.Default
	@RegistryListEntity
	@Required
	@DontObfuscate
	@Desc("The entity")
	private String entity = "";

	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("If the following entity type spawns, spawn this entity. Set to unknown for any entity spawn")
	private EntityType trigger = EntityType.UNKNOWN;

	@Builder.Default
	@DontObfuscate
	@Desc("If the source is triggered, cancel spawning the original entity instead of ADDING a new entity.")
	private boolean cancelSourceSpawn = false;

	@Builder.Default
	@MinNumber(1)
	@DontObfuscate
	@Desc("The 1 in RARITY chance for this entity to spawn")
	private int rarity = 1;

	private final transient AtomicCache<RNG> rng = new AtomicCache<>();
	private final transient AtomicCache<IrisEntity> ent = new AtomicCache<>();

	public IrisEntitySpawnOverride()
	{

	}

	public Entity on(IrisTerrainProvider g, Location at, EntityType t, EntitySpawnEvent ee)
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

	public IrisEntity getRealEntity(IrisTerrainProvider g)
	{
		return ent.aquire(() -> g.getData().getEntityLoader().load(getEntity()));
	}

	public Entity spawn(IrisTerrainProvider g, Location at)
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
}
