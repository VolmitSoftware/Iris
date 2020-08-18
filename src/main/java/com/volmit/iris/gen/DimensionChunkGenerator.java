package com.volmit.iris.gen;

import java.io.File;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.B;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class DimensionChunkGenerator extends ContextualChunkGenerator
{
	protected String dimensionName;
	protected static final BlockData AIR = Material.AIR.createBlockData();
	protected static final BlockData CAVE_AIR = B.get("CAVE_AIR");
	protected static final BlockData BEDROCK = Material.BEDROCK.createBlockData();
	protected static final BlockData WATER = Material.WATER.createBlockData();

	public DimensionChunkGenerator(String dimensionName)
	{
		super();
		this.dimensionName = dimensionName;
	}

	public void onPlayerLeft(Player p)
	{

	}

	public void onTick(int m)
	{

	}

	public void onInit(World world, RNG masterRandom)
	{
		if(dimensionName.isEmpty())
		{
			File folder = new File(world.getWorldFolder(), "iris/dimensions");

			if(!folder.exists())
			{
				Iris.error("Missing World iris/dimensions folder!");
				dimensionName = "error-missing-dimension";
				return;
			}

			for(File i : folder.listFiles())
			{
				if(i.isFile() && i.getName().endsWith(".json"))
				{
					dimensionName = i.getName().replaceAll("\\Q.json\\E", "");
					return;
				}
			}

			Iris.error("Missing World iris/dimensions/<dimension-name>.json file. Assuming overworld!");
			dimensionName = "error-missing-dimension";
			fail(new RuntimeException("Missing dimension folder/file in " + folder.getAbsolutePath()));
		}

		getData().preferFolder(getDimension().getLoadFile().getParentFile().getParentFile().getName());
	}

	public IrisDimension getDimension()
	{
		IrisDimension d = loadDimension(dimensionName);

		if(d == null)
		{
			Iris.error("Can't find dimension: " + dimensionName);
		}

		return d;
	}

	protected BiomeResult focus()
	{
		IrisBiome biome = loadBiome(getDimension().getFocus());

		for(String i : getDimension().getRegions())
		{
			IrisRegion reg = loadRegion(i);

			if(reg.getLandBiomes().contains(biome.getLoadKey()))
			{
				biome.setInferredType(InferredType.LAND);
				break;
			}

			if(reg.getSeaBiomes().contains(biome.getLoadKey()))
			{
				biome.setInferredType(InferredType.SEA);
				break;
			}

			if(reg.getShoreBiomes().contains(biome.getLoadKey()))
			{
				biome.setInferredType(InferredType.SHORE);
				break;
			}
		}

		return new BiomeResult(biome, 0);
	}

	public double getModifiedX(int rx, int rz)
	{
		return (getDimension().cosRotate() * rx) + (-getDimension().sinRotate() * rz) +

				getDimension().getCoordFracture(masterRandom, 39392).fitDouble(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
	}

	public double getModifiedZ(int rx, int rz)
	{
		return (getDimension().sinRotate() * rx) + (getDimension().cosRotate() * rz) +

				getDimension().getCoordFracture(masterRandom, 39392).fitDouble(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
	}

	public double getZoomed(double modified)
	{
		return (double) (modified) / getDimension().getTerrainZoom();
	}

	public double getUnzoomed(double modified)
	{
		return (double) (modified) * getDimension().getTerrainZoom();
	}
}
