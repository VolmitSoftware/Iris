package ninja.bytecode.iris.generator.placer;

import org.bukkit.Location;
import org.bukkit.World;

import ninja.bytecode.iris.util.AtomicChunkData;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.Placer;

public class AtomicPlacer extends Placer
{
	private AtomicChunkData data;
	private ChunkPlan plan;

	public AtomicPlacer(World world)
	{
		super(world);
	}

	public void bind(AtomicChunkData data, ChunkPlan plan)
	{
		this.data = data;
		this.plan = plan;
	}

	@Override
	public MB get(Location l)
	{
		return MB.of(data.getType(l.getBlockX(), l.getBlockY(), l.getBlockZ()), data.getData(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
	}

	@SuppressWarnings("deprecation")
	@Override
	public void set(Location l, MB mb)
	{
		data.setBlock(l.getBlockX(), l.getBlockY(), l.getBlockZ(), mb.material.getId(), mb.data);
	}

	@Override
	public int getHighestY(Location l)
	{
		return plan.getRealHeight(l.getBlockX(), l.getBlockZ());
	}
}
