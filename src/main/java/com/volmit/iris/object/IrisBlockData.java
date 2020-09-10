package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.B;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
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

	@MinNumber(1)
	@MaxNumber(1000)
	@DontObfuscate
	@Desc("The weight is used when this block data is inside of a list of blockdata. A weight of two is just as if you placed two of the same block data values in the same list making it more common when randomly picked.")
	private int weight = 1;

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

	public static IrisBlockData from(String j)
	{
		IrisBlockData b = new IrisBlockData();
		String m = j.toLowerCase().trim();
	
		if(m.contains(":"))
		{
			b.setKey(m.split("\\Q:\\E")[0]);
			String v = m.split("\\Q:\\E")[1];
	
			if(v.contains("["))
			{
				KList<String> props = new KList<>();
				String rp = v.split("\\Q[\\E")[1].replaceAll("\\Q]\\E", "");
				b.setBlock(v.split("\\Q[\\E")[0]);
	
				if(rp.contains(","))
				{
					props.add(rp.split("\\Q,\\E"));
				}
	
				else
				{
					props.add(rp);
				}
	
				for(String i : props)
				{
					Object kg = filter(i.split("\\Q=\\E")[1]);
					b.data.put(i.split("\\Q=\\E")[0], kg);
				}
			}
	
			else
			{
				b.setBlock(v);
			}
		}
	
		else
		{
			b.setBlock(m);
		}
	
		return b;
	}

	private static Object filter(String string)
	{
		if(string.equals("true"))
		{
			return true;
		}

		if(string.equals("false"))
		{
			return false;
		}

		try
		{
			return Integer.valueOf(string);
		}

		catch(Throwable e)
		{

		}

		return string;
	}
}
