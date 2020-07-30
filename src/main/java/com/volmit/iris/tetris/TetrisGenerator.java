package com.volmit.iris.tetris;

import lombok.Data;

@Data
public class TetrisGenerator
{
	private int gridSize;

	public int getGrid(int block)
	{
		return (int) Math.floor((double) block / (double) gridSize);
	}

	public int getCenterFromGrid(int grid)
	{
		return (grid * gridSize) + (gridSize / 2);
	}

	public int getCenterFromBlock(int block)
	{
		return getCenterFromGrid(getGrid(block));
	}

	public int getMinFromGrid(int grid)
	{
		return (grid * gridSize);
	}

	public int getMinFromBlock(int block)
	{
		return getMinFromGrid(getGrid(block));
	}

	public int getMaxFromGrid(int grid)
	{
		return ((grid + 1) * gridSize) - 1;
	}

	public int getMaxFromBlock(int block)
	{
		return getMaxFromGrid(getGrid(block));
	}
}
