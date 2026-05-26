package com.konqasasas.inputvisualizer.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VisualizerSettings {
    public int version = 1;
    public boolean enabled = true;
    public String activePack = "aqua.ivizpack";
    public double globalScale = 1.0;
    public int globalOffsetX = 0;
    public int globalOffsetY = 0;
    public double globalOpacity = 1.0;
    public Map<String, GroupAdjustment> groupAdjustments = new LinkedHashMap<>();
    public void clamp() {
        if (activePack == null || activePack.trim().isEmpty() || activePack.contains("/") || activePack.contains("\\")) activePack = "aqua.ivizpack";
        globalScale = clamp(globalScale, 0.1, 8.0); globalOpacity = clamp(globalOpacity, 0.0, 1.0);
        globalOffsetX = (int)clamp(globalOffsetX, -20000, 20000); globalOffsetY = (int)clamp(globalOffsetY, -20000, 20000);
        if (groupAdjustments == null) groupAdjustments = new LinkedHashMap<>();
        for (GroupAdjustment g : groupAdjustments.values()) if (g != null) g.clamp();
    }
    public GroupAdjustment adjustment(String key) { if (key == null || key.trim().isEmpty()) key = "group"; GroupAdjustment g = groupAdjustments.get(key); if (g == null) { g = new GroupAdjustment(); groupAdjustments.put(key, g); } return g; }
    private static double clamp(double v, double min, double max) { return v < min ? min : (v > max ? max : v); }
    public static final class GroupAdjustment { public double offsetX=0,offsetY=0,scale=1.0,opacity=1.0; public void clamp(){ offsetX=clamp(offsetX,-20000,20000); offsetY=clamp(offsetY,-20000,20000); scale=clamp(scale,.05,20); opacity=clamp(opacity,0,1); } private static double clamp(double v,double min,double max){ return v<min?min:v>max?max:v; } }
}
