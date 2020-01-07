package ninja.bytecode.iris.spec;

import java.io.IOException;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.json.JSONArray;
import ninja.bytecode.shuriken.json.JSONException;
import ninja.bytecode.shuriken.json.JSONObject;

public class IrisDimension
{
	private String name;
	private Environment environment;
	GList<IrisBiome> biomes;
	
	public IrisDimension(JSONObject o) throws JSONException, IOException
	{
		this();
		fromJSON(o);
	}
	
	public IrisDimension()
	{
		biomes = new GList<IrisBiome>();
		environment = Environment.NORMAL;
	}
	
	public void fromJSON(JSONObject o) throws JSONException, IOException
	{
		name = o.getString("name");
		J.attempt(() -> environment = Environment.valueOf(o.getString("environment").toUpperCase().replaceAll(" ", "_")));
		
		try
		{
			biomes = biomesFromArray(o.getJSONArray("biomes"));
		}
		
		catch(Throwable e)
		{
			e.printStackTrace();
		}
	}
	
	public JSONObject toJSON()
	{
		JSONObject o = new JSONObject();
		
		o.put("name", name);
		o.put("environment", environment.name().toLowerCase().replaceAll("_", " "));
		o.put("biomes", biomesToArray(biomes));
		
		return o;
	}
	
	private GList<IrisBiome> biomesFromArray(JSONArray a) throws JSONException, IOException
	{
		GList<IrisBiome> b = new GList<>();
		
		for(int i = 0; i < a.length(); i++)
		{
			IrisBiome bb = Iris.loadBiome(a.getString(i));
			Iris.biomes.put(a.getString(i), bb);
			b.add(bb);
		}
		
		return b;
	}

	private JSONArray biomesToArray(GList<IrisBiome> b)
	{
		JSONArray a = new JSONArray();
		
		for(IrisBiome i : b)
		{
			a.put(i.getName().toLowerCase().replaceAll(" ", "_"));
		}
		
		return a;
	}

	public GList<IrisBiome> getBiomes()
	{
		return biomes;
	}

	public String getName()
	{
		return name;
	}
}
