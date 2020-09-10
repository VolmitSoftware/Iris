package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.B;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.RegistryListBlockType;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents Block Data")
@Data
public class IrisBlockData
{
	@RegistryListBlockType

	@Required
	@DontObfuscate
	@Desc("The cave zoom. Higher values makes caves spread out further and branch less often, but are thicker.")
	private String block = "air";

	@DontObfuscate
	@Desc("The resource key. Typically Minecraft")
	private String key = "minecraft";

	@DontObfuscate
	@Desc("Optional properties for this block data such as 'waterlogged': true")
	private KMap<String, Object> data = new KMap<>();

	private final transient AtomicCache<BlockData> blockdata = new AtomicCache<>();
	private final transient AtomicCache<String> realProperties = new AtomicCache<>();

	public IrisBlockData(String b)
	{
		this.block = b;
	}

	public String computeProperties()
	{
		if(getData().isEmpty())
		{
			return "";
		}

		KList<String> r = new KList<>();

		for(String i : getData().keySet())
		{
			r.add(i + "=" + getData().get(i));
		}

		return "[" + r.toString(",") + "]";
	}

	public BlockData getBlockData()
	{
		return blockdata.aquire(() ->
		{
			BlockData b = B.get(getKey() + ":" + getBlock() + computeProperties());

			if(b != null)
			{
				return b;
			}

			return B.get("AIR");
		});
	}
}
