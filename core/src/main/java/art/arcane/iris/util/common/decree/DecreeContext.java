package art.arcane.iris.util.decree;

import art.arcane.volmlib.util.decree.context.DecreeContextBase;
import art.arcane.iris.util.plugin.VolmitSender;

public class DecreeContext {
    private static final DecreeContextBase<VolmitSender> context = new DecreeContextBase<>();

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
