package com.volmit.iris.nms.v1X;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.nms.INMSBinding;
import com.volmit.iris.nms.INMSCreator;

public class NMSBinding1X implements INMSBinding
{
	private final AtomicCache<INMSCreator> creator = new AtomicCache<>();

	@Override
	public INMSCreator getCreator()
	{
		return creator.aquire(NMSCreator1X::new);
	}
}
