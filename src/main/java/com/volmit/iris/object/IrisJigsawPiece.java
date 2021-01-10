package com.volmit.iris.object;

import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@DontObfuscate
@Desc("Represents a structure tile")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisJigsawPiece extends IrisRegistrant
{
	@RegistryListObject
	@Required
	@DontObfuscate
	@Desc("The object this piece represents")
	private String object = "";

	@Required
	@DontObfuscate
	@ArrayType(type = IrisJigsawPieceConnector.class, min = 1)
	@Desc("The connectors this object contains")
	private KList<IrisJigsawPieceConnector> connectors = new KList<>();

	@Desc("Change how this object places depending on the terrain height map.")
	@DontObfuscate
	private ObjectPlaceMode placeMode;

	public IrisJigsawPieceConnector getConnector(IrisPosition relativePosition) {
		for(IrisJigsawPieceConnector i : connectors)
		{
			if(i.getPosition().equals(relativePosition))
			{
				return i;
			}
		}

		return null;
	}

	public IrisJigsawPiece copy() {
		IrisJigsawPiece p = new IrisJigsawPiece();
		p.setObject(getObject());
		p.setLoader(getLoader());
		p.setLoadKey(getLoadKey());
		p.setLoadFile(getLoadFile());
		p.setPlaceMode(getPlaceMode());
		p.setConnectors(new KList<>());

		for(IrisJigsawPieceConnector i : getConnectors())
		{
			p.getConnectors().add(i.copy());
		}

		return p;
	}
}
