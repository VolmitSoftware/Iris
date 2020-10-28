package com.volmit.iris.gen.v2.scaffold.hunk.io;

import com.volmit.iris.util.CompoundTag;
import lombok.Getter;
import org.bukkit.block.data.BlockData;

import java.io.File;

public class HunkCompoundRegion extends HunkRegion {

    @Getter
    private HunkRegionSlice<BlockData> parallaxSlice;
    @Getter
    private HunkRegionSlice<String> objectSlice;
    @Getter
    private HunkRegionSlice<Boolean> updateSlice;

    private final int height;

    public HunkCompoundRegion(int height, File folder, int x, int z, CompoundTag compound) {
        super(folder, x, z, compound);
        this.height = height;
        setupSlices();
    }

    public HunkCompoundRegion(int height, File folder, int x, int z) {
        super(folder, x, z);
        this.height = height;
        setupSlices();
    }

    private void setupSlices() {
        parallaxSlice = HunkRegionSlice.BLOCKDATA.apply(height, getCompound());
        objectSlice = HunkRegionSlice.STRING.apply(height, getCompound(), "objects");
        updateSlice = HunkRegionSlice.BOOLEAN.apply(height, getCompound(), "updates");
    }
}
