package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
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
	private int maxDepth = 9;

	@DontObfuscate
	@Desc("If set to true, iris will look for any pieces with only one connector in valid pools for edge connectors and attach them to 'terminate' the paths/piece connectors. Essentially it caps off ends. For example in a village, Iris would add houses to the ends of roads where possible. For terminators to be selected, they can only have one connector or they wont be chosen.")
	private boolean terminate = true;

	private AtomicCache<Integer> maxDimension = new AtomicCache<>();
}
