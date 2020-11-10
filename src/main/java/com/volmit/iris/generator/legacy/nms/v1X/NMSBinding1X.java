package com.volmit.iris.generator.legacy.nms.v1X;

import com.volmit.iris.generator.legacy.atomics.AtomicCache;
import com.volmit.iris.generator.legacy.nms.INMSBinding;
import com.volmit.iris.generator.legacy.nms.INMSCreator;

public class NMSBinding1X implements INMSBinding
{
	private final AtomicCache<INMSCreator> creator = new AtomicCache<>();

	@Override
	public INMSCreator getCreator()
	{
		return creator.aquire(NMSCreator1X::new);
	}
}
