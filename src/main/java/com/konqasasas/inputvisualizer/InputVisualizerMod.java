package com.konqasasas.inputvisualizer;

import com.konqasasas.inputvisualizer.client.InputVisualizerClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = InputVisualizerMod.MODID, name = InputVisualizerMod.NAME, version = InputVisualizerMod.VERSION, clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]")
public final class InputVisualizerMod {
    public static final String MODID = "inputvisualizer";
    public static final String NAME = "Input Visualizer";
    public static final String VERSION = "1.0.0";
    private static final Logger LOG = LogManager.getLogger("InputVisualizer");

    @SidedProxy(clientSide = "com.konqasasas.inputvisualizer.client.InputVisualizerClient", serverSide = "com.konqasasas.inputvisualizer.client.InputVisualizerClient")
    public static InputVisualizerClient client;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent e) { LOG.warn("Input Visualizer preInit reached"); client.preInit(e); }
    @Mod.EventHandler public void init(FMLInitializationEvent e) { LOG.warn("Input Visualizer init reached"); client.init(e); }
}
