package com.volmit.iris.engine;

import com.volmit.iris.platform.PlatformNamespaceKey;
import com.volmit.iris.platform.PlatformWorld;
import com.volmit.iris.util.NSK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EngineConfiguration {
    @Builder.Default
    private int chunkSize = 16;

    @Builder.Default
    private boolean mutable = false;

    @Builder.Default
    private boolean timings = false;

    @Builder.Default
    private int threads = Runtime.getRuntime().availableProcessors();

    @Builder.Default
    private int threadPriority = 3;

    @Builder.Default
    private PlatformNamespaceKey dimension = new NSK("overworld", "main");

    public EngineConfiguration validate() throws IOException {
        validateChunkSize();
        return this;
    }

    private void validateChunkSize() throws IOException {
        if(Arrays.binarySearch(allowedChunkSizes, chunkSize) < 0) {
            throw new IOException("Invalid Chunk Size: " + chunkSize + " Allowed Chunk Sizes are: " + Arrays.toString(allowedChunkSizes));
        }
    }

    private static final int[] allowedChunkSizes;

    static {
        allowedChunkSizes = new int[16];

        for(int i = 0; i < allowedChunkSizes.length; i++) {
            allowedChunkSizes[i] = (int) Math.pow(2, i+1);
        }

        Arrays.sort(allowedChunkSizes); // for binary sorting
    }
}
