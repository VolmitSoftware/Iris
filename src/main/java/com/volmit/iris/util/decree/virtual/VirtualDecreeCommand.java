/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.util.decree.virtual;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.decree.*;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.plugin.CommandDummy;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
public class VirtualDecreeCommand {
    private final Class<?> type;
    private final VirtualDecreeCommand parent;
    private final KList<VirtualDecreeCommand> nodes;
    private final DecreeNode node;
    String[] gradients = new String[]{
            "<gradient:#f5bc42:#45b32d>",
            "<gradient:#1ed43f:#1ecbd4>",
            "<gradient:#1e2ad4:#821ed4>",
            "<gradient:#d41ea7:#611ed4>",
            "<gradient:#1ed473:#1e55d4>",
            "<gradient:#6ad41e:#9a1ed4>"
    };
    private ChronoLatch cl = new ChronoLatch(1000);

    private VirtualDecreeCommand(Class<?> type, VirtualDecreeCommand parent, KList<VirtualDecreeCommand> nodes, DecreeNode node) {
        this.parent = parent;
        this.type = type;
        this.nodes = nodes;
        this.node = node;
    }

    public static VirtualDecreeCommand createRoot(Object v) throws Throwable {
        return createRoot(null, v);
    }

    public static VirtualDecreeCommand createRoot(VirtualDecreeCommand parent, Object v) throws Throwable {
        VirtualDecreeCommand c = new VirtualDecreeCommand(v.getClass(), parent, new KList<>(), null);

        for (Field i : v.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(i.getModifiers()) || Modifier.isFinal(i.getModifiers()) || Modifier.isTransient(i.getModifiers()) || Modifier.isVolatile(i.getModifiers())) {
                continue;
            }

            if (!i.getType().isAnnotationPresent(Decree.class)) {
                continue;
            }

            i.setAccessible(true);
            Object childRoot = i.get(v);

            if (childRoot == null) {
                childRoot = i.getType().getConstructor().newInstance();
                i.set(v, childRoot);
            }

            c.getNodes().add(createRoot(c, childRoot));
        }

        for (Method i : v.getClass().getDeclaredMethods()) {
            if (Modifier.isStatic(i.getModifiers()) || Modifier.isFinal(i.getModifiers()) || Modifier.isPrivate(i.getModifiers())) {
                continue;
            }

            if (!i.isAnnotationPresent(Decree.class)) {
                continue;
            }

            c.getNodes().add(new VirtualDecreeCommand(v.getClass(), c, new KList<>(), new DecreeNode(v, i)));
        }

