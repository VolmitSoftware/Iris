package ninja.bytecode.iris.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import ninja.bytecode.shuriken.collections.KList;

public abstract class ParticleBase implements VisualEffect
{
	@Override
	public abstract void play(Location l, double range);

	@Override
	public abstract void play(Location l, Player p);

	@Override
	public void play(Location l)
	{
		play(l, 64);
	}

	@Override
	public void play(Location l, KList<Player> p)
	{
		for(Player i : p)
		{
			play(l, i);
		}
	}
}
