package ninja.bytecode.iris.object;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.block.data.BlockData;

import lombok.Data;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.Desc;

@Desc("Find and replace object materials")
@Data
public class IrisObjectReplace
{
	private String find;
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
