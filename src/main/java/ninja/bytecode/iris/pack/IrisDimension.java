package ninja.bytecode.iris.pack;

import java.io.IOException;

import org.bukkit.World.Environment;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.execution.TaskExecutor;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskGroup;
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
		fromJSON(o, true);
	}

	public void fromJSON(JSONObject o, boolean chain) throws JSONException, IOException
	{
		name = o.getString("name");
		J.attempt(() -> environment = Environment.valueOf(o.getString("environment").toUpperCase().replaceAll(" ", "_")));

		try
		{
			biomes = chain ? biomesFromArray(o.getJSONArray("biomes")) : new GList<>();
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
			int ii = i;

			IrisBiome bb = Iris.getController(PackController.class).loadBiome(a.getString(ii));
			Iris.getController(PackController.class).registerBiome(a.getString(ii), bb);
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

	public Environment getEnvironment()
	{
		return environment;
	}

	public void dispose()
	{
		biomes.clear();
	}
}
