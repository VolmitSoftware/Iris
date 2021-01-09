package com.volmit.iris.object;

import com.google.gson.Gson;
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

	@DontObfuscate
	@Desc("Options for placement")
	private IrisObjectPlacementOptions placementOptions = new IrisObjectPlacementOptions();

	@Required
	@DontObfuscate
	@ArrayType(type = IrisJigsawPieceConnector.class, min = 1)
	@Desc("The connectors this object contains")
	private KList<IrisJigsawPieceConnector> connectors = new KList<>();

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
		return new Gson().fromJson(new Gson().toJson(this), getClass());
	}
}
