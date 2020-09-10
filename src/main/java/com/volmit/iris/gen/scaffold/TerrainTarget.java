package com.volmit.iris.gen.scaffold;

import java.io.File;
import java.util.List;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;

import com.volmit.iris.util.KList;

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
	private static final KList<Player> emptyPlayers = new KList<>();
	private World realWorld;

	public void setRealWorld(World realWorld)
	{
		if(this.realWorld == null || realWorld != this.realWorld)
		{
			this.realWorld = realWorld;
			this.seed = realWorld.getSeed();
			this.folder = realWorld.getWorldFolder();
			this.environment = realWorld.getEnvironment();
			this.name = realWorld.getName();
		}
	}

	public static TerrainTarget from(World world)
	{
		// @NoArgsConstructor
		return new TerrainTargetBuilder().environment(world.getEnvironment()).seed(world.getSeed()).folder(world.getWorldFolder()).name(world.getName()).realWorld(world).build();
		//@done
	}

	public List<Player> getPlayers()
	{
		return realWorld != null ? realWorld.getPlayers() : emptyPlayers;
	}

	public boolean isWorld(World world)
	{
		return world.getName().equals(getName()) && world.getSeed() == getSeed() && getEnvironment().equals(world.getEnvironment()) && world.getWorldFolder().equals(getFolder());
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		TerrainTarget other = (TerrainTarget) obj;
		if(environment != other.environment)
			return false;
		if(folder == null)
		{
			if(other.folder != null)
				return false;
		}
		else if(!folder.equals(other.folder))
			return false;
		if(name == null)
		{
			if(other.name != null)
				return false;
		}
		else if(!name.equals(other.name))
			return false;
		if(seed != other.seed)
			return false;
		return true;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((environment == null) ? 0 : environment.hashCode());
		result = prime * result + ((folder == null) ? 0 : folder.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + (int) (seed ^ (seed >>> 32));
		return result;
	}
}
