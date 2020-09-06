package com.volmit.iris.gen.bindings;

import java.io.File;

import org.bukkit.World;
import org.bukkit.World.Environment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TerrainTarget
{
	private long seed;
	private Environment environment;
	private String name;
	private File folder;

	public static TerrainTarget from(World world)
	{
		//@builder
		return new TerrainTargetBuilder()
				.environment(world.getEnvironment())
				.seed(world.getSeed())
				.folder(world.getWorldFolder())
				.name(world.getName())
				.build();
		//@done
	}
}
