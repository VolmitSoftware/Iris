package com.volmit.iris.generator.legacy.nms.v1X;

import org.bukkit.World;
import org.bukkit.WorldCreator;

import com.volmit.iris.generator.legacy.nms.INMSCreator;

class NMSCreator1X implements INMSCreator
{
	public World createWorld(WorldCreator creator, boolean loadSpawn)
	{
		return creator.createWorld();
	}
}
