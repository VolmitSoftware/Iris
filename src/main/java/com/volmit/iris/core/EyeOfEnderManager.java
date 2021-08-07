package com.volmit.iris.core;

import com.volmit.iris.util.collection.KList;
import lombok.Getter;
import org.bukkit.entity.EnderSignal;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

public class EyeOfEnderManager implements Listener {

    @Getter
    public static final KList<EnderSignal> managedEyes = new KList<>();

    @EventHandler
    public void on(EntitySpawnEvent e){

        if (e.isCancelled()){
            return;
        }

        if (!(e.getEntity() instanceof EnderSignal entity)){
            return;
        }

        // entity.setTargetLocation(Iris.locationManager.getNearest(StructureType.STRONGHOLD));
    }
}
