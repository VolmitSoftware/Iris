/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.decree.DecreeContext;
import com.volmit.iris.util.decree.DecreeNode;
import com.volmit.iris.util.decree.DecreeParameter;
import com.volmit.iris.util.decree.DecreeSystem;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;
import com.volmit.iris.util.decree.exceptions.DecreeWhichException;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;
import lombok.Getter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

@Data
public class VirtualDecreeCommand {
    private final Class<?> type;
    private final VirtualDecreeCommand parent;
    private final KList<VirtualDecreeCommand> nodes;
    private final DecreeNode node;

    private VirtualDecreeCommand(Class<?> type, VirtualDecreeCommand parent, KList<VirtualDecreeCommand> nodes, DecreeNode node)
    {
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

        for(Field i : v.getClass().getDeclaredFields())
        {
            if(Modifier.isStatic(i.getModifiers()) || Modifier.isFinal(i.getModifiers())|| Modifier.isTransient(i.getModifiers())|| Modifier.isVolatile(i.getModifiers()))
            {
                continue;
            }

            if(!i.getType().isAnnotationPresent(Decree.class))
            {
                continue;
            }

            i.setAccessible(true);
            Object childRoot = i.get(v);

            if(childRoot == null)
            {
                i.set(v, i.getType().getConstructor().newInstance());
            }

            c.getNodes().add(createRoot(c, v));
        }

        for(Method i : v.getClass().getDeclaredMethods())
        {
            if(Modifier.isStatic(i.getModifiers()) || Modifier.isFinal(i.getModifiers()) || Modifier.isPrivate(i.getModifiers()))
            {
                continue;
            }

            if(!i.isAnnotationPresent(Decree.class))
            {
                continue;
            }

            c.getNodes().add(new VirtualDecreeCommand(v.getClass(), c, new KList<>(), new DecreeNode(v, i)));
        }

        return c;
    }

    public String getPath()
    {
        KList<String> n = new KList<>();
        VirtualDecreeCommand cursor = this;

        while(cursor.getParent() != null)
        {
            cursor = cursor.getParent();
            n.add(cursor.getName());
        }

        return "/" + n.reverse().qadd(getName()).toString(" ");
    }

    public String getName()
    {
        return isNode() ? getNode().getName() : getType().getDeclaredAnnotation(Decree.class).name();
    }

    public String getDescription()
    {
        return isNode() ? getNode().getDescription() : getType().getDeclaredAnnotation(Decree.class).description();
    }

    public KList<String> getNames()
    {
        if(isNode())
        {
            return getNode().getNames();
        }

        KList<String> d = new KList<>();
        Decree dc = getType().getDeclaredAnnotation(Decree.class);
        for(String i : dc.aliases())
        {
            if(i.isEmpty())
            {
                continue;
            }

            d.add(i);
        }

        d.add(dc.name());
        d.removeDuplicates();

        return d;
    }

    public boolean isNode()
    {
        return node != null;
    }

    private KMap<String, Object> map(VolmitSender sender, KList<String> in)
    {
        KMap<String, Object> data = new KMap<>();

        for(int ix = 0; ix <  in.size(); ix++)
        {
            String i = in.get(ix);
            if(i.contains("="))
            {
                String[] v = i.split("\\Q=\\E");
                String key = v[0];
                String value = v[1];
                DecreeParameter param = null;

                for(DecreeParameter j : getNode().getParameters())
                {
                    for(String k : j.getNames())
                    {
                        if(k.equalsIgnoreCase(key))
                        {
                            param = j;
                            break;
                        }
                    }
                }

                if(param == null)
                {
                    for(DecreeParameter j : getNode().getParameters())
                    {
                        for(String k : j.getNames())
                        {
                            if(k.toLowerCase().contains(key.toLowerCase()) || key.toLowerCase().contains(k.toLowerCase()))
                            {
                                param = j;
                                break;
                            }
                        }
                    }
                }

                if(param == null)
                {
                    Iris.debug("Can't find parameter key for " + key + "=" + value + " in " + getPath());
                    // TODO: WARN UNKNOWN PARAMETER
                    continue;
                }

                key = param.getName();

                try {
                    data.put(key, param.getHandler().parse(value));
                } catch (DecreeParsingException e) {
                    Iris.debug("Can't parse parameter value for " + key + "=" + value + " in " + getPath() + " using handler " + param.getHandler().getClass().getSimpleName());
                    // TODO: WARN BAD PARAM
                    return null;
                } catch (DecreeWhichException e) {
                    KList<?> validOptions = param.getHandler().getPossibilities(value);
                    Iris.debug("Found multiple results for " + key + "=" + value + " in " + getPath() + " using the handler " + param.getHandler().getClass().getSimpleName() + " with potential matches [" + validOptions.toString(",") + "]. Asking client to define one");
                    String update = null; // TODO: PICK ONE
                    Iris.debug("Client chose " + update + " for " + key + "=" + value + " (old) in " + getPath());
                    in.set(ix--, update);
                }
            }

            else
            {
                try
                {
                    DecreeParameter par = getNode().getParameters().get(ix);
                    try {
                        data.put(par.getName(), par.getHandler().parse(i));
                    } catch (DecreeParsingException e) {
                        Iris.debug("Can't parse parameter value for " + par.getNames() + "=" + i + " in " + getPath() + " using handler " + par.getHandler().getClass().getSimpleName());
                        // TODO: WARN BAD PARAM
                        return null;
                    } catch (DecreeWhichException e) {
                        Iris.debug("Can't parse parameter value for " + par.getNames() + "=" + i + " in " + getPath() + " using handler " + par.getHandler().getClass().getSimpleName());
                        KList<?> validOptions = par.getHandler().getPossibilities(i);
                        String update = null;
                        Iris.debug("Client chose " + update + " for " + par.getNames() + "=" + i + " (old) in " + getPath());
                        in.set(ix--, update);
                    }
                }

                catch(ArrayIndexOutOfBoundsException e)
                {
                    // TODO: Ignoring parameter (not in method anywhere
                }
            }
        }

        return data;
    }

