package com.volmit.iris.gen.v2.scaffold.multicore;

import com.volmit.iris.gen.v2.scaffold.hunk.Hunk;

public interface BurstedHunk<T> extends Hunk<T>
{
	public int getOffsetX();

	public int getOffsetY();

	public int getOffsetZ();
}
