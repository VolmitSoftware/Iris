package com.volmit.iris.v2.scaffold.parallel;

import com.volmit.iris.v2.scaffold.hunk.Hunk;

public interface BurstedHunk<T> extends Hunk<T>
{
	public int getOffsetX();

	public int getOffsetY();

	public int getOffsetZ();
}
