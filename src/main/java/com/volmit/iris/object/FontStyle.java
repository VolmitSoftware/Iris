package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

@Desc("Represents a basic font style to apply to a font family")
public enum FontStyle
{
	@Desc("Plain old text")
	@DontObfuscate
	PLAIN,

	@Desc("Italicized Text")
	@DontObfuscate
	ITALIC,

	@Desc("Bold Text")
	@DontObfuscate
	BOLD,
}
