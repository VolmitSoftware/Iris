package com.volmit.host;

import com.volmit.tooling.Command;
import java.util.Objects;

/** A root command and the module that registered it, which the host names when two modules claim one root name and
 * which owns the log and the failures of the command's actions.
 */
public record CommandRegistration(Command command, DiscoveredModule module) {
    public CommandRegistration {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(module, "module");
    }

    @Override
    public String toString() {
        return command + " of module " + module;
    }
}
