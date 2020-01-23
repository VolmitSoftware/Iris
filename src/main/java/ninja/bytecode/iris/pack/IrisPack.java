package ninja.bytecode.iris.pack;

import java.io.IOException;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.json.JSONArray;
import ninja.bytecode.shuriken.json.JSONException;
import ninja.bytecode.shuriken.json.JSONObject;

public class IrisPack
{
	private KList<String> dimensions;
	private KList<String> biomes;
	private KList<String> objects;

	public IrisPack()
	{
		this.dimensions = new KList<>();
		this.biomes = new KList<>();
		this.objects = new KList<>();
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

	public KList<String> fromArray(JSONArray ja)
	{
		KList<String> g = new KList<>();

		for(int i = 0; i < ja.length(); i++)
		{
			g.add(ja.getString(i));
		}

		return g;
	}

	public JSONArray toArray(KList<String> s)
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
			IrisDimension d = Iris.pack().loadDimension(i);
			Iris.pack().registerDimension(i, d);
		}
	}

	public void loadBiome(String s) throws JSONException, IOException
	{
		IrisBiome b = Iris.pack().loadBiome(s);
		Iris.pack().registerBiome(s, b);
	}
}
