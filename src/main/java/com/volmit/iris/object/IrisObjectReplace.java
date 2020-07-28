package com.volmit.iris.object;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.block.data.BlockData;

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

	private transient ReentrantLock lock = new ReentrantLock();
	private transient BlockData findData;
	private transient BlockData replaceData;

	public IrisObjectReplace()
	{

	}

	public BlockData getFind()
	{
		if(findData == null)
		{
			findData = BlockDataTools.getBlockData(find);
		}

		return findData;
	}

	public BlockData getReplace()
	{
		if(replaceData == null)
		{
			replaceData = BlockDataTools.getBlockData(replace);
		}

		return replaceData;
	}
}
