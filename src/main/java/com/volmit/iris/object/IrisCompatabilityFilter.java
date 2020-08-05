package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.BlockDataTools;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

import lombok.Data;

@Desc("Find and replace object materials for compatability")
@Data
public class IrisCompatabilityFilter
{
	@DontObfuscate
	private String when;

	@DontObfuscate
	private String supplement;

	@DontObfuscate
	private boolean exact = false;

	private transient AtomicCache<BlockData> findData = new AtomicCache<>();
	private transient AtomicCache<BlockData> replaceData = new AtomicCache<>();

	public IrisCompatabilityFilter(String when, String supplement)
	{
		this(when, supplement, false);
	}

	public IrisCompatabilityFilter(String when, String supplement, boolean exact)
	{
		this.when = when;
		this.supplement = supplement;
		this.exact = exact;
	}

	public IrisCompatabilityFilter()
	{

	}

	public BlockData getFind()
	{
		return findData.aquire(() -> BlockDataTools.getBlockData(when));
	}

	public BlockData getReplace()
	{
		return replaceData.aquire(() -> BlockDataTools.getBlockData(supplement));
	}
}
