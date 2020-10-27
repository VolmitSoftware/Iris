package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

@Desc("Represents a location where decorations should go")
public enum DecorationPart
{
	@Desc("The default, decorate anywhere")
	@DontObfuscate
	NONE,

	@Desc("Targets shore lines (typically for sugar cane)")
	@DontObfuscate
	SHORE_LINE,

	@Desc("Target sea surfaces (typically for lilypads)")
	@DontObfuscate
	SEA_SURFACE,

	@Desc("Decorates on cave & carving ceilings or underside of overhangs")
	@DontObfuscate
	CEILING,
}
