package com.volmit.stage;

@FunctionalInterface
public interface BlockCollector<O> {
    void collect(BlockStageContext context, O accumulator);
}
