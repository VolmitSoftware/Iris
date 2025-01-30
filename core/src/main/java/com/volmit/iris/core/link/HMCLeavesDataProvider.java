package com.volmit.iris.core.link;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.IrisCustomData;
import com.volmit.iris.util.reflect.WrappedField;
import com.volmit.iris.util.reflect.WrappedReturningMethod;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.MissingResourceException;
import java.util.function.Supplier;

public class HMCLeavesDataProvider extends ExternalDataProvider {
	private Object apiInstance;
	private WrappedReturningMethod<Object, Material> worldBlockType;
	private WrappedReturningMethod<Object, Boolean> setCustomBlock;
	private Map<String, Object> blockDataMap = Map.of();
	private Map<String, Supplier<ItemStack>> itemDataField = Map.of();

	public HMCLeavesDataProvider() {
		super("HMCLeaves");
	}

	@Override
	public String getPluginId() {
		return "HMCLeaves";
	}

	@Override
	public void init() {
		try {
			worldBlockType = new WrappedReturningMethod<>((Class<Object>) Class.forName("io.github.fisher2911.hmcleaves.data.BlockData"), "worldBlockType");
			apiInstance = getApiInstance(Class.forName("io.github.fisher2911.hmcleaves.api.HMCLeavesAPI"));
			setCustomBlock = new WrappedReturningMethod<>((Class<Object>) apiInstance.getClass(), "setCustomBlock", Location.class, String.class, boolean.class);
			Object config = getLeavesConfig(apiInstance.getClass());
			blockDataMap = getMap(config, "blockDataMap");
			itemDataField = getMap(config, "itemSupplierMap");
		} catch (Throwable e) {
			Iris.error("Failed to initialize HMCLeavesDataProvider: " + e.getMessage());
		}
	}

	@NotNull
	@Override
	public BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
		Object o = blockDataMap.get(blockId.key());
		if (o == null)
			throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
		Material material = worldBlockType.invoke(o, new Object[0]);
		if (material == null)
			throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
		BlockData blockData = Bukkit.createBlockData(material);
		if (IrisSettings.get().getGenerator().preventLeafDecay && blockData instanceof Leaves leaves)
			leaves.setPersistent(true);
		return new IrisCustomData(blockData, ExternalDataSVC.buildState(blockId, state));
	}

	@NotNull
	@Override
	public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
		if (!itemDataField.containsKey(itemId.key()))
			throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
		return itemDataField.get(itemId.key()).get();
	}

	@Override
	public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
		var pair = ExternalDataSVC.parseState(blockId);
		blockId = pair.getA();
		Boolean result = setCustomBlock.invoke(apiInstance, new Object[]{block.getLocation(), blockId.key(), false});
		if (result == null || !result)
			Iris.warn("Failed to set custom block! " + blockId.key() + " " + block.getX() + " " + block.getY() + " " + block.getZ());
		else if (IrisSettings.get().getGenerator().preventLeafDecay) {
			BlockData blockData = block.getBlockData();
			if (blockData instanceof Leaves leaves)
				leaves.setPersistent(true);
		}
	}

	@NotNull
	@Override
	public Identifier[] getBlockTypes() {
		KList<Identifier> names = new KList<>();
		for (String name : blockDataMap.keySet()) {
			try {
				Identifier key = new Identifier("hmcleaves", name);
				if (getBlockData(key) != null)
					names.add(key);
			} catch (MissingResourceException ignored) {
			}
		}

		return names.toArray(new Identifier[0]);
	}

	@NotNull
	@Override
	public Identifier[] getItemTypes() {
		KList<Identifier> names = new KList<>();
		for (String name : itemDataField.keySet()) {
			try {
				Identifier key = new Identifier("hmcleaves", name);
				if (getItemStack(key) != null)
					names.add(key);
			} catch (MissingResourceException ignored) {
			}
		}

		return names.toArray(new Identifier[0]);
	}

	@Override
	public boolean isValidProvider(@NotNull Identifier id, boolean isItem) {
		return (isItem ? itemDataField.keySet() : blockDataMap.keySet()).contains(id.key());
	}

	private <C, T> Map<String, T> getMap(C config, String name) {
		WrappedField<C, Map<String, T>> field = new WrappedField<>((Class<C>) config.getClass(), name);
		return field.get(config);
	}

	private <A> A getApiInstance(Class<A> apiClass) {
		WrappedReturningMethod<A, A> instance = new WrappedReturningMethod<>(apiClass, "getInstance");
		return instance.invoke();
	}

	private <A, C> C getLeavesConfig(Class<A> apiClass) {
		WrappedReturningMethod<A, A> instance = new WrappedReturningMethod<>(apiClass, "getInstance");
		WrappedField<A, C> config = new WrappedField<>(apiClass, "config");
		return config.get(instance.invoke());
	}
}
