package ninja.bytecode.iris;

import java.io.File;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import com.google.gson.Gson;

import lombok.Data;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisBiomeDecorator;
import ninja.bytecode.iris.object.IrisDimension;
import ninja.bytecode.iris.object.IrisNoiseLayer;
import ninja.bytecode.iris.object.IrisObjectPlacement;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.util.IO;
import ninja.bytecode.iris.util.ObjectResourceLoader;
import ninja.bytecode.iris.util.ResourceLoader;
import ninja.bytecode.shuriken.json.JSONObject;

@Data
public class IrisDataManager
{
	private File dataFolder;
	private File packs;
	private ResourceLoader<IrisBiome> biomeLoader;
	private ResourceLoader<IrisRegion> regionLoader;
	private ResourceLoader<IrisDimension> dimensionLoader;
	private ObjectResourceLoader objectLoader;

	public void hotloaded()
	{
		packs.mkdirs();
		this.regionLoader = new ResourceLoader<>(packs, "regions", "Region", IrisRegion.class);
		this.biomeLoader = new ResourceLoader<>(packs, "biomes", "Biome", IrisBiome.class);
		this.dimensionLoader = new ResourceLoader<>(packs, "dimensions", "Dimension", IrisDimension.class);
		this.objectLoader = new ObjectResourceLoader(packs, "objects", "Object");
		writeExamples();
	}

	public IrisDataManager(File dataFolder)
	{
		this.dataFolder = dataFolder;
		this.packs = new File(dataFolder, "packs");
		hotloaded();
	}

	private void writeExamples()
	{
		File examples = new File(dataFolder, "example");
		examples.mkdirs();
		String biomes = "";
		String envs = "";

		for(Biome i : Biome.values())
		{
			biomes += i.name() + "\n";
		}

		for(Environment i : Environment.values())
		{
			envs += i.name() + "\n";
		}

		try
		{
			new File(examples, "example-pack/regions").mkdirs();
			new File(examples, "example-pack/biomes").mkdirs();
			new File(examples, "example-pack/dimensions").mkdirs();
			IO.writeAll(new File(examples, "biome-list.txt"), biomes);
			IO.writeAll(new File(examples, "environment-list.txt"), envs);

			IrisDimension dim = new IrisDimension();

			IrisRegion region = new IrisRegion();
			region.getLandBiomes().add("plains");
			region.getLandBiomes().add("desert");
			region.getLandBiomes().add("forest");
			region.getLandBiomes().add("mountains");
			region.getSeaBiomes().add("ocean");
			region.getShoreBiomes().add("beach");

			IrisObjectPlacement o = new IrisObjectPlacement();
			o.getPlace().add("schematic1");
			o.getPlace().add("schematic2");

			IrisBiome biome = new IrisBiome();
			biome.getAuxiliaryGenerators().add(new IrisNoiseLayer());
			biome.getChildren().add("another_biome");
			biome.getDecorators().add(new IrisBiomeDecorator());
			biome.getObjects().add(o);

			IO.writeAll(new File(examples, "example-pack/biomes/example-biome.json"), new JSONObject(new Gson().toJson(biome)).toString(4));
			IO.writeAll(new File(examples, "example-pack/regions/example-region.json"), new JSONObject(new Gson().toJson(region)).toString(4));
			IO.writeAll(new File(examples, "example-pack/dimensions/example-dimension.json"), new JSONObject(new Gson().toJson(dim)).toString(4));
		}

		catch(Throwable e)
		{

		}
	}
}