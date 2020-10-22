package com.volmit.iris.gen.nms.v1X;

import org.bukkit.World;
import org.bukkit.WorldCreator;

import com.volmit.iris.gen.nms.INMSCreator;

class NMSCreator1X implements INMSCreator
{
	public World createWorld(WorldCreator creator, boolean loadSpawn)
	{
		return creator.createWorld();
	}
}
