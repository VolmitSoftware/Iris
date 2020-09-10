package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisObjectLimit
{

	@MinNumber(0)
	@MaxNumber(255)
	@DontObfuscate
	@Desc("The minimum height for placement (bottom of object)")
	private int minimumHeight = 0;

	@MinNumber(0)
	@MaxNumber(255)
	@DontObfuscate
	@Desc("The maximum height for placement (top of object)")
	private int maximumHeight = 255;

	public boolean canPlace(int h, int l)
	{
		if(h > maximumHeight || l < minimumHeight)
		{
			return false;
		}

		return true;
	}
}
