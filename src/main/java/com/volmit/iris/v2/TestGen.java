package com.volmit.iris.v2;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.volmit.iris.util.KList;
import com.volmit.iris.v2.scaffold.engine.EngineCompositeGenerator;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.PrecisionStopwatch;
import org.jetbrains.annotations.NotNull;

public class TestGen
{
	public static void gen(Player p)
	{
		IrisTerrainGenerator tg = new IrisTerrainGenerator(1337, Iris.globaldata.getDimensionLoader().load("overworld"), Iris.globaldata);
		p.teleport(new Location(new WorldCreator("t/" + UUID.randomUUID().toString())
				.generator(EngineCompositeGenerator.newStudioWorld("overworld")).createWorld(), 0, 200, 0));
	}
}
