package com.volmit.iris.manager;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.object.*;
import com.volmit.iris.scaffold.IrisWorldCreator;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.*;
import lombok.Data;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.zeroturnaround.zip.ZipUtil;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Data
public class IrisProject
{
	private File path;
	private String name;
	private IrisAccess activeProvider;

	public IrisProject(File path)
	{
		this.path = path;
		this.name = path.getName();
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

		IrisDimension d = IrisDataManager.loadAnyDimension(getName());
		if(d == null)
		{
			sender.sendMessage("Can't find dimension: " + getName());
			return;
		} else if(sender.isPlayer()){
			sender.player().setGameMode(GameMode.SPECTATOR);
			sender.player().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 500, 10));
			sender.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(C.BLUE + "Creating studio world. Please wait..."));
		}

		J.attemptAsync(() ->
		{
			try
			{
				if (d.getLoader() == null){
					sender.sendMessage("Could not get dimension loader");
					return;
				}
				File f = d.getLoader().getDataFolder();
				boolean foundWork = false;
				for(File i : Objects.requireNonNull(f.listFiles()))
				{
					if(i.getName().endsWith(".code-workspace"))
					{
						foundWork = true;
						sender.sendMessage("Updating Workspace...");
						J.a(() ->
						{
							updateWorkspace();
							sender.sendMessage("Workspace Updated");
						});

						if(IrisSettings.get().getStudio().isOpenVSCode())
						{
							if (!GraphicsEnvironment.isHeadless()) {
								Iris.msg("Opening VSCode. You may see the output from VSCode.");
								Iris.msg("VSCode output always starts with: '(node:#####) electron'");
								Desktop.getDesktop().open(i);
							}
						}

						break;
					}
				}

				if(!foundWork)
				{
					File ff = new File(d.getLoader().getDataFolder(), d.getLoadKey() + ".code-workspace");
					Iris.warn("Project missing code-workspace: " + ff.getAbsolutePath() + " Re-creating code workspace.");

					try
					{
						IO.writeAll(ff, createCodeWorkspaceConfig());
					}

					catch(IOException e1)
					{
						e1.printStackTrace();
					}
					sender.sendMessage("Updating Workspace...");
					updateWorkspace();
					sender.sendMessage("Workspace Updated");
				}
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		});

		String wfp = "iris/" + UUID.randomUUID();

		WorldCreator c = new IrisWorldCreator().dimension(getName())
				.seed(1337)
				.name(wfp)
				.studioMode()
				.asyncPrepare()
				.create();
					IrisAccess gx = ((IrisAccess)c.generator());
					sender.sendMessage("Generating with " + Iris.getThreadCount() + " threads per chunk");
					O<Boolean> done = new O<>();
					done.set(false);
					activeProvider = gx;

					J.a(() ->
					{
						double last = 0;
						int req = 300;
						double lpc = 0;
						boolean fc;

						while(!done.get())
						{
							boolean derp = false;

							assert gx != null;
							double v = (double) gx.getGenerated() / (double) req;
							fc = lpc != v;
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

							if(fc)
							{
								sender.sendMessage(C.WHITE + "Generating " + Form.pc(v) + (derp ? (C.GRAY + " (Waiting on Server...)") : (C.GRAY + " (" + (req - gx.getGenerated()) + " Left)")));
							}

							if (sender.isPlayer()){
								sender.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(C.BLUE + "Creating studio world. Please wait..."));
							}

							J.sleep(1500);

							if(gx.isFailing())
							{

								sender.sendMessage("Generation Failed!");
								return;
							}
						}
					});

					//@builder
					World world = c.createWorld();
					Iris.linkMultiverseCore.removeFromConfig(world);

					done.set(true);
					sender.sendMessage(C.WHITE + "Generating Complete!");

					if(sender.isPlayer())
					{
						assert world != null;
						sender.player().teleport(world.getSpawnLocation());
					}

					Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
					{
						sender.sendMessage("Hotloading Active! Change any files and watch your changes appear as you load new chunks!");

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
		File folder = activeProvider.getTarget().getWorld().getWorldFolder();
		Iris.linkMultiverseCore.removeFromConfig(activeProvider.getTarget().getWorld().getName());
		Bukkit.unloadWorld(activeProvider.getTarget().getWorld().getName(), false);
		J.attemptAsync(() -> IO.delete(folder));
		activeProvider = null;
	}

	public File getCodeWorkspaceFile()
	{
		return new File(path, getName() + ".code-workspace");
	}

	public boolean updateWorkspace()
	{
		getPath().mkdirs();
		File ws = getCodeWorkspaceFile();

		try
		{
			PrecisionStopwatch p = PrecisionStopwatch.start();
			Iris.info("Building Workspace: " + ws.getPath());
			JSONObject j = createCodeWorkspaceConfig();
			IO.writeAll(ws, j.toString(4));
			p.end();
			Iris.info("Building Workspace: " + ws.getPath() + " took " + Form.duration(p.getMilliseconds(), 2));
			return true;
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
		
		return false;
	}

	public JSONObject createCodeWorkspaceConfig()
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
		settings.put("json.maxItemsComputed", 30000);
		JSONArray schemas = new JSONArray();
		IrisDataManager dm = new IrisDataManager(getPath());
		schemas.put(getSchemaEntry(IrisDimension.class, dm, "/dimensions/*.json"));
		schemas.put(getSchemaEntry(IrisEntity.class, dm, "/entities/*.json"));
		schemas.put(getSchemaEntry(IrisBiome.class, dm, "/biomes/*.json"));
		schemas.put(getSchemaEntry(IrisRegion.class, dm, "/regions/*.json"));
		schemas.put(getSchemaEntry(IrisGenerator.class,dm, "/generators/*.json"));
		schemas.put(getSchemaEntry(IrisJigsawPiece.class, dm, "/jigsaw-pieces/*.json"));
		schemas.put(getSchemaEntry(IrisJigsawPool.class, dm, "/jigsaw-pools/*.json"));
		schemas.put(getSchemaEntry(IrisJigsawStructure.class, dm, "/jigsaw-structures/*.json"));
		schemas.put(getSchemaEntry(IrisBlockData.class, dm, "/blocks/*.json"));
		schemas.put(getSchemaEntry(IrisLootTable.class, dm, "/loot/*.json"));
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
		String dimm = getName();
		IrisDataManager dm = new IrisDataManager(path);
		IrisDimension dimension = dm.getDimensionLoader().load(dimm);
		File folder = new File(Iris.instance.getDataFolder(), "exports/" + dimension.getLoadKey());
		folder.mkdirs();
		Iris.info("Packaging Dimension " + dimension.getName() + " " + (obfuscate ? "(Obfuscated)" : ""));
		KSet<IrisRegion> regions = new KSet<>();
		KSet<IrisBiome> biomes = new KSet<>();
		KSet<IrisEntity> entities = new KSet<>();
		KSet<IrisGenerator> generators = new KSet<>();
		KSet<IrisLootTable> loot = new KSet<>();
		KSet<IrisBlockData> blocks = new KSet<>();

		for(String i : dm.getDimensionLoader().getPossibleKeys())
		{
			blocks.add(dm.getBlockLoader().load(i));
		}

		//TODO: EXPORT JIGSAW PIECES FROM STRUCTURES
		dimension.getRegions().forEach((i) -> regions.add(dm.getRegionLoader().load(i)));
		dimension.getLoot().getTables().forEach((i) -> loot.add(dm.getLootLoader().load(i)));
		regions.forEach((i) -> biomes.addAll(i.getAllBiomes(null)));
		biomes.forEach((i) -> i.getGenerators().forEach((j) -> generators.add(j.getCachedGenerator(null))));
		regions.forEach((r) -> r.getLoot().getTables().forEach((i) -> loot.add(dm.getLootLoader().load(i))));
		biomes.forEach((r) -> r.getLoot().getTables().forEach((i) -> loot.add(dm.getLootLoader().load(i))));
		biomes.forEach((r) -> r.getEntitySpawnOverrides().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity()))));
		regions.forEach((r) -> r.getEntitySpawnOverrides().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity()))));
		dimension.getEntitySpawnOverrides().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity())));
		biomes.forEach((r) -> r.getEntityInitialSpawns().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity()))));
		regions.forEach((r) -> r.getEntityInitialSpawns().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity()))));
		dimension.getEntityInitialSpawns().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity())));
		KMap<String, String> renameObjects = new KMap<>();
		String a;
		StringBuilder b = new StringBuilder();
		StringBuilder c = new StringBuilder();
		sender.sendMessage("Serializing Objects");

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
		O<Integer> ggg = new O<>();
		ggg.set(0);
		biomes.forEach((i) -> i.getObjects().forEach((j) -> j.getPlace().forEach((k) ->
		{
			try
			{
				File f = dm.getObjectLoader().findFile(lookupObjects.get(k).get(0));
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

			catch(Throwable ignored)
			{

			}
		})));

		dimension.getMutations().forEach((i) -> i.getObjects().forEach((j) -> j.getPlace().forEach((k) ->
		{
			try
			{
				File f = dm.getObjectLoader().findFile(lookupObjects.get(k).get(0));
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

			catch(Throwable ignored)
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
