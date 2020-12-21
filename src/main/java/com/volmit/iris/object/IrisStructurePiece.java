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
public class IrisStructurePiece extends IrisRegistrant
{
	@RegistryListObject
	@Required
	@DontObfuscate
	@Desc("The object this piece represents")
	private String object = "";

	@Required
	@DontObfuscate
	@ArrayType(type = IrisStructurePieceConnector.class, min = 1)
	@Desc("The connectors this object contains")
	private KList<IrisStructurePieceConnector> connectors = new KList<>();
}
