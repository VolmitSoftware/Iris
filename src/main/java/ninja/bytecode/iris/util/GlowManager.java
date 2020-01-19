package ninja.bytecode.iris.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class GlowManager
{
	public static GRT f = null;

	public static Glower create(Entity e, Player observer)
	{
		return f.getGlower(e, observer);
	}
}
