package com.konqasasas.inputvisualizer.pack;
import com.google.gson.JsonObject; import com.konqasasas.inputvisualizer.model.Model; import java.util.*;
public final class VisualizerPack { public final String fileName; public final JsonObject bundle; public final Model.Layout layout; public final Map<String, LoadedPackTexture> textures; public VisualizerPack(String f, JsonObject b, Model.Layout l, Map<String,LoadedPackTexture> t){fileName=f;bundle=b;layout=l;textures=t;} }
