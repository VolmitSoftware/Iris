package com.volmit.iris.util.decree.specialhandlers;

import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;
import com.volmit.iris.util.decree.handlers.BiomeHandler;
import com.volmit.iris.util.decree.handlers.PlayerHandler;
import org.bukkit.entity.Player;

public class NullableBiomeHandler extends BiomeHandler {

    @Override
    public IrisBiome parse(String in, boolean force) throws DecreeParsingException {
        return getPossibilities(in).stream().filter((i) -> toString(i).equalsIgnoreCase(in)).findFirst().orElse(null);
    }
}
