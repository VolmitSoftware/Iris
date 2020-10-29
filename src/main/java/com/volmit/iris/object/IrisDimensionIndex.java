package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an index for dimensions to take up vertical slots in the same world")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisDimensionIndex
{
	@Required
	@DontObfuscate
	@Desc("The weight of this dimension. If there are 2 dimensions, if the weight is the same on both, both dimensions will take up 128 blocks of height.")
	private double weight = 1D;

	@DontObfuscate
	@Desc("If inverted is set to true, the dimension will be updide down in the world")
	private boolean inverted = false;

	@DontObfuscate
	@Required
	@RegistryListDimension
	@MinNumber(1)
	private String dimension = "";
}
