package com.volmit.iris;

import com.volmit.iris.util.RollingSequence;

import lombok.Data;

@Data
public class IrisMetrics
{
	private final RollingSequence parallax;
	private final RollingSequence terrain;
	private final RollingSequence post;
	private final RollingSequence total;
	private final RollingSequence perSecond;
	private final RollingSequence loss;
	public int generators = 0;
	public int noiseHits = 0;

	public IrisMetrics(int memory)
	{
		parallax = new RollingSequence(memory);
		terrain = new RollingSequence(memory);
		post = new RollingSequence(memory);
		total = new RollingSequence(memory);
		perSecond = new RollingSequence(5);
		loss = new RollingSequence(memory);
	}
}
