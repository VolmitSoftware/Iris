package com.volmit.iris.gen.scaffold;

import com.volmit.iris.gen.IrisTerrainProvider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IrisGenConfiguration
{
	private TerrainTarget target;

	@Builder.Default
	private int threads = 16;

	@Builder.Default
	private String dimension = "";

	public IrisTerrainProvider buildGenerator()
	{
		return new IrisTerrainProvider(this);
	}
}
