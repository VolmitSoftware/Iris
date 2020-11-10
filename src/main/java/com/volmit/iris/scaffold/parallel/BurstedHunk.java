package com.volmit.iris.scaffold.parallel;

import com.volmit.iris.scaffold.hunk.Hunk;

public interface BurstedHunk<T> extends Hunk<T>
{
	public int getOffsetX();

	public int getOffsetY();

	public int getOffsetZ();
}
