package com.volmit.iris.noise;

import com.volmit.iris.util.RNG;

@FunctionalInterface
public interface CNGFactory 
{
	CNG create(RNG seed);
}
