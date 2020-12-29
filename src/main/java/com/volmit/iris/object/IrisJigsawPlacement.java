package com.volmit.iris.object;

import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a jigsaw placement")
@Data
public class IrisJigsawPlacement
{
	@RegistryListJigsaw
	@Required
	@DontObfuscate
	@Desc("The jigsaw structure to use")
	private String structure = "";

	@Required
	@MinNumber(1)
	@DontObfuscate
	@Desc("The rarity for this jigsaw structure to place on a per chunk basis")
	private int rarity = 29;
}
