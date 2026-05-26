package com.konqasasas.inputvisualizer.client;

import com.konqasasas.inputvisualizer.config.VisualizerSettingsManager;
import com.konqasasas.inputvisualizer.gui.GuiInputVisualizerSettings;
import com.konqasasas.inputvisualizer.pack.VisualizerPackManager;
import com.konqasasas.inputvisualizer.render.LayoutRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.gui.Gui;

public final class InputVisualizerEvents {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final VisualizerPackManager packs;
    private final VisualizerSettingsManager settings;
    private final LayoutRenderer renderer;

    public InputVisualizerEvents(VisualizerPackManager packs, VisualizerSettingsManager settings) {
        this.packs = packs; this.settings = settings; this.renderer = new LayoutRenderer(packs);
    }

    @SubscribeEvent public void onKey(InputEvent.KeyInputEvent e) {
        if (InputVisualizerClient.openSettingsKey != null && InputVisualizerClient.openSettingsKey.isPressed()) {
            mc.displayGuiScreen(new GuiInputVisualizerSettings(packs, settings, renderer));
        }
    }


    @SubscribeEvent public void onMouse(MouseEvent e) {
        if (!settings.get().enabled) { renderer.discardMouseDelta(); return; }
        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo || !mc.inGameHasFocus) { renderer.discardMouseDelta(); return; }
        try { renderer.captureMouseDelta(e.getDx(), e.getDy()); }
        catch (Throwable ignored) {}
    }

    @SubscribeEvent public void onOverlay(RenderGameOverlayEvent.Post e) {
        // In Forge 1.12.2 Post is fired once per vanilla HUD element.
        // Rendering on TEXT is a reliable once-per-frame point near the end of HUD rendering.
        if (e.getType() != RenderGameOverlayEvent.ElementType.TEXT) return;
        if (!settings.get().enabled) { renderer.discardMouseDelta(); return; }
        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) { renderer.clearTransientState(); renderer.discardMouseDelta(); return; }
        if (!mc.inGameHasFocus) { renderer.clearTransientState(); renderer.discardMouseDelta(); return; }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushMatrix();
        try { renderer.render(e.getResolution(), settings.get()); }
        catch (Throwable t) { packs.warn("Overlay render failed; visualizer suspended for this frame", t); }
        finally { try { GL11.glPopMatrix(); GL11.glPopAttrib(); } catch (Throwable ignored) {} }
    }
}
