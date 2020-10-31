package com.volmit.iris.v2;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import com.volmit.iris.v2.scaffold.engine.EngineCompositeGenerator;

public class TestGen
{
	public static void gen(Player p)
	{
		p.teleport(new Location(new WorldCreator("t/" + UUID.randomUUID().toString())
				.generator(EngineCompositeGenerator.newStudioWorld("overworld")).createWorld(), 0, 70, 0));
	}
}
