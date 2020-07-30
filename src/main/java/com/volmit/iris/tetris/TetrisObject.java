package com.volmit.iris.tetris;

import com.volmit.iris.util.BlockPosition;
import com.volmit.iris.util.KMap;

import lombok.Data;

@Data
public class TetrisObject
{
	private int x;
	private int y;
	private int z;
	private KMap<BlockPosition, BlockPosition> holes;

	public TetrisObject(int x, int y, int z)
	{
		holes = new KMap<>();
	}
}
