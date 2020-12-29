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
@Desc("Represents a jigsaw structure")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisJigsawStructure extends IrisRegistrant
{
	@RegistryListJigsawPiece
	@Required
	@DontObfuscate
	@ArrayType(min = 1,type = String.class)
	@Desc("The starting pieces. Randomly chooses a starting piece, then connects pieces using the pools define in the starting piece.")
	private KList<String> pieces = new KList<>();

	@MaxNumber(32)
	@MinNumber(1)
	@DontObfuscate
	@Desc("The maximum pieces that can step out from the center piece")
	private int maxDepth  = 9;
}
