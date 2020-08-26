package com.volmit.iris.util;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BoardSettings
{
	private BoardProvider boardProvider;

	private ScoreDirection scoreDirection;
}
