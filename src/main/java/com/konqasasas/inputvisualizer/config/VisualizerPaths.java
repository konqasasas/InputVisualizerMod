package com.konqasasas.inputvisualizer.config;

import java.io.File;

public final class VisualizerPaths {
    public final File minecraftDir;
    public final File packDir;
    public final File configDir;
    public final File settingsFile;
    public VisualizerPaths(File forgeConfigDir) {
        this.minecraftDir = forgeConfigDir.getParentFile();
        this.packDir = new File(minecraftDir, "input_visualizer/packs");
        this.configDir = new File(forgeConfigDir, "input_visualizer");
        this.settingsFile = new File(configDir, "settings.json");
    }
    public void ensureDirectories() { if (!packDir.isDirectory()) packDir.mkdirs(); if (!configDir.isDirectory()) configDir.mkdirs(); }
}
