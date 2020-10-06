package com.volmit.iris.gen.nms;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;

public class NMSCreator
{
	public static World createWorld(WorldCreator creator)
	{
		return createWorld(creator, true);
	}

	public static World createWorld(WorldCreator creator, boolean loadSpawn)
	{
		if(IrisSettings.get().isSkipPrepareSpawnNMS())
		{
			try
			{
				String code = Iris.nmsTag();

				if(code.equals("v1_16_R2"))
				{
					return NMSCreator162.createWorld(creator, loadSpawn);
				}
				else if(code.equals("v1_16_R1"))
				{
					return NMSCreator161.createWorld(creator, loadSpawn);
				}
				else if(code.equals("v1_15_R1"))
				{
					return NMSCreator151.createWorld(creator, loadSpawn);
				}
				else if(code.equals("v1_14_R1"))
				{
					return NMSCreator141.createWorld(creator, loadSpawn);
				}
			}

			catch(Throwable e)
			{

			}
		}

		return Bukkit.createWorld(creator);
	}
}
