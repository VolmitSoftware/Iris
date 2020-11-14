package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.B;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

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

	private final transient AtomicCache<BlockData> findData = new AtomicCache<>(true);
	private final transient AtomicCache<BlockData> replaceData = new AtomicCache<>(true);

	public IrisCompatabilityBlockFilter(String when, String supplement)
	{
		this(when, supplement, false);
	}

	public BlockData getFind()
	{
		return findData.aquire(() -> B.get(when));
	}

	public BlockData getReplace()
	{
		return replaceData.aquire(() ->
		{
			BlockData b = B.getOrNull(supplement);

			if(b == null)
			{
				return null;
			}

			Iris.warn("Compat: Using " + supplement + " in place of " + when + " since this server doesnt support '" + supplement + "'");

			return b;
		});
	}
}
