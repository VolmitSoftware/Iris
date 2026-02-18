package art.arcane.iris.util.common.director;

import art.arcane.volmlib.util.director.context.DirectorContextBase;
import art.arcane.iris.util.common.plugin.VolmitSender;

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
