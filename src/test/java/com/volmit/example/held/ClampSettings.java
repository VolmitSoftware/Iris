package com.volmit.example.held;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Snippet;

@Snippet("clamp")
@Desc("Limits a placement by height")
public final class ClampSettings {
    @MinNumber(-2048)
    @MaxNumber(2048)
    @Desc("The lowest height")
    private int minimumHeight = -2048;

    public int minimumHeight() {
        return minimumHeight;
    }
}
