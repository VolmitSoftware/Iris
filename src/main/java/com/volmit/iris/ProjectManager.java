package com.volmit.iris;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.potion.PotionEffectType;
import org.zeroturnaround.zip.ZipUtil;

import com.google.gson.Gson;
import com.volmit.iris.gen.IrisChunkGenerator;
import com.volmit.iris.gen.post.Post;
import com.volmit.iris.object.DecorationPart;
import com.volmit.iris.object.Dispersion;
import com.volmit.iris.object.Envelope;
import com.volmit.iris.object.InterpolationMethod;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeGeneratorLink;
import com.volmit.iris.object.IrisBiomeMutation;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisNoiseGenerator;
import com.volmit.iris.object.IrisObjectPlacement;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisStructure;
import com.volmit.iris.object.IrisStructureTile;
import com.volmit.iris.object.StructureTileCondition;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.IrisPostBlockFilter;
import com.volmit.iris.util.J;
import com.volmit.iris.util.JSONArray;
import com.volmit.iris.util.JSONException;
import com.volmit.iris.util.JSONObject;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.M;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.MortarSender;
import com.volmit.iris.util.O;
import com.volmit.iris.util.Required;

import lombok.Data;

@Data
public class ProjectManager
{
	private IrisChunkGenerator currentProject;

	public ProjectManager()
	{

	}

	public boolean isProjectOpen()
	{
		return currentProject != null;
	}

	public void open(MortarSender sender, String dimm)
	{
		open(sender, dimm, () ->
		{
		});
	}

	public void open(MortarSender sender, String dimm, Runnable onDone)
	{
		IrisDimension d = Iris.globaldata.getDimensionLoader().load(dimm);
		J.attemptAsync(() ->
		{
			try
			{
				File f = d.getLoadFile().getParentFile().getParentFile();

				for(File i : f.listFiles())
				{
					if(i.getName().endsWith(".code-workspace"))
					{
						sender.sendMessage("Updating Workspace");
						updateWorkspace(i);
						Desktop.getDesktop().open(i);
						break;
					}
				}
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		});
		if(d == null)
		{
			sender.sendMessage("Can't find dimension: " + dimm);
			return;
		}

		if(isProjectOpen())
		{
			sender.sendMessage("Please Wait. Closing Current Project...");
			close();
		}

		sender.sendMessage("Loading " + dimm + "...");
		IrisChunkGenerator gx = new IrisChunkGenerator(dimm, IrisSettings.get().threads);
		currentProject = gx;
		gx.setDev(true);
		sender.sendMessage("Generating with " + IrisSettings.get().threads + " threads per chunk");
		O<Boolean> done = new O<Boolean>();
		done.set(false);

		J.a(() ->
		{
			double last = 0;
			int req = 740;
			while(!done.get())
			{
				boolean derp = false;
				double v = (double) gx.getGenerated() / (double) req;

				if(last > v || v > 1)
				{
					derp = true;
					v = last;
				}

				else
				{
					last = v;
				}

				sender.sendMessage("Generating " + Form.pc(v) + (derp ? " (Waiting on Server...)" : ""));
				J.sleep(3000);
			}
		});

		World world = Bukkit.createWorld(new WorldCreator("iris/" + UUID.randomUUID()).seed(1337).generator(gx).generateStructures(false).type(WorldType.NORMAL).environment(d.getEnvironment()));
		done.set(true);
		sender.sendMessage("Generating 100%");

		if(sender.isPlayer())
		{
			sender.player().teleport(new Location(world, 150, 150, 275));
		}

		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
		{
			sender.sendMessage("Hotloading Active! Change any files and watch them appear as you load new chunks!");

			if(sender.isPlayer())
			{
				sender.player().setGameMode(GameMode.SPECTATOR);
			}

			onDone.run();
		}, 0);
	}

