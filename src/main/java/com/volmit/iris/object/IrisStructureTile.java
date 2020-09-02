package com.volmit.iris.object;

import java.util.Objects;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.RegistryListObject;
import com.volmit.iris.util.Required;

import lombok.Data;
import lombok.EqualsAndHashCode;

@DontObfuscate
@Desc("Represents a structure tile")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisStructureTile
{
	@DontObfuscate
	@Desc("Reference loot tables in this area")
	private IrisLootReference loot = new IrisLootReference();

	@DontObfuscate
	@Desc("The place mode for this tile")
	private ObjectPlaceMode placeMode = ObjectPlaceMode.CENTER_HEIGHT;

	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a ceiling?")
	private StructureTileCondition ceiling = StructureTileCondition.AGNOSTIC;

	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a floor?")
	private StructureTileCondition floor = StructureTileCondition.REQUIRED;

	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a north wall?")
	private StructureTileCondition north = StructureTileCondition.AGNOSTIC;

	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a south wall?")
	private StructureTileCondition south = StructureTileCondition.AGNOSTIC;

	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a east wall?")
	private StructureTileCondition east = StructureTileCondition.AGNOSTIC;

	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a west wall?")
	private StructureTileCondition west = StructureTileCondition.AGNOSTIC;

	@RegistryListObject
	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("List of objects to place centered in this tile")
	private KList<String> objects = new KList<>();

	private transient KMap<Integer, IrisObject> forceObjects = new KMap<>();

	@RegistryListObject
	@ArrayType(min = 1, type = IrisRareObject.class)
	@DontObfuscate
	@Desc("List of objects to place centered in this tile but with rarity. These items only place some of the time so specify objects for common stuff too.")
	private KList<IrisRareObject> rareObjects = new KList<>();

	private transient AtomicCache<Integer> minFaces = new AtomicCache<>();
	private transient AtomicCache<Integer> maxFaces = new AtomicCache<>();

	public IrisStructureTile()
	{

	}

	public int hashFace()
	{
		return Objects.hash(ceiling, floor, south, north, east, west);
	}

	public String toString()
	{
		return (ceiling.required() ? "C" : "") + (floor.required() ? "F" : "") + "| " + (north.required() ? "X" : "-") + (south.required() ? "X" : "-") + (east.required() ? "X" : "-") + (west.required() ? "X" : "-") + " |";
	}

	public boolean likeAGlove(boolean floor, boolean ceiling, KList<StructureTileFace> walls, int faces, int openings)
	{
		//@builder
		
		if((getFloor().required() && !floor) || (getCeiling().required() && !ceiling))
		{
			return false;
		}
		
		if((!getFloor().supported() && floor) || (!getCeiling().supported() && ceiling))
		{
			return false;
		}
	
		if(!fitsWalls(walls, faces, openings))
		{
			return false;
		}
				
		//@done

		return faces >= minFaces.aquire(() ->
		{
			int m = 0;
			m += this.ceiling.required() ? 1 : 0;
			m += this.floor.required() ? 1 : 0;
			m += this.north.required() ? 1 : 0;
			m += this.south.required() ? 1 : 0;
			m += this.east.required() ? 1 : 0;
			m += this.west.required() ? 1 : 0;
			return m;
		}) && faces <= maxFaces.aquire(() ->
		{
			int m = 0;
			m += this.ceiling.supported() ? 1 : 0;
			m += this.floor.supported() ? 1 : 0;
			m += this.north.supported() ? 1 : 0;
			m += this.south.supported() ? 1 : 0;
			m += this.east.supported() ? 1 : 0;
			m += this.west.supported() ? 1 : 0;
			return m;
		});
	}

	private boolean fitsWalls(KList<StructureTileFace> walls, int faces, int openings)
	{
		//@builder
		if((getNorth().required() && !walls.contains(StructureTileFace.NORTH)) 
				|| (getSouth().required() && !walls.contains(StructureTileFace.SOUTH)) 
				|| (getEast().required() && !walls.contains(StructureTileFace.EAST)) 
				|| (getWest().required() && !walls.contains(StructureTileFace.WEST)))
		{
			return false;
		}
		
		if((!getNorth().supported() && walls.contains(StructureTileFace.NORTH)) 
				|| (!getSouth().supported() && walls.contains(StructureTileFace.SOUTH)) 
				|| (!getEast().supported() && walls.contains(StructureTileFace.EAST)) 
				|| (!getWest().supported() && walls.contains(StructureTileFace.WEST)))
		{
			return false;
		}
		//@done

		return true;
	}
}
