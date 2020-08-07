package com.volmit.iris;

import java.awt.Desktop;
import java.io.File;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.zeroturnaround.zip.ZipUtil;

import com.google.gson.Gson;
import com.volmit.iris.gen.IrisChunkGenerator;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeMutation;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisObjectPlacement;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisStructure;
import com.volmit.iris.object.IrisStructureTile;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.J;
import com.volmit.iris.util.JSONObject;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.M;
import com.volmit.iris.util.MortarSender;
import com.volmit.iris.util.O;

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
}
