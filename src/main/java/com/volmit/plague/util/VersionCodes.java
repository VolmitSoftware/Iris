package com.volmit.plague.util;


import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.O;

public class VersionCodes
{
	public static int getVersionCode(String version)
	{
		O<Integer> bit = new O<Integer>().set(1);
		O<Integer> code = new O<Integer>().set(0);

		for(char i : version.toCharArray())
		{
			J.attempt(() -> code.set(code.get() + Integer.parseInt(i + "") * bit.get()));
			bit.set(bit.get() + 1);
		}

		return code.get();
	}
}
