package com.volmit.example.terrain;

import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.Level;
import com.volmit.example.hosttypes.Palette;
import java.util.List;

/** Built-in settings holding host types, written in pack JSON as a bare list: {@code "layers": [...]}. */
@Feature(id = "iris:layers", name = "Layers", key = "layers", targets = Level.BIOME)
public final class LayersSettings implements IrisFeature {
    @Desc("Rock layers from the surface down")
    private List<Palette> layers = List.of();

    public List<Palette> layers() {
        return layers;
    }
}
