package com.volmit.iris.command;

import java.io.File;
import java.util.UUID;

import org.zeroturnaround.zip.ZipUtil;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.command.util.MortarCommand;
import com.volmit.iris.command.util.MortarSender;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisObjectPlacement;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.J;
import com.volmit.iris.util.JSONObject;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.KSet;

public class CommandIrisStudioPackage extends MortarCommand
{
	public CommandIrisStudioPackage()
	{
		super("package", "pkg");
		requiresPermission(Iris.perm.studio);
		setDescription("Package your dimension into a compressed format.");
		setCategory("Studio");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{

		J.a(() ->
		{
			String dim = "overworld";

			if(args.length > 1)
			{
				dim = args[1];
			}

			String dimm = dim;
			IrisDimension dimension = Iris.data.getDimensionLoader().load(dimm);
			File folder = new File(Iris.instance.getDataFolder(), "exports/" + dimension.getLoadKey());
			folder.mkdirs();
			Iris.info("Packaging Dimension " + dimension.getName());
			KSet<IrisRegion> regions = new KSet<>();
			KSet<IrisBiome> biomes = new KSet<>();
			KSet<IrisGenerator> generators = new KSet<>();
			dimension.getRegions().forEach((i) -> regions.add(Iris.data.getRegionLoader().load(i)));
			regions.forEach((i) -> biomes.addAll(i.getAllBiomes()));
			biomes.forEach((i) -> i.getGenerators().forEach((j) -> generators.add(j.getCachedGenerator())));
			KMap<String, String> renameObjects = new KMap<>();

			for(IrisBiome i : biomes)
			{
				for(IrisObjectPlacement j : i.getObjects())
				{
					KList<String> newNames = new KList<>();

					for(String k : j.getPlace())
					{
						if(renameObjects.containsKey(k))
						{
							newNames.add(renameObjects.get(k));
							continue;
						}

						String name = UUID.randomUUID().toString().replaceAll("-", "");
						newNames.add(name);
						renameObjects.put(k, name);
					}

					j.setPlace(newNames);
				}
			}

			KMap<String, KList<String>> lookupObjects = renameObjects.flip();

			biomes.forEach((i) -> i.getObjects().forEach((j) -> j.getPlace().forEach((k) ->
			{
				try
				{
					Iris.info("- " + k + " (Object)");
					IO.copyFile(Iris.data.getObjectLoader().findFile(lookupObjects.get(k).get(0)), new File(folder, "objects/" + k + ".iob"));
				}

				catch(Throwable e)
				{

				}
			})));

			try
			{
				IO.writeAll(new File(folder, "dimensions/" + dimension.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(dimension)).toString(0));

				for(IrisGenerator i : generators)
				{
					Iris.info("- " + i.getLoadKey() + " (Generator)");
					IO.writeAll(new File(folder, "generators/" + i.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(i)).toString(0));
				}

				for(IrisRegion i : regions)
				{
					Iris.info("- " + i.getName() + " (Region)");
					IO.writeAll(new File(folder, "regions/" + i.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(i)).toString(0));
				}

				for(IrisBiome i : biomes)
				{
					Iris.info("- " + i.getName() + " (Biome)");
					IO.writeAll(new File(folder, "biomes/" + i.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(i)).toString(0));
				}

				ZipUtil.pack(folder, new File(Iris.instance.getDataFolder(), "exports/" + dimension.getLoadKey() + ".iris"), 9);
				IO.delete(folder);
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}

			sender.sendMessage("Done!");
		});

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[dimension] [-o]";
	}
}
