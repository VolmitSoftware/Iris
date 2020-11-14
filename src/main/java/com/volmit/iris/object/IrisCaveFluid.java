package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.util.B;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisCaveFluid
{
	@Required
	@MaxNumber(255)
	@MinNumber(0)
	@DontObfuscate
	@Desc("The fluid height of the cave")
	private int fluidHeight = 35;

	@DontObfuscate
	@Desc("Insead of fluidHeight & below being fluid, turning inverse height on will simply spawn fluid in this cave layer from min(max_height, cave_height) to the fluid height. Basically, fluid will spawn above the fluidHeight value instead of below the fluidHeight.")
	private boolean inverseHeight = false;

	@Required
	@DontObfuscate
	@Desc("The fluid type that should spawn here")
	private IrisBlockData fluidType = new IrisBlockData("CAVE_AIR");

	private final transient AtomicCache<BlockData> fluidData = new AtomicCache<>();

	public boolean hasFluid(IrisDataManager rdata)
	{
		return !B.isAir(getFluid(rdata));
	}

	public BlockData getFluid(IrisDataManager rdata)
	{
		return fluidData.aquire(() ->
		{
			BlockData b = getFluidType().getBlockData(rdata);

			if(b != null)
			{
				return b;
			}

			return B.get("CAVE_AIR");
		});
	}
}