	public void close()
	{
		if(isProjectOpen())
		{
			currentProject.close();
			File folder = currentProject.getWorld().getWorldFolder();
			Bukkit.unloadWorld(currentProject.getWorld(), false);
			currentProject = null;
			Iris.globaldata.getObjectLoader().clearCache();
			Iris.globaldata.getBiomeLoader().clearCache();
			Iris.globaldata.getRegionLoader().clearCache();
			Iris.globaldata.getGeneratorLoader().clearCache();
			Iris.globaldata.getDimensionLoader().clearCache();
			J.attemptAsync(() -> IO.delete(folder));
		}
	}

	public File compilePackage(MortarSender sender, String dim, boolean obfuscate)
	{
		String dimm = dim;
		IrisDimension dimension = Iris.globaldata.getDimensionLoader().load(dimm);
		File folder = new File(Iris.instance.getDataFolder(), "exports/" + dimension.getLoadKey());
		folder.mkdirs();
		Iris.info("Packaging Dimension " + dimension.getName() + " " + (obfuscate ? "(Obfuscated)" : ""));
		KSet<IrisRegion> regions = new KSet<>();
		KSet<IrisBiome> biomes = new KSet<>();
		KSet<IrisStructure> structures = new KSet<>();
		KSet<IrisGenerator> generators = new KSet<>();
		dimension.getRegions().forEach((i) -> regions.add(Iris.globaldata.getRegionLoader().load(i)));
		regions.forEach((i) -> biomes.addAll(i.getAllBiomes(null)));
		biomes.forEach((i) -> i.getGenerators().forEach((j) -> generators.add(j.getCachedGenerator(null))));
		regions.forEach((i) -> i.getStructures().forEach((j) -> structures.add(j.getStructure(null))));
		biomes.forEach((i) -> i.getStructures().forEach((j) -> structures.add(j.getStructure(null))));
		KMap<String, String> renameObjects = new KMap<>();
		String a = "";
		StringBuilder b = new StringBuilder();
		StringBuilder c = new StringBuilder();
		sender.sendMessage("Serializing Objects");

		for(IrisStructure i : structures)
		{
			for(IrisStructureTile j : i.getTiles())
			{
				b.append(j.hashCode());
				KList<String> newNames = new KList<>();

				for(String k : j.getObjects())
				{
					if(renameObjects.containsKey(k))
					{
						newNames.add(renameObjects.get(k));
						continue;
					}

					String name = UUID.randomUUID().toString().replaceAll("-", "");
					b.append(name);
					newNames.add(name);
					renameObjects.put(k, name);
				}

				j.setObjects(newNames);
			}
		}

		for(IrisBiome i : biomes)
		{
			for(IrisObjectPlacement j : i.getObjects())
			{
				b.append(j.hashCode());
				KList<String> newNames = new KList<>();

				for(String k : j.getPlace())
				{
					if(renameObjects.containsKey(k))
					{
						newNames.add(renameObjects.get(k));
						continue;
					}

					String name = UUID.randomUUID().toString().replaceAll("-", "");
					b.append(name);
					newNames.add(name);
					renameObjects.put(k, name);
				}

				j.setPlace(newNames);
			}
		}

		for(IrisBiomeMutation i : dimension.getMutations())
		{
			for(IrisObjectPlacement j : i.getObjects())
			{
				b.append(j.hashCode());
				KList<String> newNames = new KList<>();

				for(String k : j.getPlace())
				{
					if(renameObjects.containsKey(k))
					{
						newNames.add(renameObjects.get(k));
						continue;
					}

					String name = UUID.randomUUID().toString().replaceAll("-", "");
					b.append(name);
					newNames.add(name);
					renameObjects.put(k, name);
				}

				j.setPlace(newNames);
			}
		}

		KMap<String, KList<String>> lookupObjects = renameObjects.flip();
		StringBuilder gb = new StringBuilder();
		ChronoLatch cl = new ChronoLatch(1000);
		O<Integer> ggg = new O<Integer>();
		ggg.set(0);
		biomes.forEach((i) -> i.getObjects().forEach((j) -> j.getPlace().forEach((k) ->
		{
			try
			{
				File f = Iris.globaldata.getObjectLoader().findFile(lookupObjects.get(k).get(0));
				IO.copyFile(f, new File(folder, "objects/" + k + ".iob"));
				gb.append(IO.hash(f));
				ggg.set(ggg.get() + 1);

				if(cl.flip())
				{
					int g = ggg.get();
					ggg.set(0);
					sender.sendMessage("Wrote another " + g + " Objects");
				}
			}

			catch(Throwable e)
			{

			}
		})));

		structures.forEach((i) -> i.getTiles().forEach((j) -> j.getObjects().forEach((k) ->
		{
			try
			{
				File f = Iris.globaldata.getObjectLoader().findFile(lookupObjects.get(k).get(0));
				IO.copyFile(f, new File(folder, "objects/" + k + ".iob"));
				gb.append(IO.hash(f));
				ggg.set(ggg.get() + 1);

				if(cl.flip())
				{
					int g = ggg.get();
					ggg.set(0);
					sender.sendMessage("Wrote another " + g + " Objects");
				}
			}

			catch(Throwable e)
			{

			}
		})));

		dimension.getMutations().forEach((i) -> i.getObjects().forEach((j) -> j.getPlace().forEach((k) ->
		{
			try
			{
				File f = Iris.globaldata.getObjectLoader().findFile(lookupObjects.get(k).get(0));
				IO.copyFile(f, new File(folder, "objects/" + k + ".iob"));
				gb.append(IO.hash(f));
				ggg.set(ggg.get() + 1);

				if(cl.flip())
				{
					int g = ggg.get();
					ggg.set(0);
					sender.sendMessage("Wrote another " + g + " Objects");
				}
			}

			catch(Throwable e)
			{

			}
		})));

		b.append(IO.hash(gb.toString()));
		c.append(IO.hash(b.toString()));
		b = new StringBuilder();

		Iris.info("Writing Dimensional Scaffold");

		try
		{
			a = new JSONObject(new Gson().toJson(dimension)).toString(0);
			IO.writeAll(new File(folder, "dimensions/" + dimension.getLoadKey() + ".json"), a);
			b.append(IO.hash(a));

			for(IrisGenerator i : generators)
			{
				a = new JSONObject(new Gson().toJson(i)).toString(0);
				IO.writeAll(new File(folder, "generators/" + i.getLoadKey() + ".json"), a);
				b.append(IO.hash(a));
			}

			c.append(IO.hash(b.toString()));
			b = new StringBuilder();

			for(IrisRegion i : regions)
			{
				a = new JSONObject(new Gson().toJson(i)).toString(0);
				IO.writeAll(new File(folder, "regions/" + i.getLoadKey() + ".json"), a);
				b.append(IO.hash(a));
			}

			for(IrisStructure i : structures)
			{
				a = new JSONObject(new Gson().toJson(i)).toString(0);
				IO.writeAll(new File(folder, "structures/" + i.getLoadKey() + ".json"), a);
				b.append(IO.hash(a));
			}

			for(IrisBiome i : biomes)
			{
				a = new JSONObject(new Gson().toJson(i)).toString(0);
				IO.writeAll(new File(folder, "biomes/" + i.getLoadKey() + ".json"), a);
				b.append(IO.hash(a));
			}

			c.append(IO.hash(b.toString()));
			b = new StringBuilder();
			String finalHash = IO.hash(c.toString());
			JSONObject meta = new JSONObject();
			meta.put("hash", finalHash);
			meta.put("time", M.ms());
			meta.put("version", dimension.getVersion());
			IO.writeAll(new File(folder, "package.json"), meta.toString(0));
			File p = new File(Iris.instance.getDataFolder(), "exports/" + dimension.getLoadKey() + ".iris");
			Iris.info("Compressing Package");
			ZipUtil.pack(folder, p, 9);
			IO.delete(folder);

			sender.sendMessage("Package Compiled!");
			return p;
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}
		sender.sendMessage("Failed!");
		return null;
	}

