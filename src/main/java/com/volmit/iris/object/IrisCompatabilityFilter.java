package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.B;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@AllArgsConstructor
@Desc("Find and replace object materials for compatability")
@Data
public class IrisCompatabilityFilter
{
	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("When iris sees this block, and it's not reconized")
	private String when = "";

	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("Replace it with this block. Dont worry if this block is also not reconized, iris repeat this compat check.")
	private String supplement = "";

	@Builder.Default
	@DontObfuscate
	@Desc("If exact is true, it compares block data for example minecraft:some_log[axis=x]")
	private boolean exact = false;

	private final transient AtomicCache<BlockData> findData = new AtomicCache<>(true);
	private final transient AtomicCache<BlockData> replaceData = new AtomicCache<>(true);

	public IrisCompatabilityFilter(String when, String supplement)
	{
		this(when, supplement, false);
	}

	public IrisCompatabilityFilter()
	{

	}

	public BlockData getFind()
	{
		return findData.aquire(() -> B.getBlockData(when));
	}

	public BlockData getReplace()
	{
		return replaceData.aquire(() ->
		{
			BlockData b = B.getBlockData(supplement);

			Iris.warn("Compat: Using " + supplement + " in place of " + when + " since this server doesnt support '" + supplement + "'");

			return b;
		});
	}
}
