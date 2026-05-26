package com.konqasasas.inputvisualizer.client;

import com.konqasasas.inputvisualizer.config.VisualizerPaths;
import com.konqasasas.inputvisualizer.config.VisualizerSettingsManager;
import com.konqasasas.inputvisualizer.pack.VisualizerPackManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class InputVisualizerClient {
    private static final Logger LOG = LogManager.getLogger("InputVisualizer");
    public static KeyBinding openSettingsKey;
    public static VisualizerPaths paths;
    public static VisualizerSettingsManager settingsManager;
    public static VisualizerPackManager packManager;
    public static InputVisualizerEvents events;

    public void preInit(FMLPreInitializationEvent event) {
        LOG.warn("Input Visualizer client preInit start; configDir=" + event.getModConfigurationDirectory().getAbsolutePath());
        paths = new VisualizerPaths(event.getModConfigurationDirectory());
        settingsManager = new VisualizerSettingsManager(paths);
        packManager = new VisualizerPackManager(paths, settingsManager);
        paths.ensureDirectories();
        settingsManager.loadOrCreate();
        packManager.ensureDefaultPack();
        packManager.loadAtStartup();
        LOG.warn("Input Visualizer client preInit done; minecraftDir=" + paths.minecraftDir.getAbsolutePath() + ", packDir=" + paths.packDir.getAbsolutePath() + ", settingsFile=" + paths.settingsFile.getAbsolutePath() + ", active=" + (packManager.active() == null ? "null" : packManager.active().fileName));
    }

    public void init(FMLInitializationEvent event) {
        openSettingsKey = new KeyBinding("key.inputvisualizer.open_settings", Keyboard.KEY_NONE, "key.categories.inputvisualizer");
        ClientRegistry.registerKeyBinding(openSettingsKey);
        events = new InputVisualizerEvents(packManager, settingsManager);
        MinecraftForge.EVENT_BUS.register(events);
        LOG.warn("Input Visualizer event handlers registered");
    }
}
