package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a position")
@Data
public class IrisPosition2D
{
	@DontObfuscate
	@Desc("The x position")
	private int x = 0;

	@DontObfuscate
	@Desc("The z position")
	private int z = 0;
}
