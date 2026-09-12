package art.arcane.iris.client.mixin;

import art.arcane.iris.modded.localization.ClientUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedMixinFlags;
import com.mojang.serialization.Lifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * CLIENT DIST ONLY. Registered from irisworldgen.client.mixins.json, whose "client" block already restricts
 * application to the client dist. Target verified against MC 26.2:
 * {@code WorldOpenFlows.confirmWorldCreation(Minecraft, CreateWorldScreen, Lifecycle, Runnable, boolean)} is
 * static, and {@code openWorldCheckWorldStemCompatibility} is a private instance method.
 */
@Mixin(WorldOpenFlows.class)
public abstract class IrisWorldOpenFlowsMixin {
    @Invoker("openWorldLoadBundledResourcePack")
    protected abstract void iris$openWorldLoadBundledResourcePack(
            LevelStorageSource.LevelStorageAccess worldAccess,
            WorldStem worldStem,
            PackRepository packRepository,
            Runnable onCancel);

    @Inject(method = "confirmWorldCreation", at = @At("HEAD"), cancellable = true)
    private static void iris$confirmWorldCreation(
            Minecraft minecraft,
            CreateWorldScreen parent,
            Lifecycle lifecycle,
            Runnable task,
            boolean skipWarning,
            CallbackInfo info) {
        ModdedMixinFlags.markWorldOpenFlows();
        if (!iris$selectedPresetIsIris(parent)) {
            return;
        }
        if (!parent.getUiState().isGenerateStructures()) {
            // Iris runs its own placement through the vanilla structure step, so a world created with
            // Generate Structures off is refused at load by IrisModdedChunkGenerator. Stop it here, at the
            // one screen that still has a toggle to fix, instead of at the load that would only report it.
            iris$showStructuresRequired(minecraft, parent);
            info.cancel();
            return;
        }
        if (skipWarning || lifecycle == Lifecycle.stable()) {
            return;
        }
        task.run();
        info.cancel();
    }

    private static void iris$showStructuresRequired(Minecraft minecraft, CreateWorldScreen parent) {
        // 5-arg ctor with shouldCloseOnEsc=false: ESC on the 3-arg ctor closes to the title screen,
        // skipping CreateWorldScreen.onClose and leaking its temp datapack directory.
        minecraft.gui.setScreen(new AlertScreen(
                () -> minecraft.gui.setScreen(parent),
                Component.literal(IrisLanguage.plain(ClientUiMessages.CREATE_STRUCTURES_REQUIRED_TITLE)),
                Component.literal(IrisLanguage.plain(ClientUiMessages.CREATE_STRUCTURES_REQUIRED_BODY)),
                CommonComponents.GUI_BACK,
                false));
    }

    @Inject(method = "openWorldCheckWorldStemCompatibility", at = @At("HEAD"), cancellable = true)
    private void iris$openWorldCheckWorldStemCompatibility(
            LevelStorageSource.LevelStorageAccess worldAccess,
            WorldStem worldStem,
            PackRepository packRepository,
            Runnable onCancel,
            CallbackInfo info) {
        ModdedMixinFlags.markWorldOpenFlows();
        if (!iris$containsIrisGenerator(worldStem)) {
            return;
        }
        iris$openWorldLoadBundledResourcePack(worldAccess, worldStem, packRepository, onCancel);
        info.cancel();
    }

    private static boolean iris$selectedPresetIsIris(CreateWorldScreen parent) {
        WorldCreationUiState.WorldTypeEntry worldType = parent.getUiState().getWorldType();
        Holder<WorldPreset> preset = worldType.preset();
        if (preset == null) {
            return false;
        }
        Optional<ResourceKey<WorldPreset>> key = preset.unwrapKey();
        return key.isPresent() && "irisworldgen".equals(key.get().identifier().getNamespace());
    }

    private static boolean iris$containsIrisGenerator(WorldStem worldStem) {
        Registry<LevelStem> dimensions = worldStem.registries()
                .compositeAccess()
                .lookupOrThrow(Registries.LEVEL_STEM);
        for (LevelStem dimension : dimensions) {
            if (dimension.generator() instanceof IrisModdedChunkGenerator) {
                return true;
            }
        }
        return false;
    }
}
