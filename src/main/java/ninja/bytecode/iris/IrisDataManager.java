package ninja.bytecode.iris;

import java.io.File;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import com.google.gson.Gson;

import lombok.Getter;
import ninja.bytecode.iris.object.Dispersion;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisBiomePaletteLayer;
import ninja.bytecode.iris.object.IrisDimension;
import ninja.bytecode.iris.util.IO;
import ninja.bytecode.iris.util.JSONObject;
import ninja.bytecode.iris.util.ResourceLoader;

public class IrisDataManager
{
	private File dataFolder;
	private File packs;

	@Getter
	private ResourceLoader<IrisBiome> biomeLoader;

	@Getter
	private ResourceLoader<IrisDimension> dimensionLoader;
	
	public void hotloaded()
	{
		packs.mkdirs();
		this.biomeLoader = new ResourceLoader<>(packs, "biomes", "Biome", IrisBiome.class);
		this.dimensionLoader = new ResourceLoader<>(packs, "dimensions", "Dimension", IrisDimension.class);
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

		IrisBiome biome = new IrisBiome();
		biome.getLayers().clear();
		IrisBiomePaletteLayer grass = new IrisBiomePaletteLayer();
		grass.add("GRASS_BLOCK");
		grass.setDispersion(Dispersion.SCATTER);
		grass.setMinHeight(1);
		grass.setMaxHeight(1);
		IrisBiomePaletteLayer dirt = new IrisBiomePaletteLayer();
		grass.add("DIRT");
		grass.setDispersion(Dispersion.SCATTER);
		grass.setMinHeight(1);
		grass.setMaxHeight(2);
		IrisBiomePaletteLayer dirtThick = new IrisBiomePaletteLayer();
		grass.add("DIRT");
		grass.add("COARSE_DIRT");
		grass.setDispersion(Dispersion.WISPY);
		grass.setMinHeight(1);
		grass.setMaxHeight(3);

		biome.getLayers().add(dirtThick);
		biome.getLayers().add(dirt);
		biome.getLayers().add(grass);

		IrisDimension dim = new IrisDimension();
		dim.getBiomes().add("a_biome");
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
			new File(examples, "example-pack/biomes").mkdirs();
			new File(examples, "example-pack/dimensions").mkdirs();
			IO.writeAll(new File(examples, "biome-list.txt"), biomes);
			IO.writeAll(new File(examples, "environment-list.txt"), envs);
			IO.writeAll(new File(examples, "example-pack/biomes/a_biome.json"), new JSONObject(new Gson().toJson(biome)).toString(4));
			IO.writeAll(new File(examples, "example-pack/dimensions/a_dimension.json"), new JSONObject(new Gson().toJson(dim)).toString(4));
		}

		catch(Throwable e)
		{

		}

	}
}