package ninja.bytecode.iris.util;

import java.util.function.Function;

import org.bukkit.World;

public enum PlacerType
{
	BUKKIT((w) -> new BukkitPlacer(w, true)),
	BUKKIT_NO_PHYSICS((w) -> new BukkitPlacer(w, false)),
	NMS((w) -> new NMSPlacer12(w));
	
	private Function<World, IPlacer> placer;
	
	private PlacerType(Function<World, IPlacer> placer)
	{
		this.placer = placer;
	}
	
	public IPlacer get(World world)
	{
		return placer.apply(world);
	}
}
