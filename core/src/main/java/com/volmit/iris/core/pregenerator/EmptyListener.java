package com.volmit.iris.core.pregenerator;

public final class EmptyListener implements PregenListener {
    public static final PregenListener INSTANCE = new EmptyListener();

    private EmptyListener() {}

    @Override
    public void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, int generated, int totalChunks, int chunksRemaining, long eta, long elapsed, String method) {

    }

    @Override
    public void onChunkGenerating(int x, int z) {

    }

    @Override
    public void onChunkGenerated(int x, int z) {

    }

    @Override
    public void onRegionGenerated(int x, int z) {

    }

    @Override
    public void onRegionGenerating(int x, int z) {

    }

    @Override
    public void onChunkCleaned(int x, int z) {

    }

    @Override
    public void onRegionSkipped(int x, int z) {

    }

    @Override
    public void onNetworkStarted(int x, int z) {

    }

    @Override
    public void onNetworkFailed(int x, int z) {

    }

    @Override
    public void onNetworkReclaim(int revert) {

    }

    @Override
    public void onNetworkGeneratedChunk(int x, int z) {

    }

    @Override
    public void onNetworkDownloaded(int x, int z) {

    }

    @Override
    public void onClose() {

    }

    @Override
    public void onSaving() {

    }

    @Override
    public void onChunkExistsInRegionGen(int x, int z) {

    }
}
