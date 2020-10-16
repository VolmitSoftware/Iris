package com.volmit.iris.manager;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.zeroturnaround.zip.ZipUtil;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.nms.NMSCreator;
import com.volmit.iris.gen.provisions.ProvisionBukkit;
import com.volmit.iris.gen.scaffold.IrisGenConfiguration;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeMutation;
import com.volmit.iris.object.IrisBlockData;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisEntity;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisLootTable;
import com.volmit.iris.object.IrisObjectPlacement;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisStructure;
import com.volmit.iris.object.IrisStructureTile;
import com.volmit.iris.util.C;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.J;
import com.volmit.iris.util.JSONArray;
import com.volmit.iris.util.JSONObject;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.M;
import com.volmit.iris.util.MortarSender;
import com.volmit.iris.util.O;
import com.volmit.iris.util.PrecisionStopwatch;

import lombok.Data;

@Data
public class IrisProject
{
	private File path;
	private String name;
	private IrisTerrainProvider activeProvider;

	public IrisProject(File path)
	{
		this.path = path;
		this.name = path.getName();
	}

	private static void flush()
	{
		Iris.globaldata.dump();
		Iris.globaldata.preferFolder(null);
	}

	public boolean isOpen()
	{
		return activeProvider != null;
	}

	public void open(MortarSender sender)
	{
		open(sender, () ->
		{
		});
	}

