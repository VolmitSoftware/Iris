package com.volmit.example.hosttypes;

import java.util.List;

/** Stands for a host class such as Iris's material palette: the host describes it in studio itself. */
public final class Palette {
    private List<String> blocks = List.of("minecraft:stone");
    private Object anything;

    public List<String> blocks() {
        return blocks;
    }
}
