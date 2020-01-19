package ninja.bytecode.iris.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.genobject.GenObject;
import ninja.bytecode.iris.generator.genobject.GenObjectGroup;
import ninja.bytecode.iris.pack.CompiledDimension;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.pack.IrisDimension;
import ninja.bytecode.iris.pack.IrisPack;
import ninja.bytecode.iris.util.IrisController;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.execution.TaskExecutor;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskGroup;
import ninja.bytecode.shuriken.format.F;
import ninja.bytecode.shuriken.io.IO;
import ninja.bytecode.shuriken.json.JSONException;
import ninja.bytecode.shuriken.json.JSONObject;
import ninja.bytecode.shuriken.logging.L;

public class PackController implements IrisController
{
	private GMap<String, CompiledDimension> compiledDimensions;
	private GMap<String, IrisDimension> dimensions;
	private GMap<String, IrisBiome> biomes;
	private GMap<String, GenObjectGroup> genObjectGroups;
	private boolean ready;

	@Override
	public void onStart()
	{
		compiledDimensions = new GMap<>();
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

	public GList<File> getFiles(File folder)
	{
		GList<File> buf = new GList<File>();

		if(!folder.exists())
		{
			return buf;
		}

		if(folder.isDirectory())
		{
			for(File i : folder.listFiles())
			{
				if(i.isFile())
				{
					buf.add(i);
				}

				else if(i.isDirectory())
				{
					buf.addAll(getFiles(folder));
				}
			}
		}

		return buf;
	}

	public void compile()
	{
		dimensions = new GMap<>();
		biomes = new GMap<>();
		genObjectGroups = new GMap<>();
		ready = false;
		PrecisionStopwatch p = PrecisionStopwatch.start();
		File dims = new File(Iris.instance.getDataFolder(), "dimensions");
		dims.mkdirs();

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
		
		for(GenObjectGroup i : genObjectGroups.v())
		{
			i.processVariants();
		}

		for(String i : dimensions.k())
		{
			IrisDimension id = dimensions.get(i);
			CompiledDimension d = new CompiledDimension(id);

			for(IrisBiome j : id.getBiomes())
			{
				d.registerBiome(j);
				GList<String> g = j.getSchematicGroups().k();
				g.sort();

				for(String k : g)
				{
					d.registerObject(genObjectGroups.get(k));

					if(j.isSnowy())
					{
						GenObjectGroup ggx = genObjectGroups.get(k).copy("-snowy-" + j.getSnow());
						ggx.applySnowFilter((int) (j.getSnow() * 4));
						d.registerObject(ggx);
					}
				}
			}

			d.sort();
			compiledDimensions.put(i, d);
		}

		for(String i : compiledDimensions.k())
		{
			CompiledDimension d = compiledDimensions.get(i);
			L.i(ChatColor.GREEN + i + ChatColor.WHITE + " (" + d.getEnvironment().toString().toLowerCase() + ")");
			L.i(ChatColor.DARK_GREEN + "  Biomes: " + ChatColor.GRAY + F.f(d.getBiomes().size()));
			L.i(ChatColor.DARK_GREEN + "  Objects: " + ChatColor.GRAY + F.f(d.countObjects()));
			L.flush();
		}

		L.i("");
		L.i(ChatColor.LIGHT_PURPLE + "Compilation Time: " + ChatColor.WHITE + F.duration(p.getMilliseconds(), 2));
		L.i(ChatColor.GREEN + "Iris Dimensions Successfully Compiled!");
		L.i("");
		L.flush();

		ready = true;
	}

	public CompiledDimension getDimension(String name)
	{
		return compiledDimensions.get(name);
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
			Iris.getController(PackController.class).genObjectGroups.put(s, g);
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

	public void registerBiome(String name, IrisBiome biome)
	{
		biomes.put(name, biome);
	}

	public void registerDimension(String i, IrisDimension d)
	{
		dimensions.put(i, d);
	}

	public void invalidate()
	{
		J.attempt(() -> new File(Iris.instance.getDataFolder(), "dimensions").delete());
		compiledDimensions.clear();
	}

	public IrisBiome getBiomeById(String id)
	{
		if(!biomes.containsKey(id))
		{
			try
			{
				biomes.put(id, Iris.getController(PackController.class).loadBiome(id));
			}

			catch(JSONException | IOException e)
			{
				e.printStackTrace();
			}
		}

		return biomes.get(id);
	}

	public void dispose()
	{
		compiledDimensions.clear();
		dimensions.clear();
		biomes.clear();
		genObjectGroups.clear();
	}
}
