package com.volmit.iris.core.decrees;

import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;

@Decree(name = "what", aliases = "?", description = "Get information about the world around you", origin = DecreeOrigin.PLAYER)
public class DecIrisWhat implements DecreeExecutor {
    
}
