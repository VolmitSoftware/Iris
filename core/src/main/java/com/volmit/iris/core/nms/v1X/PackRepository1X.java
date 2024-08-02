package com.volmit.iris.core.nms.v1X;

import com.volmit.iris.core.nms.container.IPackRepository;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

class PackRepository1X implements IPackRepository {
    @Override
    public void reload() {}

    @Override
    public void reloadWorldData() {}

    @Override
    public void setSelected(Collection<String> packs) {}

    @Override
    public boolean addPack(String packId) {
        return false;
    }

    @Override
    public boolean removePack(String packId) {
        return false;
    }

    @Override
    public Collection<String> getAvailableIds() {
        return List.of();
    }

    @Override
    public Collection<String> getSelectedIds() {
        return List.of();
    }

    @Override
    public boolean isAvailable(String packId) {
        return false;
    }
}