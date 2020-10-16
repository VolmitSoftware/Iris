package com.volmit.iris.manager;

import java.io.File;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import com.google.gson.Gson;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeDecorator;
import com.volmit.iris.object.IrisBlockData;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisEntity;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisLootTable;
import com.volmit.iris.object.IrisNoiseGenerator;
import com.volmit.iris.object.IrisObjectPlacement;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisStructure;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.JSONObject;
import com.volmit.iris.util.ObjectResourceLoader;
import com.volmit.iris.util.ResourceLoader;

import lombok.Data;

@Data
public class IrisDataManager
{
	private File dataFolder;
	private File packs;
	private boolean prod;
	private ResourceLoader<IrisBiome> biomeLoader;
	private ResourceLoader<IrisLootTable> lootLoader;
	private ResourceLoader<IrisRegion> regionLoader;
	private ResourceLoader<IrisDimension> dimensionLoader;
	private ResourceLoader<IrisGenerator> generatorLoader;
	private ResourceLoader<IrisStructure> structureLoader;
	private ResourceLoader<IrisEntity> entityLoader;
	private ResourceLoader<IrisBlockData> blockLoader;
	private ObjectResourceLoader objectLoader;

	public IrisDataManager(File dataFolder)
	{
		this.dataFolder = dataFolder;
		this.packs = new File(dataFolder, ProjectManager.workspaceName);
		boolean pr = false;
		if(!packs.exists())
		{
			if(new File(dataFolder, "iris").exists())
			{
				pr = true;
				packs = new File(dataFolder, "iris");
			}
		}

		hotloaded();
		prod = pr;
	}

	public void hotloaded()
	{
		if(prod)
		{
			return;
		}

		File packs = this.packs.getName().equals(ProjectManager.workspaceName) ? this.packs : dataFolder;
		packs.mkdirs();
		this.lootLoader = new ResourceLoader<>(packs, "loot", "Loot", IrisLootTable.class);
		this.entityLoader = new ResourceLoader<>(packs, "entities", "Entity", IrisEntity.class);
		this.regionLoader = new ResourceLoader<>(packs, "regions", "Region", IrisRegion.class);
		this.biomeLoader = new ResourceLoader<>(packs, "biomes", "Biome", IrisBiome.class);
		this.dimensionLoader = new ResourceLoader<>(packs, "dimensions", "Dimension", IrisDimension.class);
		this.structureLoader = new ResourceLoader<>(packs, "structures", "Structure", IrisStructure.class);
		this.generatorLoader = new ResourceLoader<>(packs, "generators", "Generator", IrisGenerator.class);
		this.blockLoader = new ResourceLoader<>(packs, "blocks", "Block", IrisBlockData.class);
		this.objectLoader = new ObjectResourceLoader(packs, "objects", "Object");

		if(packs.getName().equals(ProjectManager.workspaceName))
		{
			writeExamples();
		}
	}

	public void dump()
	{
		biomeLoader.clearCache();
		blockLoader.clearCache();
		lootLoader.clearCache();
		regionLoader.clearCache();
		dimensionLoader.clearCache();
		entityLoader.clearCache();
		generatorLoader.clearCache();
		structureLoader.clearCache();
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
			new File(examples, "example-pack/generators").mkdirs();
			IO.writeAll(new File(examples, "biome-list.txt"), biomes);
			IO.writeAll(new File(examples, "environment-list.txt"), envs);

			IrisGenerator gen = new IrisGenerator();
			IrisNoiseGenerator n = new IrisNoiseGenerator();
			n.setSeed(1000);
			IrisNoiseGenerator nf = new IrisNoiseGenerator();
			nf.setOctaves(3);
			nf.setOpacity(16);
			nf.setZoom(24);
			nf.setSeed(44);
			n.getFracture().add(nf);
			IrisNoiseGenerator nf2 = new IrisNoiseGenerator();
			nf2.setOctaves(8);
			nf2.setOpacity(24);
			nf2.setZoom(64);
			nf2.setSeed(55);
			n.getFracture().add(nf2);
			gen.getComposite().add(n);

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
			biome.getChildren().add("another_biome");
			biome.getDecorators().add(new IrisBiomeDecorator());
			biome.getObjects().add(o);

			IO.writeAll(new File(examples, "example-pack/biomes/example-biome.json"), new JSONObject(new Gson().toJson(biome)).toString(4));
			IO.writeAll(new File(examples, "example-pack/regions/example-region.json"), new JSONObject(new Gson().toJson(region)).toString(4));
			IO.writeAll(new File(examples, "example-pack/dimensions/example-dimension.json"), new JSONObject(new Gson().toJson(dim)).toString(4));
			IO.writeAll(new File(examples, "example-pack/generators/example-generator.json"), new JSONObject(new Gson().toJson(gen)).toString(4));
		}

		catch(Throwable e)
		{

		}
	}

	public void preferFolder(String name)
	{
		biomeLoader.preferFolder(name);
		blockLoader.preferFolder(name);
		lootLoader.preferFolder(name);
		regionLoader.preferFolder(name);
		entityLoader.preferFolder(name);
		dimensionLoader.preferFolder(name);
		generatorLoader.preferFolder(name);
		structureLoader.preferFolder(name);
	}

	public void clearLists()
	{
		lootLoader.clearList();
		blockLoader.clearList();
		entityLoader.clearList();
		biomeLoader.clearList();
		regionLoader.clearList();
		dimensionLoader.clearList();
		generatorLoader.clearList();
		structureLoader.clearList();
	}
}