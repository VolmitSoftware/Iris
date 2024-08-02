package com.volmit.iris.core.nms.container;

import java.util.Collection;

public interface IPackRepository {
    void reload();
    void reloadWorldData();
    void setSelected(Collection<String> packs);
    boolean addPack(String packId);
    boolean removePack(String packId);
    Collection<String> getAvailableIds();
    Collection<String> getSelectedIds();
    boolean isAvailable(String packId);
}