    public boolean invoke(VolmitSender sender, KList<String> realArgs)
    {
        return invoke(sender, realArgs, new KList<>());
    }

    public boolean invoke(VolmitSender sender, KList<String> args, KList<Integer> skip)
    {
        Iris.debug("@ " + getPath() + " with " + args.toString(", "));
        if(isNode())
        {
            Iris.debug("Invoke " +getPath() + "(" + args.toString(",") + ") at ");
            if(invokeNode(sender, map(sender, args)))
            {
                return true;
            }

            skip.add(hashCode());
            return false;
        }

        if(args.isEmpty())
        {
            int m = getNodes().size();

            if(getNodes().isNotEmpty())
            {
                for(VirtualDecreeCommand i : getNodes())
                {
                    sender.sendMessage(i.getPath() + " - " + i.getDescription());
                }
            }

            else
            {
                sender.sendMessage(C.RED + "There are no subcommands in this group! Contact support, this is a command design issue!");
            }

            return true;
        }

        String head = args.get(0);
        VirtualDecreeCommand match = matchNode(head, skip);

        if(match != null)
        {
            args.pop();
            return match.invoke(sender, args, skip);
        }

        skip.add(hashCode());

        return false;
    }

    private boolean invokeNode(VolmitSender sender, KMap<String, Object> map) {
        if(map == null)
        {
            return false;
        }

        Object[] params = new Object[getNode().getMethod().getParameterCount()];
        int vm = 0;
        for(DecreeParameter i : getNode().getParameters())
        {
            Object value = map.get(i.getName());

            if(value == null)
            {
                if(i.isRequired())
                {
                    // TODO: REQUIRED... UNDEFINED
                    return false;
                }
            }

            params[vm] = value;
            vm++;
        }

        Runnable rx = () -> {
            try
            {
                DecreeContext.touch(sender);
                getNode().getMethod().setAccessible(true);
                getNode().getMethod().invoke(getNode().getInstance(), params);
            }

            catch(Throwable e)
            {
                e.printStackTrace();
                throw new RuntimeException("Failed to execute <INSERT REAL NODE HERE>"); // TODO:
            }
        };

        if(getNode().isSync())
        {
            J.s(rx);
        }

        else
        {
            rx.run();
        }

        return true;
    }

    public VirtualDecreeCommand matchNode(String in, KList<Integer> skip)
    {
        for(VirtualDecreeCommand i : nodes)
        {
            if(skip.contains(i.hashCode()))
            {
                continue;
            }

            if(i.matches(in))
            {
                return i;
            }
        }

        for(VirtualDecreeCommand i : nodes)
        {
            if(skip.contains(i.hashCode()))
            {
                continue;
            }

            if(i.deepMatches(in))
            {
                return i;
            }
        }

        return null;
    }

    public boolean deepMatches(String in)
    {
        KList<String> a = getNames();

        for(String i : a)
        {
            if(i.toLowerCase().contains(in.toLowerCase()) || in.toLowerCase().contains(i.toLowerCase()))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public int hashCode(){
        return Objects.hash(getName(), getDescription(), getType(), getPath());
    }

    public boolean matches(String in)
    {
        KList<String> a = getNames();

        for(String i : a)
        {
            if(i.equalsIgnoreCase(in))
            {
                return true;
            }
        }

        return false;
    }
}
