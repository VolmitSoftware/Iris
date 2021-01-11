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

	private transient AtomicCache<Integer> maxDimension = new AtomicCache<>();

	private void loadPool(String p, KList<String> pools, KList<String> pieces)
	{
		IrisJigsawPool pool = getLoader().getJigsawPoolLoader().load(p);

		for(String i : pool.getPieces())
		{
			if(pieces.addIfMissing(i))
			{
				loadPiece(i, pools, pieces);
			}
		}
	}

	private void loadPiece(String p, KList<String> pools, KList<String> pieces)
	{
		IrisJigsawPiece piece = getLoader().getJigsawPieceLoader().load(p);
		for(IrisJigsawPieceConnector i : piece.getConnectors())
		{
			for(String j : i.getPools())
			{
				if(pools.addIfMissing(j))
				{
					loadPool(p, pools, pieces);
				}
			}
		}
	}

	public int getMaxDimension()
	{
		return maxDimension.aquire(() -> {
			int max = 0;
			KList<String> pools = new KList<>();
			KList<String> pieces = new KList<>();

			for(String i : getPieces())
			{
				loadPiece(i, pools, pieces);
			}

			for(String i : pieces)
			{
				max = Math.max(max, getLoader().getJigsawPieceLoader().load(i).getMax3dDimension());
			}

			return max;
		});
	}
}
