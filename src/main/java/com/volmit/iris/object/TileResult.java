package com.volmit.iris.object;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
		p.setSmartBore(tile.isSmartBore());
		p.setWaterloggable(structure.isUnderwater());
		p.setMode(tile.getPlaceMode());
		placement = p;
	}
}
