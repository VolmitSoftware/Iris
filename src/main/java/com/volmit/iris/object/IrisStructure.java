package com.volmit.iris.object;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.BlockPosition;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.Data;
import lombok.EqualsAndHashCode;

@DontObfuscate
@Desc("Represents a structure in iris.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisStructure extends IrisRegistrant
{
	@MinNumber(2)
	@Required
	@DontObfuscate
	@Desc("This is the human readable name for this structure. Such as Red Dungeon or Tropical Village.")
	private String name = "A Structure Type";

	@Required
	@MinNumber(3)
	@MaxNumber(64)
	@DontObfuscate
	@Desc("This is the x and z size of each grid cell")
	private int gridSize = 11;

	@Required
	@MinNumber(1)
	@MaxNumber(255)
	@DontObfuscate
	@Desc("This is the y size of each grid cell")
	private int gridHeight = 5;

	@MinNumber(1)
	@MaxNumber(82)
	@DontObfuscate
	@Desc("This is the maximum layers iris will generate for (height cells)")
	private int maxLayers = 1;

	@Required
	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("This is the wall chance. Higher values makes more rooms and less open halls")
	private double wallChance = 0.25;

	@DontObfuscate
	@Desc("Edges of tiles replace each other instead of having their own.")
	private boolean mergeEdges = false;

	@Required
	@ArrayType(min = 1, type = IrisStructureTile.class)
	@DontObfuscate
	@Desc("The tiles")
	private KList<IrisStructureTile> tiles = new KList<>();

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

		BlockPosition p = asTileHorizon(new BlockPosition((int) x, (int) y, (int) z), face);
		return (getWallGenerator(rng).fitDoubleD(0, 1, p.getX(), p.getY(), p.getZ()) < getWallChance());
	}

	public int getTileHorizon(double v)
	{
		return (int) Math.floor(v / gridSize);
	}

	public BlockPosition asTileHorizon(BlockPosition b, StructureTileFace face)
	{
		b.setX((int) (Math.floor((b.getX() * 2) / gridSize) + face.x()));
		b.setY((int) (Math.floor((b.getY() * 2) / gridHeight) + face.y()));
		b.setZ((int) (Math.floor((b.getZ() * 2) / gridSize) + face.z()));
		return b;
	}

	public CNG getWallGenerator(RNG rng)
	{
		return wallGenerator.aquire(() ->
		{
			RNG rngx = rng.nextParallelRNG((int) (name.hashCode() + gridHeight - gridSize + maxLayers + tiles.size()));
			return CNG.signature(rngx).scale(0.8);
		});
	}

	public IrisStructure()
	{

	}
}
