package ninja.bytecode.iris.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import ninja.bytecode.shuriken.collections.GList;

public interface VisualEffect
{
	public void play(Location l);

	public void play(Location l, double r);

	public void play(Location l, Player p);

	public void play(Location l, GList<Player> p);
}
