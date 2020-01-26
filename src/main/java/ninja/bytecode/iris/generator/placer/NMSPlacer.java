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
import ninja.bytecode.shuriken.collections.KSet;
import ninja.bytecode.shuriken.execution.J;

public class NMSPlacer extends Placer
{
	private KSet<Chunk> c;

	public NMSPlacer(World world)
	{
		super(world);
		c = new KSet<>();
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

	@Override
	public int getHighestYUnderwater(Location l)
	{
		int y = getHighestY(l);

		while(y > 0)
		{
			y--;
			Block b = l.getWorld().getBlockAt(l.getBlockX(), y, l.getBlockZ());
			if(!b.isEmpty())
			{
				if(b.isLiquid())
				{
					continue;
				}

				return y + 1;
			}
		}

		return y;
	}

	public void flush()
	{
		J.attempt(() ->
		{
			for(Chunk i : c)
			{
				NMP.host.relight(i);

				J.a(() ->
				{
					for(Player j : i.getWorld().getPlayers())
					{
						NMP.CHUNK.refreshIgnorePosition(j, i);
					}
				});
			}

			c.clear();
		});
	}
}
