package com.volmit.iris.util;

import com.volmit.iris.object.IrisStructure;
import com.volmit.iris.object.IrisStructureTile;

import lombok.Data;

@Data
public class IrisStructureResult
{
	private IrisStructureTile tile;
	private IrisStructure structure;

	public IrisStructureResult(IrisStructureTile tile, IrisStructure structure)
	{
		this.tile = tile;
		this.structure = structure;
	}
}
