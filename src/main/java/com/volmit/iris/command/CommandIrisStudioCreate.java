package com.volmit.iris.command;

import java.io.IOException;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.object.InterpolationMethod;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeGeneratorLink;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisNoiseGenerator;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.JSONException;
import com.volmit.iris.util.JSONObject;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

import net.md_5.bungee.api.ChatColor;

public class CommandIrisStudioCreate extends MortarCommand
{
	public CommandIrisStudioCreate()
	{
		super("create", "new");
		requiresPermission(Iris.perm.studio);
		setDescription("Create a new project & open it.");
		setCategory("Studio");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args.length != 1)
		{
			sender.sendMessage("Please use a lowercase name with hyphens (-) for spaces.");
			sender.sendMessage("I.e. /iris std new " + ChatColor.BOLD + "aether");
			return true;
		}

		IrisDimension dimension = new IrisDimension();
		dimension.setLoadKey(args[0]);
		dimension.setName(Form.capitalizeWords(args[0].replaceAll("\\Q-\\E", " ")));

		if(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "dimensions", dimension.getLoadKey() + ".json").exists())
		{
			sender.sendMessage("Project Already Exists! Open it instead!");
			return true;
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
		b1.setGenerator(gen.getLoadKey());
		b1.setMin(12);
		b1.setMax(35);
		IrisBiomeGeneratorLink b3 = new IrisBiomeGeneratorLink();
		b1.setGenerator(gen.getLoadKey());
		b1.setMin(-1);
		b1.setMax(1);
		IrisBiomeGeneratorLink b4 = new IrisBiomeGeneratorLink();
		b1.setGenerator(gen.getLoadKey());
		b1.setMin(-5);
		b1.setMax(-38);
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
			String g = "{\"folders\": [{\"path\": \".\"}],\"settings\": {\"workbench.colorTheme\": \"Monokai\",\"workbench.preferredHighContrastColorTheme\": \"Solarized Dark\",\"workbench.preferredDarkColorTheme\": \"Solarized Dark\",\"workbench.statusBar.visible\": false,\"workbench.tips.enabled\": false,\"workbench.tree.indent\": 24,\"files.autoSave\": \"onFocusChange\"}}";
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "dimensions", dimension.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(dimension)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "regions", exampleRegion.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleRegion)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "biomes", exampleLand1.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleLand1)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "biomes", exampleLand2.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleLand2)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "biomes", exampleShore1.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleShore1)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "biomes", exampleOcean1.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleOcean1)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), "generators", gen.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(gen)).toString(4));
			IO.writeAll(Iris.instance.getDataFile("packs", dimension.getLoadKey(), dimension.getLoadKey() + ".code-workspace"), new JSONObject(g).toString(4));
			Iris.proj.open(sender, dimension.getName());
		}

		catch(JSONException | IOException e)
		{
			sender.sendMessage("Failed! Check the console.");
			e.printStackTrace();
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[dimension]";
	}
}