        return c;
    }

    public void cacheAll() {
        VolmitSender sender = new VolmitSender(new CommandDummy());

        if (isNode()) {
            sender.sendDecreeHelpNode(this);
        }

        for (VirtualDecreeCommand j : nodes) {
            j.cacheAll();
        }
    }

    public String getPath() {
        KList<String> n = new KList<>();
        VirtualDecreeCommand cursor = this;

        while (cursor.getParent() != null) {
            cursor = cursor.getParent();
            n.add(cursor.getName());
        }

        return "/" + n.reverse().qadd(getName()).toString(" ");
    }

    public String getParentPath() {
        return getParent().getPath();
    }

    public String getName() {
        return isNode() ? getNode().getName() : getType().getDeclaredAnnotation(Decree.class).name();
    }

    private boolean isStudio() {
        return isNode() ? getNode().getDecree().studio() : getType().getDeclaredAnnotation(Decree.class).studio();
    }

    public String getDescription() {
        return isNode() ? getNode().getDescription() : getType().getDeclaredAnnotation(Decree.class).description();
    }

    public KList<String> getNames() {
        if (isNode()) {
            return getNode().getNames();
        }

        Decree dc = getType().getDeclaredAnnotation(Decree.class);
        KList<String> d = new KList<>();
        d.add(dc.name());
        for (String i : dc.aliases()) {
            if (i.isEmpty()) {
                continue;
            }

            d.add(i);
        }

        d.removeDuplicates();

        return d;
    }

    public boolean isNode() {
        return node != null;
    }

    public KList<String> tabComplete(KList<String> args, String raw) {
        KList<Integer> skip = new KList<>();
        KList<String> tabs = new KList<>();
        invokeTabComplete(args, skip, tabs, raw);
        return tabs;
    }

    private boolean invokeTabComplete(KList<String> args, KList<Integer> skip, KList<String> tabs, String raw) {
        if (isStudio() && !IrisSettings.get().getStudio().isStudio()) {
            return false;
        }

        if (isNode()) {
            tab(args, tabs);
            skip.add(hashCode());
            return false;
        }

        if (args.isEmpty()) {
            tab(args, tabs);
            return true;
        }

        String head = args.get(0);

        if (args.size() > 1 || head.endsWith(" ")) {
            VirtualDecreeCommand match = matchNode(head, skip);

            if (match != null) {
                args.pop();
                return match.invokeTabComplete(args, skip, tabs, raw);
            }

            skip.add(hashCode());
        } else {
            tab(args, tabs);
        }

        return false;
    }

    private void tab(KList<String> args, KList<String> tabs) {
        String last = null;
        KList<DecreeParameter> ignore = new KList<>();
        Runnable la = () -> {

        };
        for (String a : args) {
            la.run();
            last = a;
            la = () -> {
                if (isNode()) {
                    String sea = a.contains("=") ? a.split("\\Q=\\E")[0] : a;
                    sea = sea.trim();

                    searching:
                    for (DecreeParameter i : getNode().getParameters()) {
                        for (String m : i.getNames()) {
                            if (m.equalsIgnoreCase(sea) || m.toLowerCase().contains(sea.toLowerCase()) || sea.toLowerCase().contains(m.toLowerCase())) {
                                ignore.add(i);
                                continue searching;
                            }
                        }
                    }
                }
            };
        }

        if (last != null) {
            if (isNode()) {
                for (DecreeParameter i : getNode().getParameters()) {
                    if (ignore.contains(i)) {
                        continue;
                    }

                    int g = 0;

                    if (last.contains("=")) {
                        String[] vv = last.trim().split("\\Q=\\E");
                        String vx = vv.length == 2 ? vv[1] : "";
                        for (String f : i.getHandler().getPossibilities(vx).convert((v) -> i.getHandler().toStringForce(v))) {
                            g++;
                            tabs.add(i.getName() + "=" + f);
                        }
                    } else {
                        for (String f : i.getHandler().getPossibilities("").convert((v) -> i.getHandler().toStringForce(v))) {
                            g++;
                            tabs.add(i.getName() + "=" + f);
                        }
                    }

                    if (g == 0) {
                        tabs.add(i.getName() + "=");
                    }
                }
            } else {
                for (VirtualDecreeCommand i : getNodes()) {
                    String m = i.getName();
                    if (m.equalsIgnoreCase(last) || m.toLowerCase().contains(last.toLowerCase()) || last.toLowerCase().contains(m.toLowerCase())) {
                        tabs.addAll(i.getNames());
                    }
                }
            }
        }
    }

    /**
     * Maps the input a player typed to the parameters of this command
     *
     * @param sender The sender
     * @param in     The input
     * @return A map of all the parameter names and their values
     */
    private KMap<String, Object> map(VolmitSender sender, KList<String> in) {
        KMap<String, Object> data = new KMap<>();
        KSet<Integer> nowhich = new KSet<>();

        KList<String> unknownInputs = new KList<>(in.stream().filter(s -> !s.contains("=")).collect(Collectors.toList()));
        KList<String> knownInputs = new KList<>(in.stream().filter(s -> s.contains("=")).collect(Collectors.toList()));

        //Loop known inputs
        for (int x = 0; x < knownInputs.size(); x++) {
            String stringParam = knownInputs.get(x);
            int original = in.indexOf(stringParam);

            String[] v = stringParam.split("\\Q=\\E");
            String key = v[0];
            String value = v[1];
            DecreeParameter param = null;

            //Find decree parameter from string param
            for (DecreeParameter j : getNode().getParameters()) {
                for (String k : j.getNames()) {
                    if (k.equalsIgnoreCase(key)) {
                        param = j;
                        break;
                    }
                }
            }

            //If it failed, see if we can find it by checking if the names contain the param
            if (param == null) {
                for (DecreeParameter j : getNode().getParameters()) {
                    for (String k : j.getNames()) {
                        if (k.toLowerCase().contains(key.toLowerCase()) || key.toLowerCase().contains(k.toLowerCase())) {
                            param = j;
                            break;
                        }
                    }
                }
            }

            //Still failed to find, error them
            if (param == null) {
                Iris.debug("Can't find parameter key for " + key + "=" + value + " in " + getPath());
                sender.sendMessage(C.YELLOW + "Unknown Parameter: " + key);
                unknownInputs.add(value); //Add the value to the unknowns and see if we can assume it later
                continue;
            }

            key = param.getName();

            try {
                data.put(key, param.getHandler().parse(value, nowhich.contains(original))); //Parse and put
            } catch (DecreeParsingException e) {
                Iris.debug("Can't parse parameter value for " + key + "=" + value + " in " + getPath() + " using handler " + param.getHandler().getClass().getSimpleName());
                sender.sendMessage(C.RED + "Cannot convert \"" + value + "\" into a " + param.getType().getSimpleName());
                e.printStackTrace();
                return null;
            }
        }

        //Make a list of decree params that haven't been identified
        KList<DecreeParameter> decreeParameters = new KList<>(getNode().getParameters().stream().filter(param -> !data.contains(param.getName())).collect(Collectors.toList()));

        //Loop Unknown inputs
        for (int x = 0; x < unknownInputs.size(); x++) {
            String stringParam = unknownInputs.get(x);
            int original = in.indexOf(stringParam);
            try {
                DecreeParameter par = decreeParameters.get(x);

                try {
                    data.put(par.getName(), par.getHandler().parse(stringParam, nowhich.contains(original)));
                } catch (DecreeParsingException e) {
                    Iris.debug("Can't parse parameter value for " + par.getName() + "=" + stringParam + " in " + getPath() + " using handler " + par.getHandler().getClass().getSimpleName());
                    sender.sendMessage(C.RED + "Cannot convert \"" + stringParam + "\" into a " + par.getType().getSimpleName());
                    e.printStackTrace();
                    return null;
                }
            } catch (IndexOutOfBoundsException e) {
                sender.sendMessage(C.YELLOW + "Unknown Parameter: " + stringParam + " (" + Form.getNumberSuffixThStRd(x + 1) + " argument)");
            }
        }

        return data;
    }

    public boolean invoke(VolmitSender sender, KList<String> realArgs) {
        return invoke(sender, realArgs, new KList<>());
    }

    public boolean invoke(VolmitSender sender, KList<String> args, KList<Integer> skip) {
        if (isStudio() && !IrisSettings.get().getStudio().isStudio()) {
            sender.sendMessage(C.RED + "To use Iris Studio Commands, please enable studio in Iris/settings.json (settings auto-reload)");
            return false;
        }

        DecreeOrigin origin = type.getDeclaredAnnotation(Decree.class).origin();
        if (!origin.validFor(sender)) {
            sender.sendMessage(C.RED + "This command has to be sent from another origin: " + C.GOLD + origin);
            return false;
        }

        Iris.debug("@ " + getPath() + " with " + args.toString(", "));
        if (isNode()) {
            Iris.debug("Invoke " + getPath() + "(" + args.toString(",") + ") at ");
            if (invokeNode(sender, map(sender, args))) {
                return true;
            }

            skip.add(hashCode());
            return false;
        }

        if (args.isEmpty()) {
            sender.sendDecreeHelp(this);

            return true;
        } else if (args.size() == 1) {
            for (String i : args) {
                if (i.startsWith("help=")) {
                    sender.sendDecreeHelp(this, Integer.parseInt(i.split("\\Q=\\E")[1]) - 1);
                    return true;
                }
            }
        }

        String head = args.get(0);
        VirtualDecreeCommand match = matchNode(head, skip);

        if (match != null) {
            args.pop();
            return match.invoke(sender, args, skip);
        }

        skip.add(hashCode());

        return false;
    }

    private boolean invokeNode(VolmitSender sender, KMap<String, Object> map) {
        if (map == null) {
            return false;
        }

        Object[] params = new Object[getNode().getMethod().getParameterCount()];
        int vm = 0;
        for (DecreeParameter i : getNode().getParameters()) {
            Object value = map.get(i.getName());

            try {
                if (value == null && i.hasDefault()) {
                    value = i.getDefaultValue();
                }
            } catch (DecreeParsingException e) {
                Iris.debug("Can't parse parameter value for " + i.getName() + "=" + i.getParam().defaultValue() + " in " + getPath() + " using handler " + i.getHandler().getClass().getSimpleName());
                sender.sendMessage(C.RED + "Cannot convert \"" + i.getParam().defaultValue() + "\" into a " + i.getType().getSimpleName());
                return false;
            }

            if (sender.isPlayer() && i.isContextual() && value == null) {
                Iris.debug("Contextual!");
                DecreeContextHandler<?> ch = DecreeContextHandler.contextHandlers.get(i.getType());

                if (ch != null) {
                    value = ch.handle(sender);

                    if (value != null) {
                        Iris.debug("Parameter \"" + i.getName() + "\" derived a value of \"" + i.getHandler().toStringForce(value) + "\" from " + ch.getClass().getSimpleName());
                    } else {
                        Iris.debug("Parameter \"" + i.getName() + "\" could not derive a value from \"" + ch.getClass().getSimpleName());
                    }
                } else {
                    Iris.debug("Parameter \"" + i.getName() + "\" is contextual but has no context handler for \"" + i.getType().getCanonicalName() + "\"");
                }
            }

            if (i.hasDefault() && value == null) {
                try {
                    Iris.debug("Parameter \"" + i.getName() + "\" is using default value \"" + i.getParam().defaultValue() + "\"");
                    value = i.getDefaultValue();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }

            if (i.isRequired() && value == null) {
                sender.sendMessage(C.RED + "Missing argument \"" + i.getName() + "\" (" + i.getType().getSimpleName() + ") as the " + Form.getNumberSuffixThStRd(vm + 1) + " argument.");
                sender.sendDecreeHelpNode(this);
                return false;
            }

            params[vm] = value;
            vm++;
        }

        DecreeContext.touch(sender);
        Runnable rx = () -> {
            try {
                DecreeContext.touch(sender);
                getNode().getMethod().setAccessible(true);
                getNode().getMethod().invoke(getNode().getInstance(), params);
            } catch (Throwable e) {
                e.printStackTrace();
                throw new RuntimeException("Failed to execute <INSERT REAL NODE HERE>"); // TODO:
            }
        };

        if (getNode().isSync()) {
            J.s(rx);
        } else {
            rx.run();
        }

        return true;
    }

    public KList<VirtualDecreeCommand> matchAllNodes(String in) {
        KList<VirtualDecreeCommand> g = new KList<>();

        if (in.trim().isEmpty()) {
            g.addAll(nodes);
            return g;
        }

        for (VirtualDecreeCommand i : nodes) {
            if (i.matches(in)) {
                g.add(i);
            }
        }

        for (VirtualDecreeCommand i : nodes) {
            if (i.deepMatches(in)) {
                g.add(i);
            }
        }

        g.removeDuplicates();
        return g;
    }

    public VirtualDecreeCommand matchNode(String in, KList<Integer> skip) {
        if (in.trim().isEmpty()) {
            return null;
        }

        for (VirtualDecreeCommand i : nodes) {
            if (skip.contains(i.hashCode())) {
                continue;
            }

            if (i.matches(in)) {
                return i;
            }
        }

        for (VirtualDecreeCommand i : nodes) {
            if (skip.contains(i.hashCode())) {
                continue;
            }

            if (i.deepMatches(in)) {
                return i;
            }
        }

        return null;
    }

    public boolean deepMatches(String in) {
        KList<String> a = getNames();

        for (String i : a) {
            if (i.toLowerCase().contains(in.toLowerCase()) || in.toLowerCase().contains(i.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getDescription(), getType(), getPath());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof VirtualDecreeCommand)) {
            return false;
        }
        return this.hashCode() == obj.hashCode();
    }

    public boolean matches(String in) {
        KList<String> a = getNames();

        for (String i : a) {
            if (i.equalsIgnoreCase(in)) {
                return true;
            }
        }

        return false;
    }
}