	public void create(MortarSender sender, String s)
	{
		IrisDimension dimension = new IrisDimension();
		dimension.setLoadKey(s);
		dimension.setName(Form.capitalizeWords(s.replaceAll("\\Q-\\E", " ")));

		if(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "dimensions", dimension.getLoadKey() + ".json").exists())
		{
			sender.sendMessage("Project Already Exists! Open it instead!");
			return;
		}
		sender.sendMessage("Creating New Project \"" + dimension.getName() + "\"...");
		IrisRegion exampleRegion = new IrisRegion();
		exampleRegion.setName("Example Region");
		exampleRegion.setLoadKey("example-region");
		IrisBiome exampleLand1 = new IrisBiome();
		exampleLand1.setName("Example Land 1");
		exampleLand1.setLoadKey("land-1");
		IrisBiome exampleShore1 = new IrisBiome();
		exampleShore1.setName("Example Shore");
		exampleShore1.setLoadKey("shore");
		IrisBiome exampleOcean1 = new IrisBiome();
		exampleOcean1.setName("Example Sea");
		exampleOcean1.setLoadKey("sea");
		IrisBiome exampleLand2 = new IrisBiome();
		exampleLand2.setName("Example Land 2");
		exampleLand2.setLoadKey("land-2");
		exampleLand2.setRarity(4);
		dimension.setSeaZoom(1);
		dimension.setLandZoom(1.5);
		IrisGenerator gen = new IrisGenerator();
		IrisNoiseGenerator gg = new IrisNoiseGenerator(true);
		gen.setInterpolationFunction(InterpolationMethod.HERMITE);
		gen.setInterpolationScale(185);
		gen.getComposite().add(gg);
		gen.setLoadKey("example-generator");
		IrisBiomeGeneratorLink b1 = new IrisBiomeGeneratorLink();
		b1.setGenerator(gen.getLoadKey());
		b1.setMin(3);
		b1.setMax(7);
		IrisBiomeGeneratorLink b2 = new IrisBiomeGeneratorLink();
		b2.setGenerator(gen.getLoadKey());
		b2.setMin(12);
		b2.setMax(35);
		IrisBiomeGeneratorLink b3 = new IrisBiomeGeneratorLink();
		b3.setGenerator(gen.getLoadKey());
		b3.setMin(-1);
		b3.setMax(1);
		IrisBiomeGeneratorLink b4 = new IrisBiomeGeneratorLink();
		b4.setGenerator(gen.getLoadKey());
		b4.setMin(-5);
		b4.setMax(-38);
		exampleLand2.getLayers().get(0).getPalette().clear();
		exampleLand2.getLayers().get(0).getPalette().add("RED_SAND");
		exampleShore1.getLayers().get(0).getPalette().clear();
		exampleShore1.getLayers().get(0).getPalette().add("SAND");
		exampleOcean1.getLayers().get(0).getPalette().clear();
		exampleOcean1.getLayers().get(0).getPalette().add("SAND");
		exampleLand1.getGenerators().clear();
		exampleLand1.getGenerators().add(b1);
		exampleLand2.getGenerators().clear();
		exampleLand2.getGenerators().add(b2);
		exampleShore1.getGenerators().clear();
		exampleShore1.getGenerators().add(b3);
		exampleOcean1.getGenerators().clear();
		exampleOcean1.getGenerators().add(b4);
		exampleRegion.getLandBiomes().add(exampleLand1.getLoadKey());
		exampleRegion.getLandBiomes().add(exampleLand2.getLoadKey());
		exampleRegion.getShoreBiomes().add(exampleShore1.getLoadKey());
		exampleRegion.getSeaBiomes().add(exampleOcean1.getLoadKey());
		dimension.getRegions().add(exampleRegion.getLoadKey());

