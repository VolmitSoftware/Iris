package com.volmit.iris.util.decree.specialhandlers;

import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;
import com.volmit.iris.util.decree.handlers.BiomeHandler;
import com.volmit.iris.util.decree.handlers.RegionHandler;

import javax.swing.plaf.synth.Region;

public class NullableRegionHandler extends RegionHandler {

    @Override
    public IrisRegion parse(String in, boolean force) throws DecreeParsingException {
        return getPossibilities(in).stream().filter((i) -> toString(i).equalsIgnoreCase(in)).findFirst().orElse(null);
    }
}
