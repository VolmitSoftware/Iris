package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KSet;

import lombok.Data;

@Desc("A biome mutation if a condition is met")
@Data
public class IrisBiomeMutation
{
	@DontObfuscate
	@Desc("One of The following biomes or regions must show up")
	private KList<String> sideA = new KList<>();

	@DontObfuscate
	@Desc("One of The following biomes or regions must show up")
	private KList<String> sideB = new KList<>();

	@DontObfuscate
	@Desc("The scan radius for placing this mutator")
	private int radius = 1;

	@DontObfuscate
	@Desc("How many tries per chunk to check for this mutation")
	private int checks = 2;

	@DontObfuscate
	@Desc("Objects define what schematics (iob files) iris will place in this biome mutation")
	private KList<IrisObjectPlacement> objects = new KList<IrisObjectPlacement>();

	private transient KList<String> sideACache;
	private transient KList<String> sideBCache;

	public KList<String> getRealSideA()
	{
		if(sideACache == null)
		{
			sideACache = processList(getSideA());
		}

		return sideACache;
	}

	public KList<String> getRealSideB()
	{
		if(sideBCache == null)
		{
			sideBCache = processList(getSideB());
		}

		return sideBCache;
	}

	public KList<String> processList(KList<String> s)
	{
		KSet<String> r = new KSet<>();

		for(String i : s)
		{
			String q = i;

			if(q.startsWith("^"))
			{
				r.addAll(Iris.data.getRegionLoader().load(q.substring(1)).getLandBiomes());
				continue;
			}

			else if(q.startsWith("*"))
			{
				String name = q.substring(1);
				r.addAll(Iris.data.getBiomeLoader().load(name).getAllChildren(7));
			}

			else if(q.startsWith("!"))
			{
				r.remove(q.substring(1));
			}

			else if(q.startsWith("!*"))
			{
				String name = q.substring(2);
				r.removeAll(Iris.data.getBiomeLoader().load(name).getAllChildren(7));
			}

			else
			{
				r.add(q);
			}
		}

		return new KList<String>(r);
	}
}