		try
		{
			JSONObject ws = newWorkspaceConfig();
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "dimensions", dimension.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(dimension)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "regions", exampleRegion.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleRegion)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "biomes", exampleLand1.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleLand1)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "biomes", exampleLand2.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleLand2)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "biomes", exampleShore1.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleShore1)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "biomes", exampleOcean1.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleOcean1)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "generators", gen.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(gen)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), dimension.getLoadKey() + ".code-workspace"), ws.toString(4));
			Iris.proj.open(sender, dimension.getName());
		}

		catch(JSONException | IOException e)
		{
			sender.sendMessage("Failed! Check the console.");
			e.printStackTrace();
		}
	}

	private JSONObject newWorkspaceConfig()
	{
		JSONObject ws = new JSONObject();
		JSONArray folders = new JSONArray();
		JSONObject folder = new JSONObject();
		folder.put("path", ".");
		folders.put(folder);
		ws.put("folders", folders);

		JSONObject settings = new JSONObject();
		settings.put("workbench.colorTheme", "Monokai");
		settings.put("workbench.preferredDarkColorTheme", "Solarized Dark");
		settings.put("workbench.tips.enabled", false);
		settings.put("workbench.tree.indent", 24);
		settings.put("files.autoSave", "onFocusChange");

		JSONArray schemas = buildSchemas();
		settings.put("json.schemas", schemas);
		ws.put("settings", settings);

		return ws;
	}

	public void updateWorkspace(File ws)
	{
		try
		{
			J.attemptAsync(() -> writeDocs(ws.getParentFile()));
			JSONObject j = new JSONObject(IO.readAll(ws));
			JSONObject s = j.getJSONObject("settings");
			s.put("json.schemas", buildSchemas());
			j.put("settings", s);
			IO.writeAll(ws, j.toString(4));
			Iris.info("Updating Project " + ws.getAbsolutePath());
		}

		catch(Throwable e)
		{
			Iris.warn("Project invalid: " + ws.getAbsolutePath() + " Re-creating. You may loose some vs-code workspace settings! But not your actual project!");

			try
			{
				IO.writeAll(ws, newWorkspaceConfig());
			}

			catch(IOException e1)
			{
				e1.printStackTrace();
			}
		}
	}

	private JSONArray buildSchemas()
	{
		JSONArray schemas = new JSONArray();
		schemas.put(getSchemaEntry(IrisDimension.class, "/dimensions/*.json"));
		schemas.put(getSchemaEntry(IrisBiome.class, "/biomes/*.json"));
		schemas.put(getSchemaEntry(IrisRegion.class, "/regions/*.json"));
		schemas.put(getSchemaEntry(IrisGenerator.class, "/generators/*.json"));
		schemas.put(getSchemaEntry(IrisStructure.class, "/structures/*.json"));
		return schemas;
	}

	public JSONObject getSchemaEntry(Class<?> i, String... fileMatch)
	{
		JSONObject o = new JSONObject();
		o.put("fileMatch", new JSONArray(fileMatch));
		o.put("schema", getSchemaFor(i));

		return o;
	}

	public JSONObject getSchemaFor(Class<?> i)
	{
		KMap<String, JSONObject> def = new KMap<>();
		JSONObject s = getSchemaFor(i, 7, def);
		JSONObject defx = new JSONObject();
		for(String v : def.k())
		{
			defx.put(v, def.get(v));
		}

		s.put("definitions", defx);

		return s;
	}

	public JSONObject getSchemaFor(Class<?> i, int step, KMap<String, JSONObject> def)
	{
		if(step <= 0)
		{
			JSONObject m = new JSONObject();
			m.put("properties", new JSONObject());
			return m;
		}

		JSONObject schema = new JSONObject();
		if(i.isAnnotationPresent(Desc.class))
		{
			schema.put("$schema", "http://json-schema.org/draft-07/schema#");
			schema.put("$id", "http://volmit.com/iris-schema/" + i.getSimpleName().toLowerCase() + ".json");
			schema.put("title", i.getSimpleName().replaceAll("\\QIris\\E", ""));
			schema.put("type", "object");

			Desc d = i.getAnnotation(Desc.class);
			schema.put("description", d.value());

			JSONObject properties = new JSONObject();
			JSONArray req = new JSONArray();

			for(java.lang.reflect.Field k : i.getDeclaredFields())
			{
				JSONObject prop = new JSONObject();

				if(k.isAnnotationPresent(Desc.class))
				{
					String tp = "object";

					if(k.getType().equals(int.class) || k.getType().equals(long.class))
					{
						tp = "integer";

						if(k.isAnnotationPresent(MinNumber.class))
						{
							prop.put("minimum", (int) k.getDeclaredAnnotation(MinNumber.class).value());
						}

						if(k.isAnnotationPresent(MaxNumber.class))
						{
							prop.put("maximum", (int) k.getDeclaredAnnotation(MaxNumber.class).value());
						}
					}

					if(k.getType().equals(double.class) || k.getType().equals(float.class))
					{
						tp = "number";

						if(k.isAnnotationPresent(MinNumber.class))
						{
							prop.put("minimum", k.getDeclaredAnnotation(MinNumber.class).value());
						}

						if(k.isAnnotationPresent(MaxNumber.class))
						{
							prop.put("maximum", k.getDeclaredAnnotation(MaxNumber.class).value());
						}
					}

					if(k.getType().equals(boolean.class))
					{
						tp = "boolean";
					}

					if(k.getType().equals(String.class))
					{
						tp = "string";
					}

					if(k.getType().equals(String.class))
					{
						tp = "string";
					}

					if(k.getType().isEnum())
					{
						tp = "string";
						JSONArray a = new JSONArray();

						for(Object gg : k.getType().getEnumConstants())
						{
							a.put(((Enum<?>) gg).name());
						}

						prop.put("enum", a);
					}

					if(k.getType().equals(String.class) && k.getName().equals("potionEffect"))
					{
						tp = "string";
						JSONArray a = new JSONArray();

						for(PotionEffectType gg : PotionEffectType.values())
						{
							a.put(gg.getName().toUpperCase().replaceAll("\\Q \\E", "_"));
						}

						prop.put("enum", a);
					}

					if(k.getType().equals(KList.class))
					{
						tp = "array";
					}

					if(k.isAnnotationPresent(Required.class))
					{
						req.put(k.getName());
					}

					if(tp.equals("object"))
					{
						if(k.getType().isAnnotationPresent(Desc.class))
						{
							prop.put("properties", getSchemaFor(k.getType(), step - 1, def).getJSONObject("properties"));
						}
					}

					if(tp.equals("array"))
					{
						ArrayType t = k.getDeclaredAnnotation(ArrayType.class);

						if(t.min() > 0)
						{
							prop.put("minItems", t.min());
						}

						if(t != null)
						{
							String tx = "object";

							if(t.type().equals(int.class) || k.getType().equals(long.class))
							{
								tx = "integer";
							}

							if(t.type().equals(double.class) || k.getType().equals(float.class))
							{
								tx = "number";
							}

							if(t.type().equals(boolean.class))
							{
								tx = "boolean";
							}

							if(t.type().equals(String.class))
							{
								tx = "string";
							}

							if(t.type().isEnum())
							{
								tx = "string";
								JSONArray a = new JSONArray();

								for(Object gg : t.type().getEnumConstants())
								{
									a.put(((Enum<?>) gg).name());
								}

								String name = "enum" + t.type().getSimpleName().toLowerCase();

								if(!def.containsKey(name))
								{
									JSONObject deff = new JSONObject();
									deff.put("type", tx);
									deff.put("enum", a);
									def.put(name, deff);
								}

								JSONObject items = new JSONObject();
								items.put("$ref", "#/definitions/" + name);
								prop.put("items", items);
							}

							if(t.type().isEnum())
							{
								tx = "string";
							}

							if(t.type().equals(KList.class))
							{
								tx = "array";
							}

							JSONObject items = new JSONObject();

							if(tx.equals("object"))
							{
								if(t.type().isAnnotationPresent(Desc.class))
								{
									String name = t.type().getSimpleName().toLowerCase();

									if(!def.containsKey(name))
									{
										JSONObject deff = new JSONObject();
										JSONObject scv = getSchemaFor(t.type(), step - 1, def);
										deff.put("type", tx);
										deff.put("description", t.type().getDeclaredAnnotation(Desc.class).value());
										deff.put("properties", scv.getJSONObject("properties"));
										if(scv.has("required"))
										{
											deff.put("required", scv.getJSONArray("required"));
										}
										def.put(name, deff);
									}

									items.put("$ref", "#/definitions/" + name);
								}

								else
								{
									items.put("type", tx);
								}
							}

							else
							{
								items.put("type", tx);
							}

							prop.put("items", items);
						}

						if(tp.getClass().isAnnotationPresent(Desc.class))
						{
							prop.put("properties", getSchemaFor(tp.getClass(), step - 1, def).getJSONObject("properties"));
						}
					}

					prop.put("description", k.getAnnotation(Desc.class).value());
					prop.put("type", tp);
					properties.put(k.getName(), prop);
				}
			}

			schema.put("properties", properties);
			schema.put("required", req);
		}

		return schema;
	}

	public void writeDocs(File folder) throws IOException, JSONException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
	{
		File of = new File(folder, "_docs");
		KList<String> m = new KList<>();

		for(Biome i : Biome.values())
		{
			m.add(i.name());
		}

		IO.writeAll(new File(of, "biomes.txt"), m.toString("\n"));
		m = new KList<>();

		for(Particle i : Particle.values())
		{
			m.add(i.name());
		}

		IO.writeAll(new File(of, "particles.txt"), m.toString("\n"));
		m = new KList<>();

		for(Dispersion i : Dispersion.values())
		{
			m.add(i.name());
		}

		IO.writeAll(new File(of, "dispersion.txt"), m.toString("\n"));
		m = new KList<>();

		for(DecorationPart i : DecorationPart.values())
		{
			m.add(i.name());
		}

		IO.writeAll(new File(of, "decoration-part.txt"), m.toString("\n"));
		m = new KList<>();

		for(Envelope i : Envelope.values())
		{
			m.add(i.name());
		}

		IO.writeAll(new File(of, "envelope.txt"), m.toString("\n"));
		m = new KList<>();

		for(Environment i : Environment.values())
		{
			m.add(i.name());
		}

		IO.writeAll(new File(of, "environment.txt"), m.toString("\n"));
		m = new KList<>();

		for(StructureTileCondition i : StructureTileCondition.values())
		{
			m.add(i.name());
		}

		IO.writeAll(new File(of, "structure-tile-condition.txt"), m.toString("\n"));
		m = new KList<>();

		for(InterpolationMethod i : InterpolationMethod.values())
		{
			m.add(i.name());
		}

		IO.writeAll(new File(of, "interpolation-method.txt"), m.toString("\n"));
		m = new KList<>();

		for(Class<? extends IrisPostBlockFilter> i : Iris.postProcessors)
		{
			m.add(i.getDeclaredAnnotation(Post.class).value());
		}

		IO.writeAll(new File(of, "post-processors.txt"), m.toString("\n"));
		m = new KList<>();

		for(PotionEffectType i : PotionEffectType.values())
		{
			m.add(i.getName().toUpperCase().replaceAll("\\Q \\E", "_"));
		}
	}
}
