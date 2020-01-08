package ninja.bytecode.iris.pack;

import java.io.IOException;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.json.JSONArray;
import ninja.bytecode.shuriken.json.JSONException;
import ninja.bytecode.shuriken.json.JSONObject;

public class IrisPack
{
	private GList<String> dimensions;
	private GList<String> biomes;
	private GList<String> objects;
	
	public IrisPack()
	{
		this.dimensions = new GList<>();
		this.biomes = new GList<>();
		this.objects = new GList<>();
	}
	
	public IrisPack(JSONObject o)
	{
		this();
		fromJSON(o);
	}
	
	public void fromJSON(JSONObject o)
	{
		J.attempt(() -> dimensions = fromArray(o.getJSONArray("dimensions")));
		J.attempt(() -> biomes = fromArray(o.getJSONArray("biomes")));
		J.attempt(() -> objects = fromArray(o.getJSONArray("objects")));
	}
	
	public JSONObject toJSON()
	{
		JSONObject o = new JSONObject();
		o.put("dimensions", toArray(dimensions));
		o.put("biomes", toArray(biomes));
		o.put("objects", toArray(objects));
		
		return o;
	}
	
	public GList<String> fromArray(JSONArray ja)
	{
		GList<String> g = new GList<>();
		
		for(int i = 0; i < ja.length(); i++)
		{
			g.add(ja.getString(i));
		}
		
		return g;
	}
	
	public JSONArray toArray(GList<String> s)
	{
		JSONArray ja = new JSONArray();
		
		for(String i : s)
		{
			ja.put(i);
		}
		
		return ja;
	}

	public void load() throws JSONException, IOException
	{
		for(String i : dimensions)
		{
			IrisDimension d = Iris.getController(PackController.class).loadDimension(i);
			Iris.getController(PackController.class).getDimensions().put(i, d);
		}
	}
	
	public void loadBiome(String s) throws JSONException, IOException
	{
		IrisBiome b = Iris.getController(PackController.class).loadBiome(s);
		Iris.getController(PackController.class).getBiomes().put(s, b);
	}
}
