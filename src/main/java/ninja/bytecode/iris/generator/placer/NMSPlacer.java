package ninja.bytecode.iris.generator.placer;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import mortar.api.nms.Catalyst;
import mortar.api.nms.NMP;
import mortar.api.world.MaterialBlock;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.Placer;
import ninja.bytecode.shuriken.collections.GSet;

public class NMSPlacer extends Placer
{
	private GSet<Chunk> c;

	public NMSPlacer(World world)
	{
		super(world);
		c = new GSet<>();
	}

	@SuppressWarnings("deprecation")
	@Override
	public MB get(Location l)
	{
		Block b = world.getBlockAt(l);
		return MB.of(b.getType(), b.getData());
	}

	@SuppressWarnings("deprecation")
	@Override
	public void set(Location l, MB mb)
	{
		Catalyst.host.setBlock(l, new MaterialBlock(mb.material.getId(), mb.data));
		c.add(l.getChunk());
	}

	@Override
	public int getHighestY(Location l)
	{
		return world.getHighestBlockYAt(l);
	}

	public void flush()
	{
		for(Chunk i : c)
		{
			for(Player j : NMP.CHUNK.nearbyPlayers(i))
			{
				NMP.CHUNK.refresh(j, i);
			}
		}

		c.clear();
	}
}
