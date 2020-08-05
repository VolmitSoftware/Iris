package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;

import lombok.Data;
import lombok.EqualsAndHashCode;

@DontObfuscate
@Desc("Represents a structure tile")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisStructureTile
{
	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a ceiling?")
	private StructureTileCondition ceiling = StructureTileCondition.AGNOSTIC;

	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a floor?")
	private StructureTileCondition floor = StructureTileCondition.REQUIRED;

	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a north wall?")
	private StructureTileCondition north = StructureTileCondition.AGNOSTIC;

	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a south wall?")
	private StructureTileCondition south = StructureTileCondition.AGNOSTIC;

	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a east wall?")
	private StructureTileCondition east = StructureTileCondition.AGNOSTIC;

	@DontObfuscate
	@Desc("Is this structure allowed to place if there is supposed to be a west wall?")
	private StructureTileCondition west = StructureTileCondition.AGNOSTIC;

	@DontObfuscate
	@Desc("List of objects to place centered in this tile")
	private KList<String> objects = new KList<>();

	public IrisStructureTile()
	{

	}

	public boolean likeAGlove(boolean floor, boolean ceiling, KList<StructureTileFace> walls)
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
	
		if(!fitsWalls(walls))
		{
			return false;
		}
				
		//@done

		return true;
	}

	private boolean fitsWalls(KList<StructureTileFace> walls)
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
