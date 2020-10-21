package com.volmit.iris.gen;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.B;
import com.volmit.iris.util.FastBlockData;
import com.volmit.iris.util.MortarSender;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class DimensionalTerrainProvider extends ContextualTerrainProvider
{
	private String dimensionName;
	private IrisDimension forceDimension;
	protected static final FastBlockData AIR = FastBlockData.of(Material.AIR);
	protected static final FastBlockData CAVE_AIR = B.get("CAVE_AIR");
	protected static final FastBlockData BEDROCK = FastBlockData.of(Material.BEDROCK);
	protected static final FastBlockData WATER = FastBlockData.of(Material.WATER);

	public DimensionalTerrainProvider(TerrainTarget t, String dimensionName)
	{
		super(t);
		setDimensionName(dimensionName);
		setForceDimension(null);

		if(getDimensionName().isEmpty())
		{
			File folder = new File(getTarget().getFolder(), "iris/dimensions");

			if(!folder.exists())
			{
				Iris.error("Missing World iris/dimensions folder! (" + folder.getAbsolutePath() + ")");
				setDimensionName(IrisSettings.get().getDefaultWorldType());
				Iris.proj.installIntoWorld(new MortarSender(Bukkit.getConsoleSender()), getDimensionName(), getTarget().getFolder());
				return;
			}

			if(!folder.exists())
			{
				Iris.error("Missing World iris/dimensions folder! (" + folder.getAbsolutePath() + ")");
				try
				{
					throw new RuntimeException("Cant find dimension data!");
				}

				catch(Throwable e)
				{
					fail(e);
				}
				return;
			}

			for(File i : folder.listFiles())
			{
				if(i.isFile() && i.getName().endsWith(".json"))
				{
					setDimensionName(i.getName().replaceAll("\\Q.json\\E", ""));
					return;
				}
			}

			if(!folder.exists())
			{
				Iris.error("Missing World iris/dimensions folder! (" + folder.getAbsolutePath() + ")");
				try
				{
					throw new RuntimeException("Cant find dimension data!");
				}

				catch(Throwable e)
				{
					fail(e);
				}
				return;
			}
		}

		try
		{
			getData().preferFolder(getDimension().getLoadFile().getParentFile().getParentFile().getName());
		}

		catch(Throwable ignored)
		{

		}
	}

	protected void forceDimension(IrisDimension sky)
	{
		setForceDimension(sky);
	}

	protected void useDefaultDimensionSetupNOW()
	{

	}

	public void onPlayerLeft(Player p)
	{

	}

	public void onTick(int m)
	{
		getData().preferFolder(getDimension().getLoadFile().getParentFile().getParentFile().getName());
	}

	public void onInit(RNG masterRandom)
	{
		if(getDimension().hasSky())
		{
			getDimension().getSky().setSkyDimension(true);
		}
	}

	public IrisDimension getDimension()
	{
		if(forceDimension != null)
		{
			return forceDimension;
		}

		IrisDimension d = loadDimension(getDimensionName());

		return d;
	}

	protected IrisBiome focus()
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

		return biome;
	}

	public double getModifiedX(int rx, int rz)
	{
		return (getDimension().cosRotate() * rx) + (-getDimension().sinRotate() * rz) + getDimension().getCoordFracture(getMasterRandom(), 39392).fitDouble(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
	}

	public double getModifiedZ(int rx, int rz)
	{
		return (getDimension().sinRotate() * rx) + (getDimension().cosRotate() * rz) + getDimension().getCoordFracture(getMasterRandom(), 39392).fitDouble(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
	}

	public double getZoomed(double modified)
	{
		return modified / getDimension().getTerrainZoom();
	}

	public double getUnzoomed(double modified)
	{
		return modified * getDimension().getTerrainZoom();
	}
}
