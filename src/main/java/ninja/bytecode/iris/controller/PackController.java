package ninja.bytecode.iris.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.genobject.GenObject;
import ninja.bytecode.iris.generator.genobject.GenObjectGroup;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.pack.IrisDimension;
import ninja.bytecode.iris.pack.IrisPack;
import ninja.bytecode.iris.util.IrisController;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.execution.TaskExecutor;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskGroup;
import ninja.bytecode.shuriken.format.F;
import ninja.bytecode.shuriken.io.IO;
import ninja.bytecode.shuriken.json.JSONException;
import ninja.bytecode.shuriken.json.JSONObject;
import ninja.bytecode.shuriken.logging.L;

public class PackController implements IrisController
{
	private GMap<String, IrisDimension> dimensions;
	private GMap<String, IrisBiome> biomes;
	private GMap<String, GenObjectGroup> genObjectGroups;
	private boolean ready;

	@Override
	public void onStart()
	{
		dimensions = new GMap<>();
		biomes = new GMap<>();
		genObjectGroups = new GMap<>();
		ready = false;
	}

	@Override
	public void onStop()
	{

	}

	public boolean isReady()
	{
		return ready;
	}

	public void loadContent()
	{
		dimensions = new GMap<>();
		biomes = new GMap<>();
		genObjectGroups = new GMap<>();
		ready = false;
		PrecisionStopwatch p = PrecisionStopwatch.start();
		L.i("Loading Content");

		try
		{
			IrisPack master = new IrisPack(loadJSON("pack/manifest.json"));
			master.load();
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		L.v(ChatColor.LIGHT_PURPLE + "Processing Content");

		TaskExecutor exf = new TaskExecutor(Iris.settings.performance.compilerThreads, Iris.settings.performance.compilerPriority, "Iris Compiler");
		TaskGroup gg = exf.startWork();
		for(GenObjectGroup i : getGenObjectGroups().v())
		{
			gg.queue(i::processVariants);
		}

		gg.execute();
		exf.close();
		int m = 0;

		for(GenObjectGroup i : getGenObjectGroups().v())
		{
			m += i.size();
		}

		L.i(ChatColor.LIGHT_PURPLE + "Dimensions: " + ChatColor.WHITE + getDimensions().size());
		L.i(ChatColor.LIGHT_PURPLE + "Biomes: " + ChatColor.WHITE + getBiomes().size());
		L.i(ChatColor.LIGHT_PURPLE + "Object Groups: " + ChatColor.WHITE + F.f(getGenObjectGroups().size()));
		L.i(ChatColor.LIGHT_PURPLE + "Objects: " + ChatColor.WHITE + F.f(m));
		L.i(ChatColor.LIGHT_PURPLE + "Compilation Time: " + ChatColor.WHITE + F.duration(p.getMilliseconds(), 2));
		L.flush();
		L.i(ChatColor.GREEN + "Iris Dimensions Successfully Compiled!");
		ready = true;
	}

	public IrisDimension loadDimension(String s) throws JSONException, IOException
	{
		L.v(ChatColor.GOLD + "Loading Dimension: " + ChatColor.GRAY + "pack/dimensions/" + s + ".json");
		return new IrisDimension(loadJSON("pack/dimensions/" + s + ".json"));
	}

	public IrisBiome loadBiome(String s) throws JSONException, IOException
	{
		L.v(ChatColor.DARK_GREEN + "Loading Biome: " + ChatColor.GRAY + "pack/biomes/" + s + ".json");
		return new IrisBiome(loadJSON("pack/biomes/" + s + ".json"));
	}

	public GenObjectGroup loadSchematicGroup(String s)
	{
		GenObjectGroup g = GenObjectGroup.load("pack/objects/" + s);
		L.v(ChatColor.DARK_AQUA + "Loading Objects: " + ChatColor.GRAY + "pack/objects/" + s + ".ish");

		if(g != null)
		{
			Iris.getController(PackController.class).getGenObjectGroups().put(s, g);
			return g;
		}

		L.i("Cannot load Object Group: " + s);

		return null;
	}

	public GenObject loadSchematic(String s) throws IOException
	{
		return GenObject.load(loadResource("pack/objects/" + s + ".ish"));
	}

	public JSONObject loadJSON(String s) throws JSONException, IOException
	{
		return new JSONObject(IO.readAll(loadResource(s)));
	}

	public File loadFolder(String string)
	{
		File internal = internalResource(string);

		if(internal.exists())
		{
			return internal;
		}

		L.f(ChatColor.RED + "Cannot find folder: " + internal.getAbsolutePath());
		return null;
	}

	public InputStream loadResource(String string) throws IOException
	{
		File internal = internalResource(string);

		if(internal.exists())
		{
			L.flush();
			return new FileInputStream(internal);
		}

		else
		{
			L.f(ChatColor.RED + "Cannot find Resource: " + ChatColor.YELLOW + internal.getAbsolutePath());
			
			if(internal.getName().equals("manifest.json"))
			{
				L.f(ChatColor.RED + "Reloading Iris to fix manifest jar issues");
				Iris.instance.reload();
			}
			
			return null;
		}
	}

	private static File internalResource(String resource)
	{
		if(new File(Iris.instance.getDataFolder(), "pack").exists())
		{
			return new File(Iris.instance.getDataFolder(), resource);
		}

		return new File(System.getProperty("java.io.tmpdir") + "/Iris/" + resource);
	}

	public GMap<String, IrisDimension> getDimensions()
	{
		return dimensions;
	}

	public GMap<String, IrisBiome> getBiomes()
	{
		return biomes;
	}

	public GMap<String, GenObjectGroup> getGenObjectGroups()
	{
		return genObjectGroups;
	}
}