	public void open(MortarSender sender, Runnable onDone)
	{
		if(isOpen())
		{
			close();
		}

		flush();
		IrisDimension d = Iris.globaldata.getDimensionLoader().load(getName());
		J.attemptAsync(() ->
		{
			try
			{
				File f = d.getLoadFile().getParentFile().getParentFile();

				for(File i : f.listFiles())
				{
					if(i.getName().endsWith(".code-workspace"))
					{
						sender.sendMessage("Updating Workspace...");
						J.a(() ->
						{
							updateWorkspace();
							sender.sendMessage("Workspace Updated");
						});

						if(IrisSettings.get().openVSCode)
						{
							Desktop.getDesktop().open(i);
						}

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
			sender.sendMessage("Can't find dimension: " + getName());
			return;
		}

		Iris.globaldata.dump();
		String wfp = "iris/" + UUID.randomUUID();
		//@builder
		ProvisionBukkit gen = Iris.instance
				.createProvisionBukkit(IrisGenConfiguration.builder()
						.threads(Iris.getThreadCount())
						.dimension(getName())
						.target(TerrainTarget.builder()
								.environment(d.getEnvironment())
								.folder(new File(wfp))
								.name(wfp)
								.seed(1337)
								.build())
						.build());
		//@done

		IrisTerrainProvider gx = (IrisTerrainProvider) gen.getProvider();
		gx.setDev(true);
		sender.sendMessage("Generating with " + Iris.getThreadCount() + " threads per chunk");
		O<Boolean> done = new O<Boolean>();
		done.set(false);
		activeProvider = gx;

		J.a(() ->
		{
			double last = 0;
			int req = 740;
			double lpc = 0;
			boolean c = false;

			while(!done.get())
			{
				boolean derp = false;

				double v = (double) gx.getGenerated() / (double) req;
				c = lpc != v;
				lpc = v;

				if(last > v || v > 1)
				{
					derp = true;
					v = last;
				}

				else
				{
					last = v;
				}

				if(c)
				{
					sender.sendMessage(C.WHITE + "Generating " + Form.pc(v) + (derp ? (C.GRAY + " (Waiting on Server...)") : (C.GRAY + " (" + (req - gx.getGenerated()) + " Left)")));
				}

				J.sleep(3000);

				if(gx.isFailing())
				{
					sender.sendMessage("Generation Failed!");
					return;
				}
			}
		});

		//@builder
		World world = NMSCreator.createWorld(new WorldCreator(wfp)
				.seed(1337)
				.generator(gen)
				.generateStructures(d.isVanillaStructures())
				.type(WorldType.NORMAL)
				.environment(d.getEnvironment()), false);
		//@done
		gx.getTarget().setRealWorld(world);
		Iris.linkMultiverseCore.removeFromConfig(world);

		done.set(true);
		sender.sendMessage("Generating 100%");

		if(sender.isPlayer())
		{
			sender.player().teleport(world.getSpawnLocation());
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
		activeProvider.close();
		File folder = activeProvider.getTarget().getFolder();
		Iris.linkMultiverseCore.removeFromConfig(activeProvider.getTarget().getName());
		Bukkit.unloadWorld(activeProvider.getTarget().getName(), false);
		flush();
		J.attemptAsync(() -> IO.delete(folder));
		activeProvider = null;
	}

	public File getCodeWorkspaceFile()
	{
		return new File(path, getName() + ".code-workspace");
	}

	public void updateWorkspace()
	{
		getPath().mkdirs();
		File ws = getCodeWorkspaceFile();

		try
		{
			PrecisionStopwatch p = PrecisionStopwatch.start();
			Iris.info("Updating Workspace: " + ws.getPath());
			JSONObject j = createCodeWorkspaceConfig();
			IO.writeAll(ws, j.toString(4));
			p.end();
			Iris.info("Updated Workspace: " + ws.getPath() + " in " + Form.duration(p.getMilliseconds(), 2));
		}

		catch(Throwable e)
		{
			Iris.warn("Project invalid: " + ws.getAbsolutePath() + " Re-creating. You may loose some vs-code workspace settings! But not your actual project!");
			ws.delete();
			try
			{
				IO.writeAll(ws, createCodeWorkspaceConfig());
			}

			catch(IOException e1)
			{
				e1.printStackTrace();
			}
		}
	}

	public JSONObject createCodeWorkspaceConfig()
	{
		Iris.globaldata.clearLists();
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
		JSONObject jc = new JSONObject();
		jc.put("editor.autoIndent", "brackets");
		jc.put("editor.acceptSuggestionOnEnter", "smart");
		jc.put("editor.cursorSmoothCaretAnimation", true);
		jc.put("editor.dragAndDrop", false);
		jc.put("files.trimTrailingWhitespace", true);
		jc.put("diffEditor.ignoreTrimWhitespace", true);
		jc.put("files.trimFinalNewlines", true);
		jc.put("editor.suggest.showKeywords", false);
		jc.put("editor.suggest.showSnippets", false);
		jc.put("editor.suggest.showWords", false);
		JSONObject st = new JSONObject();
		st.put("strings", true);
		jc.put("editor.quickSuggestions", st);
		jc.put("editor.suggest.insertMode", "replace");
		settings.put("[json]", jc);
		settings.put("json.maxItemsComputed", 15000);
		String gg = Iris.globaldata.getBiomeLoader().getPreferredFolder();
		Iris.globaldata.preferFolder(getName());
		JSONArray schemas = new JSONArray();
		schemas.put(getSchemaEntry(IrisDimension.class, Iris.globaldata, "/dimensions/*.json"));
		schemas.put(getSchemaEntry(IrisEntity.class, Iris.globaldata, "/entities/*.json"));
		schemas.put(getSchemaEntry(IrisBiome.class, Iris.globaldata, "/biomes/*.json"));
		schemas.put(getSchemaEntry(IrisRegion.class, Iris.globaldata, "/regions/*.json"));
		schemas.put(getSchemaEntry(IrisGenerator.class, Iris.globaldata, "/generators/*.json"));
		schemas.put(getSchemaEntry(IrisStructure.class, Iris.globaldata, "/structures/*.json"));
		schemas.put(getSchemaEntry(IrisBlockData.class, Iris.globaldata, "/blocks/*.json"));
		schemas.put(getSchemaEntry(IrisLootTable.class, Iris.globaldata, "/loot/*.json"));
		Iris.globaldata.preferFolder(gg);
		settings.put("json.schemas", schemas);
		ws.put("settings", settings);

		return ws;
	}

	public JSONObject getSchemaEntry(Class<?> i, IrisDataManager dat, String... fileMatch)
	{
		Iris.verbose("Processing Folder " + i.getSimpleName() + " " + fileMatch[0]);
		JSONObject o = new JSONObject();
		o.put("fileMatch", new JSONArray(fileMatch));
		o.put("schema", new SchemaBuilder(i, dat).compute());

		return o;
	}

	public File compilePackage(MortarSender sender, boolean obfuscate, boolean minify)
	{
		String dim = getName();
		Iris.globaldata.dump();
		Iris.globaldata.preferFolder(null);
		String dimm = dim;
		IrisDimension dimension = Iris.globaldata.getDimensionLoader().load(dimm);
		File folder = new File(Iris.instance.getDataFolder(), "exports/" + dimension.getLoadKey());
		folder.mkdirs();
		Iris.info("Packaging Dimension " + dimension.getName() + " " + (obfuscate ? "(Obfuscated)" : ""));
		KSet<IrisRegion> regions = new KSet<>();
		KSet<IrisBiome> biomes = new KSet<>();
		KSet<IrisEntity> entities = new KSet<>();
		KSet<IrisStructure> structures = new KSet<>();
		KSet<IrisGenerator> generators = new KSet<>();
		KSet<IrisLootTable> loot = new KSet<>();
		KSet<IrisBlockData> blocks = new KSet<>();
		Iris.globaldata.preferFolder(dim);

		for(String i : Iris.globaldata.getBlockLoader().getPreferredKeys())
		{
			blocks.add(Iris.globaldata.getBlockLoader().load(i));
		}

		Iris.globaldata.preferFolder(null);
		dimension.getRegions().forEach((i) -> regions.add(Iris.globaldata.getRegionLoader().load(i)));
		dimension.getLoot().getTables().forEach((i) -> loot.add(Iris.globaldata.getLootLoader().load(i)));
		regions.forEach((i) -> biomes.addAll(i.getAllBiomes(null)));
		biomes.forEach((i) -> i.getGenerators().forEach((j) -> generators.add(j.getCachedGenerator(null))));
		regions.forEach((i) -> i.getStructures().forEach((j) -> structures.add(j.getStructure(null))));
		biomes.forEach((i) -> i.getStructures().forEach((j) -> structures.add(j.getStructure(null))));
		regions.forEach((r) -> r.getLoot().getTables().forEach((i) -> loot.add(Iris.globaldata.getLootLoader().load(i))));
		biomes.forEach((r) -> r.getLoot().getTables().forEach((i) -> loot.add(Iris.globaldata.getLootLoader().load(i))));
		structures.forEach((r) -> r.getLoot().getTables().forEach((i) -> loot.add(Iris.globaldata.getLootLoader().load(i))));
		structures.forEach((b) -> b.getTiles().forEach((r) -> r.getLoot().getTables().forEach((i) -> loot.add(Iris.globaldata.getLootLoader().load(i)))));
		structures.forEach((r) -> r.getEntitySpawnOverrides().forEach((sp) -> entities.add(Iris.globaldata.getEntityLoader().load(sp.getEntity()))));
		structures.forEach((s) -> s.getTiles().forEach((r) -> r.getEntitySpawnOverrides().forEach((sp) -> entities.add(Iris.globaldata.getEntityLoader().load(sp.getEntity())))));
		biomes.forEach((r) -> r.getEntitySpawnOverrides().forEach((sp) -> entities.add(Iris.globaldata.getEntityLoader().load(sp.getEntity()))));
		regions.forEach((r) -> r.getEntitySpawnOverrides().forEach((sp) -> entities.add(Iris.globaldata.getEntityLoader().load(sp.getEntity()))));
		dimension.getEntitySpawnOverrides().forEach((sp) -> entities.add(Iris.globaldata.getEntityLoader().load(sp.getEntity())));
		structures.forEach((r) -> r.getEntityInitialSpawns().forEach((sp) -> entities.add(Iris.globaldata.getEntityLoader().load(sp.getEntity()))));
		structures.forEach((s) -> s.getTiles().forEach((r) -> r.getEntityInitialSpawns().forEach((sp) -> entities.add(Iris.globaldata.getEntityLoader().load(sp.getEntity())))));
		biomes.forEach((r) -> r.getEntityInitialSpawns().forEach((sp) -> entities.add(Iris.globaldata.getEntityLoader().load(sp.getEntity()))));
		regions.forEach((r) -> r.getEntityInitialSpawns().forEach((sp) -> entities.add(Iris.globaldata.getEntityLoader().load(sp.getEntity()))));
		dimension.getEntityInitialSpawns().forEach((sp) -> entities.add(Iris.globaldata.getEntityLoader().load(sp.getEntity())));
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

					String name = !obfuscate ? k : UUID.randomUUID().toString().replaceAll("-", "");
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

					String name = !obfuscate ? k : UUID.randomUUID().toString().replaceAll("-", "");
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

					String name = !obfuscate ? k : UUID.randomUUID().toString().replaceAll("-", "");
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
			a = new JSONObject(new Gson().toJson(dimension)).toString(minify ? 0 : 4);
			IO.writeAll(new File(folder, "dimensions/" + dimension.getLoadKey() + ".json"), a);
			b.append(IO.hash(a));

			for(IrisGenerator i : generators)
			{
				a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
				IO.writeAll(new File(folder, "generators/" + i.getLoadKey() + ".json"), a);
				b.append(IO.hash(a));
			}

			c.append(IO.hash(b.toString()));
			b = new StringBuilder();

			for(IrisRegion i : regions)
			{
				a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
				IO.writeAll(new File(folder, "regions/" + i.getLoadKey() + ".json"), a);
				b.append(IO.hash(a));
			}

			for(IrisBlockData i : blocks)
			{
				a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
				IO.writeAll(new File(folder, "blocks/" + i.getLoadKey() + ".json"), a);
				b.append(IO.hash(a));
			}

			for(IrisStructure i : structures)
			{
				a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
				IO.writeAll(new File(folder, "structures/" + i.getLoadKey() + ".json"), a);
				b.append(IO.hash(a));
			}

			for(IrisBiome i : biomes)
			{
				a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
				IO.writeAll(new File(folder, "biomes/" + i.getLoadKey() + ".json"), a);
				b.append(IO.hash(a));
			}

			for(IrisEntity i : entities)
			{
				a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
				IO.writeAll(new File(folder, "entities/" + i.getLoadKey() + ".json"), a);
				b.append(IO.hash(a));
			}

			for(IrisLootTable i : loot)
			{
				a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
				IO.writeAll(new File(folder, "loot/" + i.getLoadKey() + ".json"), a);
				b.append(IO.hash(a));
			}

			c.append(IO.hash(b.toString()));
			b = new StringBuilder();
			String finalHash = IO.hash(c.toString());
			JSONObject meta = new JSONObject();
			meta.put("hash", finalHash);
			meta.put("time", M.ms());
			meta.put("version", dimension.getVersion());
			IO.writeAll(new File(folder, "package.json"), meta.toString(minify ? 0 : 4));
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
}
