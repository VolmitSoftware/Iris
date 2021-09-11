package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.plugin.IrisService;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldLoadEvent;

public class EndSVC implements IrisService {
    @Override
    public void onEnable() {
        for (int i = 0; i < 100; i++) {
            Iris.info("Boop!");
        }
    }

    @Override
    public void onDisable() {

    }

//    try {
//        Class.forName("net.pl3x.purpur.PurpurConfig");
//    } catch (ClassNotFoundException e) {
//        System.out.println("Not purpur motherfucker, upgrade now or else!")
//    }
    // TODO: Only load this SVC when this exists. We also need to add logic to IrisDimension#

    @EventHandler
    public void on(PurpurDragonFightEvent e) { // TODO IDK what the event will be called yet

        if (!e.getWorld().getEnvironment().equals(World.Environment.THE_END)) {
            System.out.println("Non-end world loaded");
            return;
        }

        if (!IrisToolbelt.isIrisWorld(e.getWorld())) {
            Iris.debug("Non-iris end-world loaded");
        }

        PlatformChunkGenerator gen = IrisToolbelt.access(e.getWorld());
        if (gen == null) {
            Iris.debug("Newly loaded end-world has no Accessible PlatformChunkGen");
            return;
        }

        if (!gen.getEngine().getDimension().getEndSettings().isEnableDragonFight()) {
            Iris.debug("Dragon fight in newly loaded end-world not disabled");
            return;
        }

        DragonBattle battle = e.getWorld().getEnderDragonBattle();

        if (battle == null) {
            System.out.println("Supposed to cancel dragon fight in end-world but dragon battle is null");
            return;
        }

        System.out.println("Cancelling dragon fight");

        battle.generateEndPortal(false);
        battle.getBossBar().removeAll();
        battle.getBossBar().setVisible(false);
        EnderDragon dragon = battle.getEnderDragon();
        if (dragon != null) {
            dragon.remove();
        }
    }

    // TODO: implement the same thing for the end-gateways

    // TODO: see https://github.com/pl3xgaming/Purpur/discussions/653
}
