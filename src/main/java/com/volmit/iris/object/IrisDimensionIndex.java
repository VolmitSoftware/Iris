package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RegistryListDimension;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

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
	@Desc("Only one dimension layer should be set to primary. The primary dimension layer is where players spawn, and the biomes that the vanilla structure system uses to figure out what structures to place.")
	private boolean primary = false;

	@DontObfuscate
	@Required
	@RegistryListDimension
	@MinNumber(1)
	@Desc("Name of dimension")
	private String dimension = "";
}
