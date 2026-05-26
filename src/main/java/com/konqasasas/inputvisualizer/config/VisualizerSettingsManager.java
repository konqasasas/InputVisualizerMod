package com.konqasasas.inputvisualizer.config;

import com.google.gson.Gson; import com.google.gson.GsonBuilder;
import java.io.*; import java.nio.charset.StandardCharsets;

public final class VisualizerSettingsManager {
    private final VisualizerPaths paths; private final Gson gson = new GsonBuilder().setPrettyPrinting().create(); private VisualizerSettings settings = new VisualizerSettings();
    public VisualizerSettingsManager(VisualizerPaths paths) { this.paths = paths; }
    public VisualizerSettings get() { return settings; }
    public void loadOrCreate() { paths.ensureDirectories(); if (!paths.settingsFile.isFile()) { settings = new VisualizerSettings(); save(); return; } try (Reader r = new InputStreamReader(new FileInputStream(paths.settingsFile), StandardCharsets.UTF_8)) { VisualizerSettings s = gson.fromJson(r, VisualizerSettings.class); settings = s == null ? new VisualizerSettings() : s; settings.clamp(); save(); } catch (Exception e) { settings = new VisualizerSettings(); save(); } }
    public void save() { try { paths.ensureDirectories(); settings.clamp(); try (Writer w = new OutputStreamWriter(new FileOutputStream(paths.settingsFile), StandardCharsets.UTF_8)) { gson.toJson(settings, w); } } catch (Exception ignored) {} }
}
