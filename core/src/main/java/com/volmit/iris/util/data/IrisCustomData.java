package com.volmit.iris.util.data;

import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.util.collection.KMap;
import lombok.NonNull;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Proxy;
import java.util.*;

public interface IrisCustomData extends BlockData {
	@NonNull BlockData getBase();
	@NonNull Identifier getCustom();

	static IrisCustomData of(@NotNull BlockData base, @NotNull Identifier custom) {
		var clazz = base.getClass();
		var loader = clazz.getClassLoader();
		return (IrisCustomData) Proxy.newProxyInstance(loader, Internal.getInterfaces(loader, clazz), (proxy, method, args) ->
				switch (method.getName()) {
					case "getBase" -> base;
					case "getCustom" -> custom;
					case "merge" -> of(base.merge((BlockData) args[0]), custom);
					case "clone" -> of(base.clone(), custom);
					case "hashCode" -> Objects.hash(base, custom);
                    case "copyTo" -> throw new UnsupportedOperationException("Cannot copy from custom block data");
					case "matches" -> {
						if (!(args[0] instanceof IrisCustomData store))
							yield false;
						yield base.matches(store.getBase()) && custom.equals(store.getCustom());
					}
					case "equals" -> {
						if (!(args[0] instanceof IrisCustomData store))
							yield false;
						yield store.getBase().equals(base) && store.getCustom().equals(custom);
					}
					default -> method.invoke(base, args);
				});
	}

	@ApiStatus.Internal
	abstract class Internal {
		private static final KMap<Class<?>, Class<?>[]> cache = new KMap<>();

		private static Class<?>[] getInterfaces(ClassLoader loader, Class<?> base) {
			return cache.computeIfAbsent(base, k -> {
				Queue<Class<?>> queue = new LinkedList<>();
				Set<Class<?>> set = new HashSet<>();

				queue.add(k);
				while (!queue.isEmpty()) {
					Class<?> i = queue.poll();

					if (!BlockData.class.isAssignableFrom(i))
						continue;

					for (Class<?> j : i.getInterfaces()) {
						if (j.isSealed() || j.isHidden())
							continue;

						try {
							Class.forName(j.getName(), false, loader);
							set.add(j);
						} catch (ClassNotFoundException ignored) {}
					}

					var parent = i.getSuperclass();
					if (parent != null)
						queue.add(parent);
				}

				set.add(IrisCustomData.class);
				return set.toArray(Class<?>[]::new);
			});
		}
	}
}
