package ninja.bytecode.iris.pack;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.json.JSONObject;

public class IrisRegion
{
	private String name;
	private GList<IrisBiome> biomes;
	private double rarity;
	private boolean frozen;
	private IrisBiome beach;

	public IrisRegion(String name)
	{
		frozen = false;
		this.name = name;
		this.biomes = new GList<>();
		rarity = 1;
		beach = null;
	}

	public void load()
	{
		J.attempt(() ->
		{
			JSONObject o = Iris.getController(PackController.class).loadJSON("pack/regions/" + name + ".json");
			J.attempt(() -> frozen = o.getBoolean("frozen"));
			J.attempt(() -> name = o.getString("name"));
			J.attempt(() -> rarity = o.getDouble("rarity"));
			J.attempt(() -> beach = Iris.getController(PackController.class).getBiomeById(o.getString("beach")));
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

	public GList<IrisBiome> getBiomes()
	{
		return biomes;
	}

	public void setBiomes(GList<IrisBiome> biomes)
	{
		this.biomes = biomes;
	}

	public double getRarity()
	{
		return rarity;
	}

	public void setRarity(double rarity)
	{
		this.rarity = rarity;
	}

	public IrisBiome getBeach()
	{
		return beach;
	}

	public void setBeach(IrisBiome beach)
	{
		this.beach = beach;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((beach == null) ? 0 : beach.hashCode());
		result = prime * result + ((biomes == null) ? 0 : biomes.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		long temp;
		temp = Double.doubleToLongBits(rarity);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
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
		IrisRegion other = (IrisRegion) obj;
		if(beach == null)
		{
			if(other.beach != null)
				return false;
		}
		else if(!beach.equals(other.beach))
			return false;
		if(biomes == null)
		{
			if(other.biomes != null)
				return false;
		}
		else if(!biomes.equals(other.biomes))
			return false;

		if(name == null)
		{
			if(other.name != null)
				return false;
		}
		else if(!name.equals(other.name))
			return false;
		if(Double.doubleToLongBits(rarity) != Double.doubleToLongBits(other.rarity))
			return false;
		return true;
	}

	public boolean isFrozen()
	{
		return frozen;
	}
}
