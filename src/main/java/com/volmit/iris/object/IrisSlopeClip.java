package com.volmit.iris.object;

import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisSlopeClip
{
	@MinNumber(0)
	@MaxNumber(255)
	@DontObfuscate
	@Desc("The minimum slope for placement")
	private double minimumSlope = 0;

	@MinNumber(0)
	@MaxNumber(255)
	@DontObfuscate
	@Desc("The maximum slope for placement")
	private double maximumSlope = 10;

	public boolean isDefault() {
		return minimumSlope <= 0 && maximumSlope >= 10;
	}

	public boolean isValid(double slope)
	{
		if(isDefault())
		{
			return true;
		}

		if(minimumSlope > slope || maximumSlope < slope)
		{
			return false;
		}

		return true;
	}
}
