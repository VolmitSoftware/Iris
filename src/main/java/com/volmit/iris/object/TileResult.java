package com.volmit.iris.object;

import lombok.Data;

@Data
public class TileResult
{
	private IrisStructure structure;
	private IrisStructureTile tile;
	private IrisObjectPlacement placement;

	public TileResult(IrisStructure structure, IrisStructureTile tile, int rot)
	{
		this.structure = structure;
		this.tile = tile;
		IrisObjectPlacement p = new IrisObjectPlacement();
		IrisObjectRotation rt = new IrisObjectRotation();
		rt.setYAxis(new IrisAxisRotationClamp(rot != 0, rot, rot, 0));
		p.setRotation(rt);
		p.setBottom(true);
		p.setBore(structure.isBore());
		p.setClamp(structure.getClamp());
		p.setWaterloggable(structure.isUnderwater());
		p.setMode(tile.getPlaceMode());
		placement = p;
	}
}
