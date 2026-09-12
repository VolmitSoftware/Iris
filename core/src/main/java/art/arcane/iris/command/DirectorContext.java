package art.arcane.iris.command;

import art.arcane.volmlib.util.director.context.DirectorContextBase;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;

public class DirectorContext {
    private static final DirectorContextBase<VolmitSender> context = new DirectorContextBase<>();

    public static VolmitSender get() {
        return context.get();
    }

    public static void touch(VolmitSender c) {
        context.touch(c);
    }

    public static void remove() {
        context.remove();
    }
}
