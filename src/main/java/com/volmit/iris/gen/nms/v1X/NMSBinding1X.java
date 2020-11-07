package com.volmit.iris.gen.nms.v1X;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.gen.nms.INMSBinding;
import com.volmit.iris.gen.nms.INMSCreator;

public class NMSBinding1X implements INMSBinding
{
	private final AtomicCache<INMSCreator> creator = new AtomicCache<>();

	@Override
	public INMSCreator getCreator()
	{
		return creator.aquire(NMSCreator1X::new);
	}
}
