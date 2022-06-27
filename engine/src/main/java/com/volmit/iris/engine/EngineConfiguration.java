package com.volmit.iris.engine;

import com.volmit.iris.platform.PlatformWorld;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EngineConfiguration {
    @Builder.Default
    private boolean mutable = false;

    @Builder.Default
    private boolean timings = false;

    @Builder.Default
    private int threads = 4;
}
