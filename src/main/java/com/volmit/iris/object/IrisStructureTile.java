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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Builder
@AllArgsConstructor
@DontObfuscate
@Desc("Represents a structure tile")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisStructureTile
{
	@Builder.Default
	@DontObfuscate
	@Desc("Reference loot tables in this area")
	private IrisLootReference loot = new IrisLootReference();

	@Builder.Default
	@DontObfuscate
	@Desc("Entity spawns to override or add to this structure tile")
	@ArrayType(min = 1, type = IrisEntitySpawnOverride.class)
	private KList<IrisEntitySpawnOverride> entitySpawnOverrides = new KList<>();

	@Builder.Default
	@DontObfuscate
	@Desc("Entity spawns during generation")
	@ArrayType(min = 1, type = IrisEntityInitialSpawn.class)
	private KList<IrisEntityInitialSpawn> entityInitialSpawns = new KList<>();

	@Builder.Default
	@DontObfuscate
	@Desc("The place mode for this tile")
	private ObjectPlaceMode placeMode = ObjectPlaceMode.CENTER_HEIGHT;

	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a ceiling?")
	private StructureTileCondition ceiling = StructureTileCondition.AGNOSTIC;

	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a floor?")
	private StructureTileCondition floor = StructureTileCondition.REQUIRED;

	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a north wall?")
	private StructureTileCondition north = StructureTileCondition.AGNOSTIC;

	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a south wall?")
	private StructureTileCondition south = StructureTileCondition.AGNOSTIC;

	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a east wall?")
	private StructureTileCondition east = StructureTileCondition.AGNOSTIC;

	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a west wall?")
	private StructureTileCondition west = StructureTileCondition.AGNOSTIC;

	@Builder.Default
	@RegistryListObject
	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("List of objects to place centered in this tile")
	private KList<String> objects = new KList<>();

	@Builder.Default
	@DontObfuscate
	@Desc("If set to true, Iris will try to fill the insides of 'rooms' and 'pockets' where air should fit based off of raytrace checks. This prevents a village house placing in an area where a tree already exists, and instead replaces the parts of the tree where the interior of the structure is. \n\nThis operation does not affect warmed-up generation speed however it does slow down loading objects.")
	private boolean smartBore = false;

	@Builder.Default
	@RegistryListObject
	@ArrayType(min = 1, type = IrisRareObject.class)
	@DontObfuscate
	@Desc("List of objects to place centered in this tile but with rarity. These items only place some of the time so specify objects for common stuff too.")
	private KList<IrisRareObject> rareObjects = new KList<>();

	private final transient KMap<Integer, IrisObject> forceObjects = new KMap<>();
	private final transient AtomicCache<Integer> minFaces = new AtomicCache<>();
	private final transient AtomicCache<Integer> maxFaces = new AtomicCache<>();

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
