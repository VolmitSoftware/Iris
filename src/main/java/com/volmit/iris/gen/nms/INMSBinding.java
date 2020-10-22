package com.volmit.iris.gen.nms;

import org.bukkit.World;
import org.bukkit.WorldCreator;

public interface INMSBinding
{
	public INMSCreator getCreator();

	default World createWorld(WorldCreator creator)
	{
		return getCreator().createWorld(creator);
	}

	default World createWorld(WorldCreator creator, boolean loadSpawn)
	{
		return getCreator().createWorld(creator, loadSpawn);
	}
}
