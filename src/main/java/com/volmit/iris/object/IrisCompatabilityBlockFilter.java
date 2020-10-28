package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.B;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Find and replace object materials for compatability")
@Data
public class IrisCompatabilityBlockFilter
{
	@Required
	@DontObfuscate
	@Desc("When iris sees this block, and it's not reconized")
	private String when = "";

	@Required
	@DontObfuscate
	@Desc("Replace it with this block. Dont worry if this block is also not reconized, iris repeat this compat check.")
	private String supplement = "";

	@DontObfuscate
	@Desc("If exact is true, it compares block data for example minecraft:some_log[axis=x]")
	private boolean exact = false;

	private final transient AtomicCache<FastBlockData> findData = new AtomicCache<>(true);
	private final transient AtomicCache<FastBlockData> replaceData = new AtomicCache<>(true);

	public IrisCompatabilityBlockFilter(String when, String supplement)
	{
		this(when, supplement, false);
	}

	public FastBlockData getFind()
	{
		return findData.aquire(() -> B.getBlockData(when));
	}

	public FastBlockData getReplace()
	{
		return replaceData.aquire(() ->
		{
			FastBlockData b = B.parseBlockDataOrNull(supplement);

			if(b == null)
			{
				return null;
			}

			Iris.warn("Compat: Using " + supplement + " in place of " + when + " since this server doesnt support '" + supplement + "'");

			return b;
		});
	}
}
