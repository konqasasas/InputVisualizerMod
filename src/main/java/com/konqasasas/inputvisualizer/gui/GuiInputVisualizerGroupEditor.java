package com.konqasasas.inputvisualizer.gui;

import com.konqasasas.inputvisualizer.config.*;
import com.konqasasas.inputvisualizer.pack.VisualizerPackManager;
import com.konqasasas.inputvisualizer.render.LayoutRenderer;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.util.*;

public final class GuiInputVisualizerGroupEditor extends GuiScreen {
    private static final double SCALE_MIN = 0.10;
    private static final double SCALE_MAX = 3.00;
    private static final double OPACITY_MIN = 0.05;
    private final VisualizerPackManager packs; private final VisualizerSettingsManager settings; private final LayoutRenderer renderer; private final GuiScreen parent;
    private String selected=""; private boolean dragging,scaleDrag,opacityDrag; private int lastX,lastY; private String message="";
    public GuiInputVisualizerGroupEditor(VisualizerPackManager p, VisualizerSettingsManager s, LayoutRenderer r, GuiScreen parent){packs=p;settings=s;renderer=r;this.parent=parent;}
    public void initGui(){ buttonList.clear(); buttonList.add(new GuiButton(1,width-72,height-26,64,20,"Done")); buttonList.add(new GuiButton(2,width-152,height-26,74,20,"Reset All")); buttonList.add(new GuiButton(3,width-246,height-26,88,20,"Center All")); }
    protected void actionPerformed(GuiButton b){ if(b.id==1){settings.save(); mc.displayGuiScreen(parent);} if(b.id==2){settings.get().groupAdjustments.clear(); selected=""; message="Reset all groups";} if(b.id==3){centerAllGroups();} }
    public void drawScreen(int mx,int my,float pt){
        drawDefaultBackground();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushMatrix();
        try{ renderer.render(new ScaledResolution(mc),settings.get()); }catch(Throwable t){ packs.warn("group editor preview render failed",t); }
        finally{ try{GL11.glPopMatrix();GL11.glPopAttrib();}catch(Throwable ignored){} }
        for(LayoutRenderer.GroupBox b:renderer.groupBoxes()) drawGroupBox(b,b.key.equals(selected));
        drawPanel(mx,my);
        drawCenteredString(fontRenderer,"Group Edit: left-drag move, right-click select, adjust scale/opacity",width/2,8,0xFFFFFFFF);
        if(!message.isEmpty()) drawCenteredString(fontRenderer,message,width/2,height-40,0xFFFFFF88);
        super.drawScreen(mx,my,pt);
    }
    private void drawGroupBox(LayoutRenderer.GroupBox b,boolean sel){ int c=sel?0xD0FFFFFF:0xA037D7FF; drawHorizontalLine((int)b.x,(int)(b.x+b.w),(int)b.y,c); drawHorizontalLine((int)b.x,(int)(b.x+b.w),(int)(b.y+b.h),c); drawVerticalLine((int)b.x,(int)b.y,(int)(b.y+b.h),c); drawVerticalLine((int)(b.x+b.w),(int)b.y,(int)(b.y+b.h),c); drawString(fontRenderer,b.key,(int)b.x+3,(int)b.y+3,c); }
    private void drawPanel(int mx,int my){
        if(selected==null||selected.isEmpty())return;
        VisualizerSettings.GroupAdjustment a=settings.get().adjustment(selected);
        int x=8,y=24,w=170,h=92; drawRect(x,y,x+w,y+h,0xB0101824); drawString(fontRenderer,"Group: "+selected,x+8,y+8,0xFFFFFFFF);
        slider(x+8,y+30,140,"Scale",a.scale,SCALE_MIN,SCALE_MAX);
        slider(x+8,y+56,140,"Opacity",a.opacity,OPACITY_MIN,1);
    }
    private void slider(int x,int y,int w,String label,double value,double min,double max){ drawString(fontRenderer,label+" "+String.format(Locale.ROOT,"%.2f",value),x,y-10,0xFFEAF6FF); drawRect(x,y,x+w,y+4,0xFF34485A); int k=x+(int)Math.round((value-min)/(max-min)*w); drawRect(k-2,y-3,k+2,y+7,0xFF37D7FF); }
    protected void mouseClicked(int mx,int my,int button)throws java.io.IOException{ super.mouseClicked(mx,my,button); if(button==0&&selected!=null&&!selected.isEmpty()){ if(inScale(mx,my)){scaleDrag=true; setScale(mx); return;} if(inOpacity(mx,my)){opacityDrag=true; setOpacity(mx); return;} } LayoutRenderer.GroupBox hit=hit(mx,my); if(hit!=null){ selected=hit.key; lastX=mx; lastY=my; if(button==0)dragging=hit.adjust.allowMove; if(button==1)message="Selected: "+selected; } }
    protected void mouseClickMove(int mx,int my,int button,long time){ if(dragging&&selected!=null&&!selected.isEmpty()){ VisualizerSettings.GroupAdjustment a=settings.get().adjustment(selected); a.offsetX+=mx-lastX; a.offsetY+=my-lastY; a.clamp(); lastX=mx; lastY=my; } if(scaleDrag)setScale(mx); if(opacityDrag)setOpacity(mx); }
    protected void mouseReleased(int mx,int my,int state){ dragging=false; scaleDrag=false; opacityDrag=false; }
    protected void keyTyped(char typedChar,int keyCode)throws java.io.IOException{ if(moveSelected(keyCode))return; super.keyTyped(typedChar,keyCode); }
    private boolean moveSelected(int keyCode){ if(selected==null||selected.isEmpty())return false; int dx=0,dy=0; if(keyCode==Keyboard.KEY_LEFT)dx=-1; else if(keyCode==Keyboard.KEY_RIGHT)dx=1; else if(keyCode==Keyboard.KEY_UP)dy=-1; else if(keyCode==Keyboard.KEY_DOWN)dy=1; else return false; int step=(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)||Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))?10:1; VisualizerSettings.GroupAdjustment a=settings.get().adjustment(selected); a.offsetX+=dx*step; a.offsetY+=dy*step; a.clamp(); message=selected+" offset "+String.format(Locale.ROOT,"%.0f, %.0f",a.offsetX,a.offsetY); return true; }
    private LayoutRenderer.GroupBox hit(int mx,int my){ ArrayList<LayoutRenderer.GroupBox> boxes=new ArrayList<>(renderer.groupBoxes()); for(int i=boxes.size()-1;i>=0;i--){ LayoutRenderer.GroupBox b=boxes.get(i); if(b.contains(mx,my))return b; } return null; }
    private boolean inScale(int mx,int my){return mx>=16&&mx<=156&&my>=54&&my<=66;} private boolean inOpacity(int mx,int my){return mx>=16&&mx<=156&&my>=80&&my<=92;}
    private void setScale(int mx){ VisualizerSettings.GroupAdjustment a=settings.get().adjustment(selected); a.scale=clamp(SCALE_MIN+(mx-16)/140.0*(SCALE_MAX-SCALE_MIN),SCALE_MIN,SCALE_MAX); a.clamp(); }
    private void setOpacity(int mx){ VisualizerSettings.GroupAdjustment a=settings.get().adjustment(selected); a.opacity=clamp(OPACITY_MIN+(mx-16)/140.0*(1-OPACITY_MIN),OPACITY_MIN,1); a.clamp(); }
    private void centerAllGroups(){ java.util.List<LayoutRenderer.GroupBox> boxes=renderer.groupBoxes(); if(boxes.isEmpty()){message="No groups to center"; return;} double cx=width/2.0,cy=height/2.0; for(LayoutRenderer.GroupBox b:boxes){ VisualizerSettings.GroupAdjustment a=settings.get().adjustment(b.key); a.offsetX+=cx-(b.x+b.w/2.0); a.offsetY+=cy-(b.y+b.h/2.0); a.clamp(); } message="Centered all groups"; }
    private double clamp(double v,double a,double b){return v<a?a:v>b?b:v;}
    public void onGuiClosed(){ settings.save(); }
    public boolean doesGuiPauseGame(){return false;}
}
