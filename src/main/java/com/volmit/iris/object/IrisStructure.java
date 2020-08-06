package com.volmit.iris.object;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.CNG;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@DontObfuscate
@Desc("Represents a structure in iris.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisStructure extends IrisRegistrant
{
	@DontObfuscate
	@Desc("This is the human readable name for this structure. Such as Red Dungeon or Tropical Village.")
	private String name = "A Structure Type";

	@DontObfuscate
	@Desc("This is the x and z size of each grid cell")
	private int gridSize = 11;

	@DontObfuscate
	@Desc("This is the y size of each grid cell")
	private int gridHeight = 5;

	@DontObfuscate
	@Desc("This is the maximum layers iris will generate for (height cells)")
	private int maxLayers = 1;

	@DontObfuscate
	@Desc("This is the wall chance. Higher values makes more rooms and less open halls")
	private double wallChance = 0.25;

	@DontObfuscate
	@Desc("Edges of tiles replace each other instead of having their own.")
	private boolean mergeEdges = true;

	@DontObfuscate
	@Desc("The tiles")
	private KList<IrisStructureTile> tiles = new KList<>();

	@DontObfuscate
	@Desc("This is the wall chance zoom")
	private double wallChanceZoom = 1D;

	@DontObfuscate
	@Desc("The dispersion of walls")
	private Dispersion dispersion = Dispersion.SCATTER;

	private transient AtomicCache<CNG> wallGenerator = new AtomicCache<>();

	public TileResult getTile(RNG rng, double x, double y, double z)
	{
		KList<StructureTileFace> walls = new KList<>();
		boolean floor = isWall(rng, x, y, z, StructureTileFace.DOWN);
		boolean ceiling = isWall(rng, x, y, z, StructureTileFace.UP);

		if(isWall(rng, x, y, z, StructureTileFace.NORTH))
		{
			walls.add(StructureTileFace.NORTH);
		}

		if(isWall(rng, x, y, z, StructureTileFace.SOUTH))
		{
			walls.add(StructureTileFace.SOUTH);
		}

		if(isWall(rng, x, y, z, StructureTileFace.EAST))
		{
			walls.add(StructureTileFace.EAST);
		}

		if(isWall(rng, x, y, z, StructureTileFace.WEST))
		{
			walls.add(StructureTileFace.WEST);
		}

		int rt = 0;

		for(int cx = 0; cx < 4; cx++)
		{
			for(IrisStructureTile i : tiles)
			{
				if(i.likeAGlove(floor, ceiling, walls))
				{
					return new TileResult(i, rt);
				}
			}

			if(cx < 3)
			{
				rotate(walls);
				rt += 90;
			}
		}

		return null;
	}

	public void rotate(KList<StructureTileFace> faces)
	{
		for(int i = 0; i < faces.size(); i++)
		{
			faces.set(i, faces.get(i).rotate90CW());
		}
	}

	public boolean isWall(RNG rng, double x, double y, double z, StructureTileFace face)
	{
		if((face == StructureTileFace.DOWN || face == StructureTileFace.UP) && maxLayers == 1)
		{
			return true;
		}

		int gs = getGridSize() + 1;
		int gh = getGridHeight() + 1;
		int gx = getTileHorizon(x);
		int gy = getTileVertical(y);
		int gz = getTileHorizon(z);
		int hx = face.x();
		int hy = face.y();
		int hz = face.z();

		int tx = (gx * 2) + (hx * gs);
		int ty = (gy * 2) + (hy * gh);
		int tz = (gz * 2) + (hz * gs);

		return getWallGenerator(rng).fitDoubleD(0, 1, (tx) / wallChanceZoom, ty / wallChanceZoom, tz / wallChanceZoom) < getWallChance();
	}

	public int getTileHorizon(double v)
	{
		return (int) Math.floor(v / gridSize);
	}

	public int getTileVertical(double v)
	{
		return (int) Math.floor(v / gridHeight);
	}

	public CNG getWallGenerator(RNG rng)
	{
		return wallGenerator.aquire(() ->
		{
			CNG wallGenerator = new CNG(rng);
			RNG rngx = rng.nextParallelRNG((int) ((wallChance * 102005) + gridHeight - gridSize + maxLayers + tiles.size()));

			switch(dispersion)
			{
				case SCATTER:
					wallGenerator = CNG.signature(rngx).freq(1000000);
					break;
				case WISPY:
					wallGenerator = CNG.signature(rngx);
					break;
			}

			return wallGenerator;
		});
	}

	public IrisStructure()
	{

	}
}
