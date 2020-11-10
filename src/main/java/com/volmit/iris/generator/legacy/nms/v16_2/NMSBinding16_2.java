package com.volmit.iris.generator.legacy.nms.v16_2;

import com.volmit.iris.generator.legacy.atomics.AtomicCache;
import com.volmit.iris.generator.legacy.nms.INMSBinding;
import com.volmit.iris.generator.legacy.nms.INMSCreator;

public class NMSBinding16_2 implements INMSBinding
{
	private final AtomicCache<INMSCreator> creator = new AtomicCache<>();

	@Override
	public INMSCreator getCreator()
	{
		return creator.aquire(NMSCreator16_2::new);
	}
}
