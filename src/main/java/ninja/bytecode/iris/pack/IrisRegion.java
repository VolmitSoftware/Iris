package ninja.bytecode.iris.pack;

import java.util.Objects;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.json.JSONObject;

public class IrisRegion
{
	private String name;
	private KList<IrisBiome> biomes;
	private IrisBiome ocean;
	private IrisBiome lake;
	private IrisBiome lakeBeach;
	private IrisBiome channel;
	private IrisBiome beach;

	public IrisRegion(String name)
	{
		this.name = name;
		this.biomes = new KList<>();
		beach = null;
		ocean = null;
		lake = null;
		lakeBeach = null;
		channel = null;
	}

	public void load()
	{
		J.attempt(() ->
		{
			JSONObject o = Iris.getController(PackController.class).loadJSON("pack/regions/" + name + ".json");
			J.attempt(() -> name = o.getString("name"));
			J.attempt(() -> ocean = Iris.getController(PackController.class).getBiomeById(o.getString("ocean")));
			J.attempt(() -> beach = Iris.getController(PackController.class).getBiomeById(o.getString("beach")));
			J.attempt(() -> lake = Iris.getController(PackController.class).getBiomeById(o.getString("lake")));
			J.attempt(() -> lakeBeach = Iris.getController(PackController.class).getBiomeById(o.getString("shore")));
			J.attempt(() -> channel = Iris.getController(PackController.class).getBiomeById(o.getString("channel")));
		});
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public KList<IrisBiome> getBiomes()
	{
		return biomes;
	}

	public void setBiomes(KList<IrisBiome> biomes)
	{
		this.biomes = biomes;
	}

	public IrisBiome getBeach()
	{
		return beach;
	}

	public void setBeach(IrisBiome beach)
	{
		this.beach = beach;
	}

	public IrisBiome getOcean()
	{
		return ocean;
	}

	public IrisBiome getLake()
	{
		return lake;
	}

	public IrisBiome getShore()
	{
		return lakeBeach;
	}

	public IrisBiome getChannel()
	{
		return channel;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(beach, biomes, channel, lake, lakeBeach, name, ocean);
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
		{
			return true;
		}
		if(!(obj instanceof IrisRegion))
		{
			return false;
		}
		IrisRegion other = (IrisRegion) obj;
		return Objects.equals(beach, other.beach) && Objects.equals(biomes, other.biomes) && Objects.equals(channel, other.channel) && Objects.equals(lake, other.lake) && Objects.equals(lakeBeach, other.lakeBeach) && Objects.equals(name, other.name) && Objects.equals(ocean, other.ocean);
	}
}
