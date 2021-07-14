package com.volmit.iris.util;

import lombok.Builder;
import lombok.Getter;

@DontObfuscate
@Getter
@Builder
public class BoardSettings {
    @DontObfuscate
    private final BoardProvider boardProvider;

    @DontObfuscate
    private final ScoreDirection scoreDirection;
}
