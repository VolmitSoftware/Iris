package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.BlockDataTools;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

import lombok.Data;

@Desc("Find and replace object materials")
@Data
public class IrisObjectReplace
{
	@DontObfuscate
	private String find;

	@DontObfuscate
	private String replace;

	@DontObfuscate
	private boolean exact = false;

	private transient AtomicCache<BlockData> findData = new AtomicCache<>();
	private transient AtomicCache<BlockData> replaceData = new AtomicCache<>();

	public IrisObjectReplace()
	{

	}

	public BlockData getFind()
	{
		return findData.aquire(() -> BlockDataTools.getBlockData(find));
	}

	public BlockData getReplace()
	{
		return replaceData.aquire(() -> BlockDataTools.getBlockData(replace));
	}
}
