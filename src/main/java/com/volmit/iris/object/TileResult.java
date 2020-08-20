package com.volmit.iris.object;

import lombok.Data;

@Data
public class TileResult
{
	private IrisStructureTile tile;
	private IrisObjectPlacement placement;

	public TileResult(IrisStructureTile tile, int rot)
	{
		this.tile = tile;
		IrisObjectPlacement p = new IrisObjectPlacement();
		IrisObjectRotation rt = new IrisObjectRotation();
		rt.setYAxis(new IrisAxisRotationClamp(rot != 0, rot, rot, 0));
		p.setRotation(rt);
		p.setBottom(true);
		p.setMode(ObjectPlaceMode.PAINT);
		placement = p;
	}
}
