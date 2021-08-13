package com.volmit.iris.util.decree;

import com.volmit.iris.util.decree.annotations.Decree;

@Decree(name = "boop", aliases = {"b", "bp"}, description = "Sub example with another name!")
// the boop command in here can be called with (/super) boop beep, b beep and bp beep
public class SubExample implements DecreeCommand {
    @Decree(name = "beep", description = "Boops the sender") // Origin is not defined so both console & player senders can run this command (Default)
    public void boop() { // Called with "beep" because name = "beep", "boop" will not work in the command
        DecreeContext.get().sendMessage("Boop");
    }
}
