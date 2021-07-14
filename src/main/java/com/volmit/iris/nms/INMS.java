package com.volmit.iris.nms;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.nms.v17_1.NMSBinding17_1;
import com.volmit.iris.nms.v1X.NMSBinding1X;
import com.volmit.iris.util.KMap;
import org.bukkit.Bukkit;

public class INMS {
    //@builder
    private static final KMap<String, Class<? extends INMSBinding>> bindings = new KMap<String, Class<? extends INMSBinding>>()
            .qput("v1_17_R1", NMSBinding17_1.class);
    //@done
    private static final INMSBinding binding = bind();

    public static INMSBinding get() {
        return binding;
    }

    public static final String getNMSTag() {
        if (IrisSettings.get().getGeneral().isDisableNMS()) {
            return "BUKKIT";
        }

        try {
            return Bukkit.getServer().getClass().getCanonicalName().split("\\Q.\\E")[3];
        } catch (Throwable e) {
            Iris.error("Failed to determine server nms version!");
            e.printStackTrace();
        }

        return "BUKKIT";
    }

    private static final INMSBinding bind() {
        String code = getNMSTag();
        Iris.info("Locating NMS Binding for " + code);

        if (bindings.containsKey(code)) {
            try {
                INMSBinding b = bindings.get(code).getConstructor().newInstance();
                Iris.info("Craftbukkit " + code + " <-> " + b.getClass().getSimpleName() + " Successfully Bound");

                return b;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        Iris.info("Craftbukkit " + code + " <-> " + NMSBinding1X.class.getSimpleName() + " Successfully Bound");
        Iris.warn("Note: Some features of Iris may not work the same since you are on an unsupported version of Minecraft.");
        Iris.warn("Note: If this is a new version, expect an update soon.");

        return new NMSBinding1X();
    }
}
