package com.konqasasas.inputvisualizer.render;

import com.konqasasas.inputvisualizer.config.VisualizerSettings; import com.konqasasas.inputvisualizer.model.Model; import com.konqasasas.inputvisualizer.pack.*; import net.minecraft.client.*; import net.minecraft.client.gui.*; import net.minecraft.client.renderer.*; import net.minecraft.client.settings.KeyBinding; import org.lwjgl.input.*; import org.lwjgl.opengl.GL11; import java.util.*;

public final class LayoutRenderer {
    private static final double INPUT_SUBSTEP_DISTANCE = 2.1;
    private static final int MAX_SUBSTEPS_PER_FRAME = 128;
    private static final int INTERNAL_MAX_TRAIL_POINTS = 1024;
    private static final int INTERNAL_MAX_RENDER_SAMPLES = 2048;
    private static final int INTERNAL_MAX_SMOOTH_POINTS = 2048;
    private static final int TAIL_TAPER_SAMPLES = 12;
    private static final double DEFAULT_MAX_TRAIL_DISTANCE = 165.0;
    private static final double SHARP_TURN_DOT = 0.25;
    private static final double REVERSAL_TURN_DOT = -0.35;
    private static final int MAX_RUN_SAMPLES = 96;
    private final Minecraft mc = Minecraft.getMinecraft(); private final VisualizerPackManager packs; private final HashMap<String,AnimState> anim = new HashMap<>(); private final HashMap<String,TrailState> trails = new HashMap<>(); private final LinkedHashMap<String,GroupBox> groupBoxes = new LinkedHashMap<>(); private final HashSet<String> warnedMissingTextures = new HashSet<>(); private int frameDx, frameDy; private boolean frameMouseRead; private int pendingDx, pendingDy;
    public LayoutRenderer(VisualizerPackManager p){packs=p;} public void clearTransientState(){trails.clear();anim.clear(); pendingDx=0; pendingDy=0;} public void discardMouseDelta(){ try{Mouse.getDX();Mouse.getDY();}catch(Throwable ignored){} pendingDx=0; pendingDy=0; frameDx=0; frameDy=0; frameMouseRead=false; } public void captureMouseDelta(int dx,int dy){ pendingDx+=dx; pendingDy+=dy; }
    public List<GroupBox> groupBoxes(){ return new ArrayList<>(groupBoxes.values()); }
    public void render(ScaledResolution sr, VisualizerSettings st){ VisualizerPack pack=packs.active(); if(pack==null||pack.layout==null)return; groupBoxes.clear(); if(!frameMouseRead){frameDx=pendingDx; frameDy=pendingDy; pendingDx=0; pendingDy=0; if(frameDx==0&&frameDy==0){ try{frameDx=Mouse.getDX(); frameDy=Mouse.getDY();}catch(Throwable ignored){frameDx=0;frameDy=0;} } frameMouseRead=true;} GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770,771,1,0); for(Model.Element e: sort(pack.layout.roots)){ safe2DState(); renderElement(e, sr, st.globalOffsetX, st.globalOffsetY, st.globalScale, st.globalOpacity, pack, st); } frameMouseRead=false; }
    private void renderElement(Model.Element e, ScaledResolution sr, double ox,double oy,double sc,double op, VisualizerPack pack, VisualizerSettings st){ try{ double[] p=anchored(e.anchor,e.x,e.y,e.width,e.height,sr.getScaledWidth(),sr.getScaledHeight()); double x=ox+p[0]*sc, y=oy+p[1]*sc; renderLocal(e,x,y,sc*e.scale,sc*e.scale,1.0,op*e.opacity,pack,st); } catch(Throwable t){packs.warn("element render failed: "+e.id,t);} }
    private void renderLocal(Model.Element e,double x,double y,double boxSc,double textSc,double groupScale,double op,VisualizerPack pack,VisualizerSettings st){ if(op<=0||boxSc<=0||textSc<=0)return; if(e.kind==Model.ElementKind.GROUP){ String key=groupKey(e); VisualizerSettings.GroupAdjustment adj=key.isEmpty()?null:st.groupAdjustments.get(key); if(adj!=null){ x+=adj.offsetX; y+=adj.offsetY; boxSc*=adj.scale; groupScale*=adj.scale; op*=adj.opacity; } if(!key.isEmpty()) groupBoxes.put(key,new GroupBox(key,e.id,x,y,e.width*boxSc,e.height*boxSc,e.gameAdjust)); for(Model.Element c:sort(e.children)) renderChild(c,x,y,e.width,e.height,boxSc,textSc,groupScale,op,pack,st); } else if(e.kind==Model.ElementKind.INPUT){ safe2DState(); renderInput(e,x,y,boxSc,textSc,groupScale,op); safe2DState(); } else if(e.kind==Model.ElementKind.MOUSE_PAD){ safe2DState(); renderMousePad(e,x,y,boxSc,groupScale,op,pack); safe2DState(); } }
    private void renderChild(Model.Element e,double px,double py,double pw,double ph,double boxSc,double textSc,double radiusMul,double op,VisualizerPack pack,VisualizerSettings st){ try{ double[] a=anchored(e.anchor,e.x,e.y,e.width,e.height,pw,ph); renderLocal(e,px+a[0]*boxSc,py+a[1]*boxSc,boxSc*e.scale,textSc*e.scale,radiusMul,op*e.opacity,pack,st);}catch(Throwable t){packs.warn("child render failed: "+e.id,t);} }
    private List<Model.Element> sort(List<Model.Element> in){ ArrayList<Model.Element> l=new ArrayList<>(in); Collections.sort(l,(a,b)->a.zIndex!=b.zIndex?Integer.compare(a.zIndex,b.zIndex):Integer.compare(a.order,b.order)); return l; }
    private double[] anchored(Model.Anchor a,double x,double y,double w,double h,double pw,double ph){ double ax=0,ay=0; switch(a){case TOP_CENTER: ax=(pw-w)/2; break; case TOP_RIGHT: ax=pw-w; break; case CENTER_LEFT: ay=(ph-h)/2; break; case CENTER: ax=(pw-w)/2; ay=(ph-h)/2; break; case CENTER_RIGHT: ax=pw-w; ay=(ph-h)/2; break; case BOTTOM_LEFT: ay=ph-h; break; case BOTTOM_CENTER: ax=(pw-w)/2; ay=ph-h; break; case BOTTOM_RIGHT: ax=pw-w; ay=ph-h; break; default: break;} return new double[]{ax+x,ay+y}; }
    private String groupKey(Model.Element e){ if(e.kind!=Model.ElementKind.GROUP||!e.gameAdjust.enabled)return ""; String k=e.gameAdjust.storageKey==null?"":e.gameAdjust.storageKey.trim(); if(k.isEmpty())k=e.id==null?"":e.id.trim(); return k; }
    private void renderInput(Model.Element e,double x,double y,double boxSc,double textSc,double groupScale,double op){ Model.State state=inputState(e.input); long now=System.currentTimeMillis(); String id=e.id.isEmpty()?"@"+e.order:e.id; AnimState an=anim.computeIfAbsent(id,k->new AnimState()); Model.InputStyle st=e.inputStyle; boolean pressed=state==Model.State.PRESSED, valid=state!=Model.State.DISABLED; double pressAmount=resolvePressAmount(an,pressed,valid,st,now); Model.StateStyle immediate=pressed?st.pressed:valid?st.normal:st.disabled; Model.StateStyle motion=valid?blendMotion(st.normal,st.pressed,pressAmount,immediate):st.disabled; applyPressAnimation(st,motion,pressAmount); Model.Shape shape=immediate.shape!=null?immediate.shape:st.shape; Model.FillMode fillMode=immediate.fillMode!=null?immediate.fillMode:st.fillMode; double baseW=e.width*boxSc,baseH=e.height*boxSc,dw=baseW*motion.scale,dh=baseH*motion.scale; double cx=x+(baseW-dw)/2.0+motion.offsetX*boxSc,cy=y+(baseH-dh)/2.0+motion.offsetY*boxSc; double alpha=op*motion.opacity,r=stateRadius(st,motion)*groupScale,bw=scaledBorder(stateBorder(st,motion),groupScale); drawShape(shape,fillMode,cx,cy,dw,dh,r,bw,withAlpha(immediate.fill,alpha),withAlpha(immediate.border,alpha)); drawLabel(e,st,immediate,cx,cy,dw,dh,textSc*motion.scale,alpha); }
    private double resolvePressAmount(AnimState an,boolean pressed,boolean valid,Model.InputStyle st,long now){ if(!valid){an.down=false;an.releaseAlpha=0;return 0;} boolean pressEnabled=st.press.enabled&&st.press.type!=Model.PressAnim.NONE; double pressDuration=Math.max(1,st.press.durationMs),releaseDuration=Math.max(1,st.release.durationMs); if(pressed&&!an.down){an.down=true;an.pressAt=now;an.releaseFrom=0;} if(!pressed&&an.down){double cur=pressEnabled?easeOutCubic((now-an.pressAt)/pressDuration):0;an.down=false;an.releaseAt=now;an.releaseFrom=cur;} if(pressed){an.releaseAlpha=0;return pressEnabled?easeOutCubic((now-an.pressAt)/pressDuration):0;} double age=an.releaseAt>0?now-an.releaseAt:Double.POSITIVE_INFINITY; if(age<=releaseDuration){double t=easeOutCubic(age/releaseDuration); an.releaseAlpha=(1-t)*(1-t); return Model.clamp(an.releaseFrom*(1-t),0,1);} an.releaseAlpha=0; return 0; }
    private Model.StateStyle blendMotion(Model.StateStyle normal,Model.StateStyle pressed,double t,Model.StateStyle immediate){ Model.StateStyle out=new Model.StateStyle(); out.fill=immediate.fill; out.border=immediate.border; out.text=immediate.text; out.shape=immediate.shape; out.fillMode=immediate.fillMode; if(normal.cornerRadius!=null||pressed.cornerRadius!=null) out.cornerRadius=lerp(normal.cornerRadius==null?0:normal.cornerRadius,pressed.cornerRadius==null?(normal.cornerRadius==null?0:normal.cornerRadius):pressed.cornerRadius,t); if(normal.borderWidth!=null||pressed.borderWidth!=null) out.borderWidth=lerp(normal.borderWidth==null?0:normal.borderWidth,pressed.borderWidth==null?(normal.borderWidth==null?0:normal.borderWidth):pressed.borderWidth,t); out.textShadow=immediate.textShadow; out.opacity=lerp(normal.opacity,pressed.opacity,t); out.scale=lerp(normal.scale,pressed.scale,t); out.offsetX=lerp(normal.offsetX,pressed.offsetX,t); out.offsetY=lerp(normal.offsetY,pressed.offsetY,t); boolean moving=t>0.0001; out.glow.enabled=moving?(immediate.glow.enabled||normal.glow.enabled||pressed.glow.enabled):normal.glow.enabled; out.glow.color=moving?immediate.glow.color:normal.glow.color; out.glow.alpha=lerp(normal.glow.alpha,pressed.glow.alpha,t); out.glow.size=lerp(normal.glow.size,pressed.glow.size,t); return out; }
    private void applyPressAnimation(Model.InputStyle st,Model.StateStyle motion,double amount){ if(!st.press.enabled||st.press.type==Model.PressAnim.NONE)return; if(st.press.type==Model.PressAnim.SCALE||st.press.type==Model.PressAnim.SCALE_OFFSET||st.press.type==Model.PressAnim.GLOW_PULSE) motion.scale=lerp(st.normal.scale,Math.min(st.normal.scale,st.press.scale),amount); if(st.press.type==Model.PressAnim.OFFSET||st.press.type==Model.PressAnim.SCALE_OFFSET||st.press.type==Model.PressAnim.GLOW_PULSE){motion.offsetX=lerp(st.normal.offsetX,st.normal.offsetX+st.press.offsetX,amount); motion.offsetY=lerp(st.normal.offsetY,st.normal.offsetY+st.press.offsetY,amount);} }
    private void drawLabel(Model.Element e,Model.InputStyle st,Model.StateStyle ss,double x,double y,double w,double h,double sc,double alpha){ String label=e.label==null?e.input.autoLabel:e.label; if(label==null||label.isEmpty())return; FontRenderer fr=mc.fontRenderer; double fs=st.fontScale*sc; GL11.glPushMatrix(); GL11.glTranslated(x,y,0); GL11.glScaled(fs,fs,1); double tw=fr.getStringWidth(label), th=fr.FONT_HEIGHT; double pad=4*sc/fs; double lx="left".equals(st.hAlign)?pad:"right".equals(st.hAlign)?w/fs-tw-pad:(w/fs-tw)/2; double ly="top".equals(st.vAlign)?pad:"bottom".equals(st.vAlign)?h/fs-th-pad:(h/fs-th)/2; lx+=(st.textOffsetX*sc)/fs; ly+=(st.textOffsetY*sc)/fs; fr.drawString(label,(float)lx,(float)ly,withAlpha(ss.text,alpha),ss.textShadow==null?st.textShadow:ss.textShadow); GL11.glPopMatrix(); }
    private Model.State inputState(Model.Input in){ try{ if(in.kind==Model.InputKind.KEY_BINDING){ for(KeyBinding kb:mc.gameSettings.keyBindings) if(kb.getKeyDescription().equals(in.name)) return kb.isKeyDown()?Model.State.PRESSED:Model.State.NORMAL; return Model.State.DISABLED;} if(in.kind==Model.InputKind.KEY_CODE) return in.keyCode>0?(Keyboard.isKeyDown(in.keyCode)?Model.State.PRESSED:Model.State.NORMAL):Model.State.DISABLED; if(in.kind==Model.InputKind.MOUSE_BUTTON) return in.mouseButton>=0&&in.mouseButton<Mouse.getButtonCount()?(Mouse.isButtonDown(in.mouseButton)?Model.State.PRESSED:Model.State.NORMAL):Model.State.DISABLED; }catch(Throwable ignored){} return Model.State.DISABLED; }

    private void renderMousePad(Model.Element e,double x,double y,double sc,double groupScale,double op,VisualizerPack pack){ Model.MousePadStyle s=e.mousePad; double w=e.width*sc,h=e.height*sc,r=s.cornerRadius*groupScale,bw=scaledBorder(s.borderWidth,groupScale),bodyAlpha=op*s.opacity; if(s.fillMode==Model.FillMode.FILLED||s.fillMode==Model.FillMode.FILLED_OUTLINE) drawShape(s.shape,Model.FillMode.FILLED,x,y,w,h,r,bw,withAlpha(s.fillColor,bodyAlpha),0); double pad=s.contentPadding*sc; double ix=x+pad,iy=y+pad,iw=Math.max(1,w-pad*2),ih=Math.max(1,h-pad*2); double contentRadius=Model.clamp(Math.max(0,r-pad),0,Math.min(iw,ih)/2.0); Model.Shape contentShape=s.clipShape==Model.ClipShape.VISUAL_SHAPE?s.shape:Model.Shape.RECTANGLE; Clip clip=pushScissor(ix,iy,iw,ih); ShapeClip shapeClip=null; try{ if(s.clipShape==Model.ClipShape.VISUAL_SHAPE) shapeClip=beginShapeClip(s.shape,ix,iy,iw,ih,contentRadius); TrailState ts=trails.computeIfAbsent(e.id.isEmpty()?"pad"+e.order:e.id,k->new TrailState()); updateTrail(ts,s.trail,iw,ih,groupScale); renderBackground(s.background,ix,iy,iw,ih,op,pack,ts,contentShape,contentRadius); renderTrail(ts,s.trail,ix,iy,iw,ih,op); } finally { if(shapeClip!=null) endShapeClip(shapeClip); popScissor(clip); } if((s.fillMode==Model.FillMode.OUTLINE||s.fillMode==Model.FillMode.FILLED_OUTLINE)&&bw>0) drawShape(s.shape,Model.FillMode.OUTLINE,x,y,w,h,r,bw,0,withAlpha(s.borderColor,bodyAlpha)); }
    private void updateTrail(TrailState ts,Model.Trail tr,double w,double h,double sensitivityScale){
        long nowNs=System.nanoTime();
        if(!tr.enabled){ts.points.clear();ts.lastInputNs=0;return;}
        if(!ts.initialized){
            resetTrailStateToCenter(ts,tr,w,h);
            ts.initialized=true;
            ts.lastNs=nowNs;
            ts.lastInputNs=0;
        }
        long prevNs=ts.lastNs;
        if(prevNs<=0) prevNs=nowNs;
        double dt=Math.max(0.000001,(nowNs-prevNs)/1000000000.0);
        ts.lastNs=nowNs;
        prune(ts,tr,nowNs,w);

        int dx=frameDx,dy=-frameDy;
        boolean hasMove=dx!=0||dy!=0;
        boolean l=Mouse.isButtonDown(0), r=Mouse.isButtonDown(1);

        // Empty trail means no visible head either. Do not re-seed without input;
        // otherwise the cursor/head never fades out and resetMode cannot work.
        if(ts.points.isEmpty() && !hasMove){
            if(tr.reset==Model.ResetMode.CENTER_ON_EMPTY) resetTrailStateToCenter(ts,tr,w,h);
            if(tr.mode==Model.TrailMode.PAN) updatePanFollow(ts,tr,w,h,dt);
            return;
        }

        double effectiveSensitivity=tr.sensitivity*Math.max(.01,sensitivityScale);
        double mx=dx*effectiveSensitivity,my=dy*effectiveSensitivity;
        if(hasMove){
            if(ts.points.isEmpty()) seedTrailAtCurrentPosition(ts,tr,prevNs,l,r);
            double dist=Math.hypot(mx,my);
            int steps=Math.max(1,(int)Math.ceil(dist/INPUT_SUBSTEP_DISTANCE));
            steps=Math.min(steps,MAX_SUBSTEPS_PER_FRAME);
            double sx=mx/steps, sy=my/steps;
            for(int i=1;i<=steps;i++){
                long stepTimeNs=prevNs+(long)((nowNs-prevNs)*(i/(double)steps));
                if(stepTimeNs<=0) stepTimeNs=nowNs;
                if(tr.mode==Model.TrailMode.PAN) panStep(ts,tr,sx,sy,w,h,stepTimeNs,l,r);
                else wrapStep(ts,tr,sx,sy,w,h,stepTimeNs,l,r);
                ts.lastInputNs=stepTimeNs;
            }
        }
        if(tr.mode==Model.TrailMode.PAN) updatePanFollow(ts,tr,w,h,dt);
        int maxPoints=Math.max(64,Math.min(INTERNAL_MAX_TRAIL_POINTS,Math.max(tr.maxPoints,512)));
        while(ts.points.size()>maxPoints) ts.points.removeFirst();
        ts.drawCx=ts.cx;ts.drawCy=ts.cy;
    }
    private void resetTrailStateToCenter(TrailState ts,Model.Trail tr,double w,double h){
        ts.cx=w/2.0; ts.cy=h/2.0; ts.drawCx=ts.cx; ts.drawCy=ts.cy;
        if(tr.mode==Model.TrailMode.PAN){ ts.worldX=w/2.0; ts.worldY=h/2.0; ts.viewX=0.0; ts.viewY=0.0; }
        else { ts.worldX=ts.cx; ts.worldY=ts.cy; ts.viewX=0.0; ts.viewY=0.0; }
    }
    private void seedTrailAtCurrentPosition(TrailState ts,Model.Trail tr,long timeNs,boolean l,boolean r){
        if(tr.mode==Model.TrailMode.PAN) addPoint(ts,ts.worldX,ts.worldY,timeNs,l,r,false);
        else addPoint(ts,ts.cx,ts.cy,timeNs,l,r,false);
    }
    private void wrapStep(TrailState ts,Model.Trail tr,double dx,double dy,double w,double h,long nowNs,boolean l,boolean r){
        double remainingDx=dx,remainingDy=dy;
        int guard=0;
        while(guard++<8){
            double x0=ts.cx,y0=ts.cy,x1=x0+remainingDx,y1=y0+remainingDy;
            if(x1>=0&&x1<=w&&y1>=0&&y1<=h){
                ts.cx=x1;ts.cy=y1;addPoint(ts,ts.cx,ts.cy,nowNs,l,r,false);return;
            }
            double best=Double.POSITIVE_INFINITY; int crossed=0;
            if(remainingDx>0&&x1>w){ double t=(w-x0)/remainingDx; if(t>=0&&t<=1&&t<best){best=t;crossed=1;} }
            if(remainingDx<0&&x1<0){ double t=(0-x0)/remainingDx; if(t>=0&&t<=1&&t<best){best=t;crossed=2;} }
            if(remainingDy>0&&y1>h){ double t=(h-y0)/remainingDy; if(t>=0&&t<=1&&t<best){best=t;crossed=3;} }
            if(remainingDy<0&&y1<0){ double t=(0-y0)/remainingDy; if(t>=0&&t<=1&&t<best){best=t;crossed=4;} }
            if(crossed==0||Double.isInfinite(best)){
                ts.cx=Model.clamp(mod(x1,w),0,w);ts.cy=Model.clamp(mod(y1,h),0,h);addPoint(ts,ts.cx,ts.cy,nowNs,l,r,true);return;
            }
            double edgeX=Model.clamp(x0+remainingDx*best,0,w),edgeY=Model.clamp(y0+remainingDy*best,0,h);
            addPoint(ts,edgeX,edgeY,nowNs,l,r,false);
            double usedDx=remainingDx*best,usedDy=remainingDy*best;
            remainingDx-=usedDx;remainingDy-=usedDy;
            double nx=edgeX,ny=edgeY;
            if(crossed==1) nx=0; else if(crossed==2) nx=w;
            if(crossed==3) ny=0; else if(crossed==4) ny=h;
            ts.cx=nx;ts.cy=ny;
            addPoint(ts,ts.cx,ts.cy,nowNs,l,r,true);
            double eps=0.0001;
            if(crossed==1||crossed==2) remainingDx+=crossed==1?-eps:eps;
            if(crossed==3||crossed==4) remainingDy+=crossed==3?-eps:eps;
            if(Math.hypot(remainingDx,remainingDy)<0.0005) return;
        }
    }
    private void panStep(TrailState ts,Model.Trail tr,double dx,double dy,double w,double h,long nowNs,boolean l,boolean r){
        ts.worldX+=dx; ts.worldY+=dy;
        addPoint(ts,ts.worldX,ts.worldY,nowNs,l,r,false);
    }
    private void updatePanFollow(TrailState ts,Model.Trail tr,double w,double h,double dt){
        // Reference-style pan: keep the live head/cursor visually fixed at the pad center.
        // The world cursor remains accurate; only the view projection is changed.
        ts.viewX=ts.worldX-w/2.0;
        ts.viewY=ts.worldY-h/2.0;
        ts.cx=w/2.0;
        ts.cy=h/2.0;
    }
    private void addPoint(TrailState ts,double x,double y,long timeNs,boolean l,boolean r,boolean br){
        if(!ts.points.isEmpty()){
            TrailPoint p=ts.points.getLast();
            if(Math.abs(p.x-x)+Math.abs(p.y-y)<.20&&!br)return;
        }
        ts.points.add(new TrailPoint(x,y,timeNs,l,r,br));
    }
    private void prune(TrailState ts,Model.Trail tr,long nowNs,double padW){
        long lifeNs=(long)Math.max(1,tr.lifetimeMs*1000000.0);
        while(!ts.points.isEmpty()&&nowNs-ts.points.peekFirst().timeNs>lifeNs) ts.points.removeFirst();
        if(ts.points.size()<3)return;
        double maxDist=Math.max(DEFAULT_MAX_TRAIL_DISTANCE, padW*0.90);
        double accum=0;
        int keepFrom=0;
        TrailPoint newer=null;
        for(int i=ts.points.size()-1;i>=0;i--){
            TrailPoint p=ts.points.get(i);
            if(newer!=null && !newer.breakBefore && !p.breakBefore){
                accum+=Math.hypot(newer.x-p.x,newer.y-p.y);
                if(accum>maxDist){ keepFrom=i+1; break; }
            }
            if(p.breakBefore) accum=0;
            newer=p;
        }
        for(int i=0;i<keepFrom && !ts.points.isEmpty();i++) ts.points.removeFirst();
    }
    private void renderTrail(TrailState ts,Model.Trail tr,double x,double y,double w,double h,double op){
        long nowNs=System.nanoTime();
        if(ts.points.isEmpty()) return;
        boolean ml=Mouse.isButtonDown(0), mr=Mouse.isButtonDown(1);
        ArrayList<TrailPoint> pts=new ArrayList<>(ts.points);
        appendRenderHeadPoint(pts,ts,tr,ml,mr);
        int maxPts=Math.max(16,Math.min(INTERNAL_MAX_RENDER_SAMPLES,Math.max(tr.maxRenderedSamples,512)));
        if(pts.size()>maxPts) pts=new ArrayList<>(pts.subList(pts.size()-maxPts,pts.size()));
        ArrayList<ArrayList<TrailSample>> segments=buildTrailSamples(pts,tr,ts,nowNs);
        TrailSample head=forceHeadSampleToCursor(segments,ts,tr,nowNs);
        if(tr.line) drawTrailSegments(segments,tr,x,y,op);
        if(tr.dots) drawDots(segments,tr,x,y,op);
        if(tr.cursor && head!=null && head.fade>0.001) drawHeadCursor(tr,head,x,y,op);
    }
    private void appendRenderHeadPoint(ArrayList<TrailPoint> pts,TrailState ts,Model.Trail tr,boolean ml,boolean mr){
        if(pts.isEmpty())return;
        double liveX=tr.mode==Model.TrailMode.PAN?ts.worldX:ts.cx;
        double liveY=tr.mode==Model.TrailMode.PAN?ts.worldY:ts.cy;
        TrailPoint last=pts.get(pts.size()-1);
        long headTime=ts.lastInputNs>0?ts.lastInputNs:last.timeNs;
        // Render-only endpoint: it guarantees trail head == current cursor, but it does not refresh fade time.
        if(Math.hypot(last.x-liveX,last.y-liveY)>.0001 || last.breakBefore) pts.add(new TrailPoint(liveX,liveY,headTime,ml,mr,false));
    }
    private TrailSample forceHeadSampleToCursor(ArrayList<ArrayList<TrailSample>> segments,TrailState ts,Model.Trail tr,long nowNs){
        if(segments.isEmpty())return null;
        for(int si=segments.size()-1;si>=0;si--){
            ArrayList<TrailSample> seg=segments.get(si);
            if(seg.isEmpty())continue;
            TrailSample head=seg.get(seg.size()-1);
            head.x=ts.cx;
            head.y=ts.cy;
            if(ts.lastInputNs>0) head.timeNs=ts.lastInputNs;
            head.age01=computeSampleAge(head,tr,nowNs);
            double idleFade=computeIdleFade(ts,tr,nowNs);
            head.fade=idleFade;
            double wm=head.right?tr.right.widthMultiplier:head.left?tr.left.widthMultiplier:1.0;
            head.width=Math.max(.05,tr.baseWidth*wm);
            head.color=computeSampleColor(head,tr);
            return head;
        }
        return null;
    }
    private double lx(TrailPoint p,TrailState ts,Model.Trail tr){return tr.mode==Model.TrailMode.PAN?p.x-ts.viewX:p.x;}
    private double ly(TrailPoint p,TrailState ts,Model.Trail tr){return tr.mode==Model.TrailMode.PAN?p.y-ts.viewY:p.y;}
    private ArrayList<ArrayList<TrailSample>> buildTrailSamples(List<TrailPoint> pts,Model.Trail tr,TrailState ts,long nowNs){
        ArrayList<ArrayList<TrailSample>> out=new ArrayList<>();
        ArrayList<TrailPoint> cur=new ArrayList<>();
        for(TrailPoint p:pts){
            if(p.breakBefore&&!cur.isEmpty()){ addSampledSegment(out,cur,tr,ts,nowNs); cur.clear(); }
            cur.add(p);
        }
        if(!cur.isEmpty()) addSampledSegment(out,cur,tr,ts,nowNs);
        return out;
    }
    private void addSampledSegment(ArrayList<ArrayList<TrailSample>> out,List<TrailPoint> raw,Model.Trail tr,TrailState ts,long nowNs){
        ArrayList<TrailSample> local=rawLocalSegment(raw,tr,ts,nowNs);
        if(local.size()<2){ if(!local.isEmpty()) out.add(local); return; }
        int smoothPasses = local.size() < 128 ? 2 : 1;
        ArrayList<TrailSample> smooth=chaikinSmooth(local,tr,nowNs,smoothPasses);
        smooth=removeNearDuplicateSamples(smooth,0.25);
        ArrayList<TrailSample> samples=resampleSamples(smooth,tr,nowNs);
        samples=removeNearDuplicateSamples(samples,0.20);
        applyReferenceStyle(samples,tr,ts,nowNs);
        if(!samples.isEmpty()) out.add(samples);
    }
    private ArrayList<TrailSample> rawLocalSegment(List<TrailPoint> raw,Model.Trail tr,TrailState ts,long nowNs){
        ArrayList<TrailSample> out=new ArrayList<>();
        int max=Math.max(16,Math.min(INTERNAL_MAX_SMOOTH_POINTS,Math.max(tr.maxSmoothingSamples,512)));
        for(TrailPoint p:raw){
            if(out.size()>=max)break;
            out.add(makeSample(lx(p,ts,tr),ly(p,ts,tr),p.timeNs,p.left,p.right,tr,nowNs));
        }
        return out;
    }
    private ArrayList<TrailSample> chaikinSmooth(ArrayList<TrailSample> in,Model.Trail tr,long nowNs,int passes){
        ArrayList<TrailSample> cur=new ArrayList<>(in);
        int max=Math.max(16,Math.min(INTERNAL_MAX_SMOOTH_POINTS,Math.max(tr.maxSmoothingSamples,512)));
        for(int pass=0;pass<passes && cur.size()>=3 && cur.size()<max;pass++){
            ArrayList<TrailSample> next=new ArrayList<>();
            next.add(cur.get(0));
            for(int i=0;i<cur.size()-1 && next.size()<max-1;i++){
                TrailSample a=cur.get(i),b=cur.get(i+1);
                next.add(interpSample(a,b,0.25,tr,nowNs));
                if(next.size()<max-1) next.add(interpSample(a,b,0.75,tr,nowNs));
            }
            next.add(cur.get(cur.size()-1));
            cur=next;
        }
        return cur;
    }
    private ArrayList<TrailSample> removeNearDuplicateSamples(ArrayList<TrailSample> in,double minDist){
        ArrayList<TrailSample> out=new ArrayList<>();
        for(TrailSample s:in){
            if(out.isEmpty()){out.add(s); continue;}
            TrailSample last=out.get(out.size()-1);
            if(Math.hypot(s.x-last.x,s.y-last.y)>=minDist) out.add(s);
            else {
                // Keep the newest/head sample when two samples are nearly identical.
                if(s.timeNs>=last.timeNs) out.set(out.size()-1,s);
            }
        }
        return out;
    }
    private ArrayList<TrailSample> resampleSamples(ArrayList<TrailSample> in,Model.Trail tr,long nowNs){
        ArrayList<TrailSample> out=new ArrayList<>();
        if(in.isEmpty())return out;
        double distStep=Math.max(0.75,Math.min(1.25,tr.baseWidth*.40));
        int max=Math.max(16,Math.min(INTERNAL_MAX_RENDER_SAMPLES,Math.max(tr.maxRenderedSamples,512)));
        out.add(in.get(0));
        double nextAt=distStep;
        double travelled=0;
        for(int i=1;i<in.size()&&out.size()<max;i++){
            TrailSample a=in.get(i-1),b=in.get(i);
            double dx=b.x-a.x,dy=b.y-a.y,len=Math.hypot(dx,dy);
            if(len<0.001)continue;
            while(travelled+len>=nextAt && out.size()<max){
                double u=(nextAt-travelled)/len;
                out.add(interpSample(a,b,u,tr,nowNs));
                nextAt+=distStep;
            }
            travelled+=len;
        }
        TrailSample last=in.get(in.size()-1);
        if(out.isEmpty()||Math.hypot(out.get(out.size()-1).x-last.x,out.get(out.size()-1).y-last.y)>.05) out.add(last);
        return out;
    }
    private TrailSample interpSample(TrailSample a,TrailSample b,double u,Model.Trail tr,long nowNs){
        TrailSample m=new TrailSample();
        m.x=a.x+(b.x-a.x)*u; m.y=a.y+(b.y-a.y)*u;
        m.timeNs=a.timeNs+(long)((b.timeNs-a.timeNs)*u);
        m.left=u<0.5?a.left:b.left; m.right=u<0.5?a.right:b.right;
        m.age01=computeSampleAge(m,tr,nowNs); m.fade=computeSampleFade(m.age01); m.width=computeSampleWidth(m,tr); m.color=computeSampleColor(m,tr);
        return m;
    }
    private TrailSample makeSample(double x,double y,long timeNs,boolean l,boolean r,Model.Trail tr,long nowNs){
        TrailSample s=new TrailSample(); s.x=x;s.y=y;s.timeNs=timeNs;s.left=l;s.right=r;
        s.age01=computeSampleAge(s,tr,nowNs); s.fade=computeSampleFade(s.age01); s.width=computeSampleWidth(s,tr); s.color=computeSampleColor(s,tr); return s;
    }
    private double computeSampleAge(TrailSample s,Model.Trail tr,long nowNs){ return Model.clamp((nowNs-s.timeNs)/(tr.lifetimeMs*1000000.0),0.0,1.0); }
    private double computeSampleFade(double age01){ return 1.0-smoothstep(age01); }
    private double computeSampleWidth(TrailSample s,Model.Trail tr){ double wm=s.right?tr.right.widthMultiplier:s.left?tr.left.widthMultiplier:1.0; return Math.max(.05,tr.baseWidth*wm); }
    private double computeIdleFade(TrailState ts,Model.Trail tr,long nowNs){
        if(ts.lastInputNs<=0)return 0.0;
        double age=Model.clamp((nowNs-ts.lastInputNs)/(tr.lifetimeMs*1000000.0),0.0,1.0);
        return 1.0-smoothstep(age);
    }
    private void applyReferenceStyle(ArrayList<TrailSample> samples,Model.Trail tr,TrailState ts,long nowNs){
        if(samples==null||samples.isEmpty())return;
        double idleFade=computeIdleFade(ts,tr,nowNs);
        for(int i=0;i<samples.size();i++){
            TrailSample s=samples.get(i);
            s.age01=computeSampleAge(s,tr,nowNs);
            double localAge=1.0-smoothstep(s.age01);
            double localFade=0.82+0.18*localAge;
            s.fade=idleFade*localFade;
            double taper=1.0;
            if(i<TAIL_TAPER_SAMPLES){
                taper=smoothstep((i+1)/(double)(TAIL_TAPER_SAMPLES+1));
            }
            double wm=s.right?tr.right.widthMultiplier:s.left?tr.left.widthMultiplier:1.0;
            s.width=Math.max(.05,(tr.tailWidth+(tr.baseWidth-tr.tailWidth)*taper)*wm);
            s.color=computeSampleColor(s,tr);
        }
    }
    private int computeSampleColor(TrailSample s,Model.Trail tr){ if(tr.colorMode==Model.ColorMode.BUTTON_STATE){ if(s.right)return tr.right.color; if(s.left)return tr.left.color; } if(tr.colorMode==Model.ColorMode.AGE_GRADIENT) return lerpColor(tr.color,tr.tailColor,s.age01); return s.right?tr.right.color:s.left?tr.left.color:tr.color; }
    private void drawGlowStrips(List<ArrayList<TrailSample>> segments,Model.Trail tr,double x,double y,double op){
        double g=Math.max(.1,Math.min(1.8,tr.glowWidthMultiplier));
        drawTrailStripPass(segments,tr,x,y,op,g,0.07,true);
    }
    private void drawTrailSegments(List<ArrayList<TrailSample>> segments,Model.Trail tr,double x,double y,double op){ drawTrailStripPass(segments,tr,x,y,op,1.0,1.0,false); }
    private void beginTrailMeshState(){ GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glEnable(GL11.GL_BLEND); GL11.glShadeModel(GL11.GL_SMOOTH); }
    private void endTrailMeshState(){ GL11.glShadeModel(GL11.GL_FLAT); GL11.glColor4f(1f,1f,1f,1f); GL11.glEnable(GL11.GL_ALPHA_TEST); GL11.glEnable(GL11.GL_TEXTURE_2D); }
    private void drawTrailStripPass(List<ArrayList<TrailSample>> segments,Model.Trail tr,double x,double y,double op,double widthMul,double alphaMul,boolean glow){
        beginTrailMeshState();
        try{
            for(ArrayList<TrailSample> raw:segments){
                ArrayList<TrailSample> seg=removeNearDuplicateSamples(raw,0.18);
                if(seg.size()<2){
                    if(seg.size()==1 && !glow){ TrailSample only=seg.get(0); drawFilledCircleMeshRaw(x+only.x,y+only.y,Math.max(1.0,only.width*.55),only.color,op*only.fade); }
                    continue;
                }
                if(glow) drawGroupedRuns(seg,tr,x,y,op,widthMul,alphaMul,true,0);
                else {
                    int mainStart=drawTailTaperRuns(seg,tr,x,y,op);
                    drawGroupedRuns(seg,tr,x,y,op,widthMul,alphaMul,false,mainStart);
                    drawSharpJoins(seg,tr,x,y,op);
                    TrailSample head=seg.get(seg.size()-1);
                    drawFilledCircleMeshRaw(x+head.x,y+head.y,Math.max(2.0,head.width*.68),head.color,op*head.fade);
                }
            }
        }finally{ endTrailMeshState(); }
    }
    private int drawTailTaperRuns(ArrayList<TrailSample> seg,Model.Trail tr,double x,double y,double op){
        int taperEnd=Math.min(TAIL_TAPER_SAMPLES,seg.size()-1);
        for(int i=0;i<taperEnd;i++){
            TrailSample a=seg.get(i),b=seg.get(i+1);
            if(Math.hypot(b.x-a.x,b.y-a.y)<0.001)continue;
            double t=smoothstep((i+1)/(double)(taperEnd+1));
            double alpha=(op*((a.fade+b.fade)*0.5))*overlapComp(op*((a.fade+b.fade)*0.5))*0.88;
            double w=Math.max(0.05,(tr.tailWidth+(tr.baseWidth-tr.tailWidth)*t));
            int color=b.color;
            drawShortStroke(a,b,x,y,w,color,alpha,true);
        }
        return Math.max(0,taperEnd);
    }
    private void drawGroupedRuns(ArrayList<TrailSample> seg,Model.Trail tr,double x,double y,double op,double widthMul,double alphaMul,boolean glow,int startIndex){
        int n=seg.size();
        int start=Math.max(0,Math.min(startIndex,n-2));
        while(start<n-1){
            int end=start+1;
            int st=buttonState(seg.get(start));
            while(end<n-1 && end-start<MAX_RUN_SAMPLES){
                boolean split=false;
                if(buttonState(seg.get(end))!=st) split=true;
                if(end>start && end<n-1 && isReversal(seg,end)) split=true;
                if(split)break;
                end++;
            }
            drawTrailRun(seg,start,end,tr,x,y,op,widthMul,alphaMul,glow);
            start=end;
        }
    }
    private void drawTrailRun(ArrayList<TrailSample> seg,int from,int to,Model.Trail tr,double x,double y,double op,double widthMul,double alphaMul,boolean glow){
        if(to<=from)return;
        double[][] normals=smoothNormalsRange(seg,from,to);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for(int i=from;i<=to;i++){
            TrailSample s=seg.get(i);
            double[] nm=normals[i-from];
            double gm=s.right?tr.right.glowMultiplier:s.left?tr.left.glowMultiplier:1.0;
            double half=Math.max(.05,s.width*.5*widthMul*(glow?gm:1.0));
            double alpha=op*s.fade*alphaMul*(glow?Math.min(1.6,gm):1.0);
            if(glow) alpha=Math.min(alpha,op*0.12);
            int drawColor=glow?(s.right?tr.right.color:s.left?tr.left.color:tr.glowColor):s.color;
            argb(drawColor,alpha);
            GL11.glVertex2d(x+s.x+nm[0]*half,y+s.y+nm[1]*half);
            GL11.glVertex2d(x+s.x-nm[0]*half,y+s.y-nm[1]*half);
        }
        GL11.glEnd();
        TrailSample a=seg.get(from),b=seg.get(to);
        double ca=capAlpha(op*a.fade*alphaMul,glow), cb=capAlpha(op*b.fade*alphaMul,glow);
        int ac=glow?(a.right?tr.right.color:a.left?tr.left.color:tr.glowColor):a.color;
        int bc=glow?(b.right?tr.right.color:b.left?tr.left.color:tr.glowColor):b.color;
        double agm=a.right?tr.right.glowMultiplier:a.left?tr.left.glowMultiplier:1.0;
        double bgm=b.right?tr.right.glowMultiplier:b.left?tr.left.glowMultiplier:1.0;
        if(glow){ ca=Math.min(ca*agm,op*0.10); cb=Math.min(cb*bgm,op*0.10); }
        drawFilledCircleMeshRaw(x+a.x,y+a.y,Math.max(.05,a.width*.5*widthMul*(glow?agm:1.0)),ac,ca);
        drawFilledCircleMeshRaw(x+b.x,y+b.y,Math.max(.05,b.width*.5*widthMul*(glow?bgm:1.0)),bc,cb);
    }
    private void drawShortStroke(TrailSample a,TrailSample b,double x,double y,double width,int color,double alpha,boolean caps){
        double dx=b.x-a.x,dy=b.y-a.y,len=Math.hypot(dx,dy); if(len<0.001||alpha<=0.001)return;
        double nx=-dy/len,ny=dx/len,half=Math.max(.03,width*.5);
        argb(color,alpha);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        GL11.glVertex2d(x+a.x+nx*half,y+a.y+ny*half); GL11.glVertex2d(x+a.x-nx*half,y+a.y-ny*half);
        GL11.glVertex2d(x+b.x+nx*half,y+b.y+ny*half); GL11.glVertex2d(x+b.x-nx*half,y+b.y-ny*half);
        GL11.glEnd();
        if(caps){ double ca=capAlpha(alpha,false); drawFilledCircleMeshRaw(x+a.x,y+a.y,half,color,ca); drawFilledCircleMeshRaw(x+b.x,y+b.y,half,color,ca); }
    }
    private void drawSharpJoins(ArrayList<TrailSample> seg,Model.Trail tr,double x,double y,double op){
        for(int i=1;i<seg.size()-1;i++){
            double d=turnDot(seg,i);
            if(d<SHARP_TURN_DOT){
                TrailSample s=seg.get(i);
                double alpha=joinAlpha(op*s.fade);
                if(isReversal(seg,i)) alpha*=0.92;
                drawFilledCircleMeshRaw(x+s.x,y+s.y,Math.max(.05,s.width*.50),s.color,alpha);
            }
        }
    }
    private double capAlpha(double alpha,boolean glow){ double c=alpha*overlapComp(alpha)*(glow?0.62:0.86); return glow?Math.min(c,0.10):c; }
    private double joinAlpha(double alpha){ return alpha*overlapComp(alpha)*0.74; }
    private double overlapComp(double alpha){ return alpha<0.45?0.78:0.90; }
    private int buttonState(TrailSample s){ return s.right?2:s.left?1:0; }
    private boolean isReversal(ArrayList<TrailSample> seg,int i){ return turnDot(seg,i)<REVERSAL_TURN_DOT; }
    private double turnDot(ArrayList<TrailSample> seg,int i){
        if(i<=0||i>=seg.size()-1)return 1.0;
        TrailSample p0=seg.get(i-1),p1=seg.get(i),p2=seg.get(i+1);
        double ax=p1.x-p0.x,ay=p1.y-p0.y,bx=p2.x-p1.x,by=p2.y-p1.y;
        double al=Math.hypot(ax,ay),bl=Math.hypot(bx,by);
        if(al<0.001||bl<0.001)return 1.0;
        ax/=al;ay/=al;bx/=bl;by/=bl;
        return ax*bx+ay*by;
    }
    private double[][] smoothNormalsRange(ArrayList<TrailSample> seg,int from,int to){
        ArrayList<TrailSample> sub=new ArrayList<>(); for(int i=from;i<=to;i++)sub.add(seg.get(i)); return smoothNormals(sub);
    }
    private double[][] smoothNormals(ArrayList<TrailSample> seg){
        int n=seg.size(); double[][] out=new double[n][2];
        for(int i=0;i<n;i++) out[i]=sampleNormal(seg,i);
        // Stabilize orientation so the ribbon does not flip and show strong cross-normal seams.
        for(int i=1;i<n;i++){ double dot=out[i-1][0]*out[i][0]+out[i-1][1]*out[i][1]; if(dot<0){out[i][0]*=-1; out[i][1]*=-1;} }
        if(n>2){
            double[][] tmp=new double[n][2];
            for(int i=0;i<n;i++){
                double x=out[i][0]*2.0,y=out[i][1]*2.0,weight=2.0;
                if(i>0){x+=out[i-1][0];y+=out[i-1][1];weight+=1.0;}
                if(i<n-1){x+=out[i+1][0];y+=out[i+1][1];weight+=1.0;}
                x/=weight; y/=weight; double l=Math.hypot(x,y); if(l<0.0001){tmp[i][0]=out[i][0];tmp[i][1]=out[i][1];} else {tmp[i][0]=x/l;tmp[i][1]=y/l;}
            }
            out=tmp;
        }
        return out;
    }
    private double[] sampleNormal(ArrayList<TrailSample> seg,int i){
        TrailSample cur=seg.get(i);
        double dx=0,dy=0;
        int prev=Math.max(0,i-2), next=Math.min(seg.size()-1,i+2);
        if(next!=prev){ TrailSample a=seg.get(prev),b=seg.get(next); dx=b.x-a.x; dy=b.y-a.y; }
        if(Math.hypot(dx,dy)<0.0001){
            if(i<seg.size()-1){TrailSample n=seg.get(i+1);dx=n.x-cur.x;dy=n.y-cur.y;}
            else if(i>0){TrailSample p=seg.get(i-1);dx=cur.x-p.x;dy=cur.y-p.y;}
        }
        double len=Math.hypot(dx,dy); if(len<0.0001)return new double[]{0,1}; dx/=len;dy/=len; return new double[]{-dy,dx};
    }
    private void drawRoundCap(double x,double y,double r,int c){ drawFilledCircleMesh(x,y,r,c); }
    private void drawDots(List<ArrayList<TrailSample>> segments,Model.Trail tr,double x,double y,double op){
        double spacing=Math.max(1,tr.dotSpacing);
        for(ArrayList<TrailSample> seg:segments){
            double carry=0;
            if(seg.size()<2)continue;
            for(int i=1;i<seg.size();i++){
                TrailSample a=seg.get(i-1),b=seg.get(i);
                double dx=b.x-a.x,dy=b.y-a.y,len=Math.hypot(dx,dy);
                if(len<0.001)continue;
                carry+=len;
                while(carry>=spacing){
                    double back=carry-spacing;
                    double u=Model.clamp(1.0-back/len,0,1);
                    TrailSample m=mixSample(a,b,u,tr);
                    int col=tr.dotColor!=0?tr.dotColor:m.color;
                    drawFilledCircleMesh(x+m.x,y+m.y,Math.max(.5,tr.dotSize),withAlpha(col,op*m.fade*0.72));
                    carry-=spacing;
                }
            }
        }
    }
    private TrailSample mixSample(TrailSample a,TrailSample b,double u,Model.Trail tr){ TrailSample m=new TrailSample(); m.x=a.x+(b.x-a.x)*u; m.y=a.y+(b.y-a.y)*u; m.timeNs=a.timeNs+(long)((b.timeNs-a.timeNs)*u); m.left=a.left||b.left; m.right=a.right||b.right; m.age01=a.age01+(b.age01-a.age01)*u; m.fade=a.fade+(b.fade-a.fade)*u; m.width=a.width+(b.width-a.width)*u; m.color=computeSampleColor(m,tr); return m; }
    private void drawHeadCursor(Model.Trail tr,TrailSample head,double x,double y,double op){
        double alpha=op*head.fade;
        int base=tr.cursorColor!=0?tr.cursorColor:head.color;
        double headRadius=Math.max(head.width*0.68,Math.max(2.0,tr.cursorSize*0.28));
        if(tr.cursorKind==Model.CursorKind.NONE)return;
        if(tr.cursorKind==Model.CursorKind.CIRCLE) drawCircleOutlineMesh(x+head.x,y+head.y,Math.max(headRadius,tr.cursorSize/2),Math.max(1,head.width*.18),withAlpha(base,alpha));
        else if(tr.cursorKind==Model.CursorKind.CURSOR_ARROW){
            drawFilledCircleMesh(x+head.x,y+head.y,headRadius,withAlpha(base,alpha));
        } else {
            drawFilledCircleMesh(x+head.x,y+head.y,headRadius,withAlpha(base,alpha));
        }
    }
    private void drawCursor(Model.Trail tr,double cx,double cy,double op,boolean l,boolean r){
        int base=(l?tr.left.color:r?tr.right.color:tr.color);
        int c=withAlpha(tr.cursorColor!=0?tr.cursorColor:base,op);
        if(tr.cursorKind==Model.CursorKind.NONE)return;
        if(tr.cursorKind==Model.CursorKind.CURSOR_ARROW){ drawFilledCircleMesh(cx,cy,Math.max(1,tr.cursorSize*.28),c); drawRect(cx,cy,cx+tr.cursorSize,cy+Math.max(1,tr.cursorSize/2),c); drawRect(cx,cy,cx+Math.max(1,tr.cursorSize/2),cy+tr.cursorSize,c); }
        else if(tr.cursorKind==Model.CursorKind.CIRCLE) drawCircleOutlineMesh(cx,cy,tr.cursorSize/2,Math.max(1,tr.cursorSize*.12),c);
        else drawFilledCircleMesh(cx,cy,tr.cursorSize/2,c);
    }

    private void renderBackground(Model.Background b,double x,double y,double w,double h,double op,VisualizerPack pack,TrailState ts,Model.Shape clipShape,double clipRadius){
        if(b==null||b.type==null)return;
        double ox=b.scroll==Model.ScrollMode.WORLD?-ts.viewX:0, oy=b.scroll==Model.ScrollMode.WORLD?-ts.viewY:0;
        double a=op*b.opacity;
        switch(b.type){
            case NONE: return;
            case CHECKERBOARD: renderCheckerboard(b,x,y,w,h,a,ox,oy); return;
            case GRID: renderGridBackground(b,x,y,w,h,a,ox,oy); return;
            case DOTS: renderDotsBackground(b,x,y,w,h,a,ox,oy); return;
            case IMAGE: renderImageOrFallback(b,x,y,w,h,a,ox,oy,pack,clipShape,clipRadius); return;
            default: renderCheckerboard(b,x,y,w,h,a,ox,oy);
        }
    }
    private void renderGridBackground(Model.Background b,double x,double y,double w,double h,double a,double ox,double oy){ double size=Math.max(2,b.gridSize),lw=Math.max(.1,b.lineWidth),half=lw/2.0; for(double px=mod(ox,size);px<w;px+=size) drawRect(x+px-half,y,x+px+half,y+h,withAlpha(b.lineColor,a)); for(double py=mod(oy,size);py<h;py+=size) drawRect(x,y+py-half,x+w,y+py+half,withAlpha(b.lineColor,a)); }
    private void renderDotsBackground(Model.Background b,double x,double y,double w,double h,double a,double ox,double oy){ double spacing=Math.max(2,b.spacing),dot=Math.max(.1,b.dotSize); for(double px=mod(ox,spacing);px<w;px+=spacing) for(double py=mod(oy,spacing);py<h;py+=spacing) drawFilledCircle(x+px,y+py,dot,withAlpha(b.dotColor,a)); }
    private void renderImageOrFallback(Model.Background b,double x,double y,double w,double h,double a,double ox,double oy,VisualizerPack pack,Model.Shape clipShape,double clipRadius){ LoadedPackTexture tex=pack.textures.get(b.path); if(tex!=null) renderImage(tex,b,x,y,w,h,a,ox,oy,clipShape,clipRadius); else { warnMissingTexture(b.path); renderCheckerboard(b,x,y,w,h,a,ox,oy); } }
    private void renderCheckerboard(Model.Background b,double x,double y,double w,double h,double a,double ox,double oy){ double cell=Math.max(2,b.cellSize); for(double px=-cell+mod(ox,cell);px<w+cell;px+=cell) for(double py=-cell+mod(oy,cell);py<h+cell;py+=cell){ boolean alt=(((int)Math.floor(px/cell)+(int)Math.floor(py/cell))&1)==0; drawRect(x+px,y+py,x+px+cell,y+py+cell,withAlpha(alt?b.colorA:b.colorB,a)); } }
    private void warnMissingTexture(String path){ String key=path==null?"":path; if(warnedMissingTextures.add(key)) packs.warn("image background missing, using checkerboard fallback: "+key,null); }
    private void renderImage(LoadedPackTexture tex,Model.Background b,double x,double y,double w,double h,double a,double ox,double oy,Model.Shape clipShape,double clipRadius){
        safe2DState(); mc.getTextureManager().bindTexture(tex.location); GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_ALPHA_TEST);
        boolean shaped=clipShape!=null&&clipShape!=Model.Shape.RECTANGLE;
        if(b.fit==Model.ImageFit.TILE){ double startX=x-tex.width+mod(ox,tex.width), startY=y-tex.height+mod(oy,tex.height); if(shaped){ setTextureRepeat(true); drawTexturedShape(clipShape,x,y,w,h,clipRadius,startX,startY,tex.width,tex.height,a); setTextureRepeat(false); } else for(double xx=startX;xx<x+w+tex.width;xx+=tex.width) for(double yy=startY;yy<y+h+tex.height;yy+=tex.height) drawTexturedQuad(xx,yy,tex.width,tex.height,0,0,1,1,a); }
        else { double dw=w,dh=h; if(b.fit==Model.ImageFit.CENTER){dw=tex.width;dh=tex.height;} else if(b.fit==Model.ImageFit.CONTAIN||b.fit==Model.ImageFit.COVER){ double s=(b.fit==Model.ImageFit.COVER?Math.max(w/(double)tex.width,h/(double)tex.height):Math.min(w/(double)tex.width,h/(double)tex.height)); dw=tex.width*s;dh=tex.height*s;} double dx=x+(w-dw)/2.0,dy=y+(h-dh)/2.0; if(shaped) drawTexturedShape(clipShape,x,y,w,h,clipRadius,dx,dy,dw,dh,a); else drawTexturedQuad(dx,dy,dw,dh,0,0,1,1,a); }
        GL11.glColor4f(1f,1f,1f,1f);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }
    private void setTextureRepeat(boolean repeat){
        int mode=repeat?GL11.GL_REPEAT:GL11.GL_CLAMP;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,mode);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,mode);
    }
    private void drawTexturedShape(Model.Shape shape,double clipX,double clipY,double clipW,double clipH,double radius,double texX,double texY,double texW,double texH,double alpha){
        if(clipW<=0||clipH<=0||texW<=0||texH<=0||alpha<=0.001)return;
        GL11.glColor4f(1f,1f,1f,(float)Model.clamp(alpha,0,1));
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        emitTexturedVertex(clipX+clipW/2.0,clipY+clipH/2.0,texX,texY,texW,texH);
        if(shape==Model.Shape.CIRCLE){
            double cx=clipX+clipW/2.0,cy=clipY+clipH/2.0,r=Math.min(clipW,clipH)/2.0;
            int seg=circleSegments(r);
            for(int i=0;i<=seg;i++){ double a=2*Math.PI*i/seg-Math.PI/2.0; emitTexturedVertex(cx+Math.cos(a)*r,cy+Math.sin(a)*r,texX,texY,texW,texH); }
        } else {
            ArrayList<double[]> path=roundRectPath(clipX,clipY,clipW,clipH,Model.clamp(radius,0,Math.min(clipW,clipH)/2.0));
            for(double[] pt:path) emitTexturedVertex(pt[0],pt[1],texX,texY,texW,texH);
            if(!path.isEmpty()) emitTexturedVertex(path.get(0)[0],path.get(0)[1],texX,texY,texW,texH);
        }
        GL11.glEnd();
        GL11.glColor4f(1f,1f,1f,1f);
    }
    private void emitTexturedVertex(double x,double y,double texX,double texY,double texW,double texH){
        GL11.glTexCoord2d((x-texX)/texW,(y-texY)/texH);
        GL11.glVertex2d(x,y);
    }
    private void drawTexturedQuad(double x,double y,double w,double h,double u0,double v0,double u1,double v1,double alpha){
        if(w<=0||h<=0||alpha<=0.001)return;
        GL11.glColor4f(1f,1f,1f,(float)Model.clamp(alpha,0,1));
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(u0,v1); GL11.glVertex2d(x,y+h);
        GL11.glTexCoord2d(u1,v1); GL11.glVertex2d(x+w,y+h);
        GL11.glTexCoord2d(u1,v0); GL11.glVertex2d(x+w,y);
        GL11.glTexCoord2d(u0,v0); GL11.glVertex2d(x,y);
        GL11.glEnd();
        GL11.glColor4f(1f,1f,1f,1f);
    }

    private ShapeClip beginShapeClip(Model.Shape shape,double x,double y,double w,double h,double r){
        try{
            int bits=GL11.glGetInteger(GL11.GL_STENCIL_BITS);
            if(bits<=0){ packs.warn("visualShape clip requested but stencil buffer is unavailable; using rectangle clip fallback",null); return null; }
            boolean wasStencil=GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            boolean tex=GL11.glIsEnabled(GL11.GL_TEXTURE_2D), alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST), cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
            int oldFunc=GL11.glGetInteger(GL11.GL_STENCIL_FUNC), oldRef=GL11.glGetInteger(GL11.GL_STENCIL_REF), oldValueMask=GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK), oldWriteMask=GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
            int oldFail=GL11.glGetInteger(GL11.GL_STENCIL_FAIL), oldZFail=GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL), oldZPass=GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
            ShapeClip clip=new ShapeClip(wasStencil,tex,alpha,cull,oldFunc,oldRef,oldValueMask,oldWriteMask,oldFail,oldZFail,oldZPass);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glColorMask(false,false,false,false);
            GL11.glDepthMask(false);
            GL11.glStencilFunc(GL11.GL_ALWAYS,1,0xFF);
            GL11.glStencilOp(GL11.GL_REPLACE,GL11.GL_REPLACE,GL11.GL_REPLACE);
            drawClipMaskShape(shape,x,y,w,h,r);
            GL11.glColorMask(true,true,true,true);
            GL11.glDepthMask(true);
            GL11.glStencilMask(0x00);
            GL11.glStencilFunc(GL11.GL_EQUAL,1,0xFF);
            GL11.glStencilOp(GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_KEEP);
            safe2DState();
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            return clip;
        }catch(Throwable t){ packs.warn("visualShape clip failed; using rectangle clip fallback",t); GL11.glColorMask(true,true,true,true); GL11.glDepthMask(true); GL11.glDisable(GL11.GL_STENCIL_TEST); safe2DState(); return null; }
    }
    private void endShapeClip(ShapeClip c){
        try{
            GL11.glStencilMask(c.writeMask);
            GL11.glStencilFunc(c.func,c.ref,c.valueMask);
            GL11.glStencilOp(c.fail,c.zfail,c.zpass);
            if(c.wasStencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
            if(c.tex) GL11.glEnable(GL11.GL_TEXTURE_2D); else GL11.glDisable(GL11.GL_TEXTURE_2D);
            if(c.alpha) GL11.glEnable(GL11.GL_ALPHA_TEST); else GL11.glDisable(GL11.GL_ALPHA_TEST);
            if(c.cull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1f,1f,1f,1f);
        }catch(Throwable ignored){ GL11.glDisable(GL11.GL_STENCIL_TEST); safe2DState(); }
    }
    private void drawClipMaskShape(Model.Shape shape,double x,double y,double w,double h,double r){
        drawShape(shape,Model.FillMode.FILLED,x,y,w,h,r,0,0xFFFFFFFF,0);
    }

    private Clip pushScissor(double x,double y,double w,double h){ ScaledResolution sr=new ScaledResolution(mc); int sf=sr.getScaleFactor(); boolean was=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST); if(!was)GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor((int)(x*sf),(int)((sr.getScaledHeight()-y-h)*sf),(int)(w*sf),(int)(h*sf)); return new Clip(was); } private void popScissor(Clip c){ if(!c.was)GL11.glDisable(GL11.GL_SCISSOR_TEST); }
    private void drawGlow(Model.Shape s,double x,double y,double w,double h,double r,double bw,double size,int c){ for(int i=3;i>=1;i--) drawShape(s,Model.FillMode.OUTLINE,x-size*i/3.0,y-size*i/3.0,w+size*i*2.0/3.0,h+size*i*2.0/3.0,r+size*i/3.0,bw,0,withAlpha(c,1.0/i)); }
    private void drawShape(Model.Shape shape,Model.FillMode mode,double x,double y,double w,double h,double r,double bw,int fill,int border){
        if(w<=0||h<=0||mode==Model.FillMode.NONE)return;
        r=Model.clamp(r,0,Math.min(w,h)/2.0); bw=Math.max(0,bw);
        if(mode==Model.FillMode.FILLED||mode==Model.FillMode.FILLED_OUTLINE){ if(((fill>>>24)&255)>0){ if(shape==Model.Shape.CIRCLE) drawFilledCircle(x+w/2,y+h/2,Math.min(w,h)/2,fill); else if(shape==Model.Shape.ROUNDED_RECTANGLE) drawRoundRectFilled(x,y,w,h,r,fill); else drawRect(x,y,x+w,y+h,fill); } }
        if((mode==Model.FillMode.OUTLINE||mode==Model.FillMode.FILLED_OUTLINE)&&bw>0&&((border>>>24)&255)>0){ if(shape==Model.Shape.CIRCLE) drawCircleOutline(x+w/2,y+h/2,Math.min(w,h)/2,bw,border); else if(shape==Model.Shape.ROUNDED_RECTANGLE) drawRoundRectOutline(x,y,w,h,r,bw,border); else drawRectOutline(x,y,w,h,bw,border); }
    }
    private int curveSegments(double radius){ return (int)Math.max(10,Math.min(36,Math.ceil(radius*1.35))); }
    private void safe2DState(){ GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770,771,1,0); GlStateManager.disableDepth(); GlStateManager.disableLighting(); GlStateManager.color(1f,1f,1f,1f); GL11.glLineWidth(1.0F); GL11.glShadeModel(GL11.GL_FLAT); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glEnable(GL11.GL_ALPHA_TEST); }
    private void beginFlat(int c){ GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glEnable(GL11.GL_BLEND); GL11.glEnable(GL11.GL_LINE_SMOOTH); GL11.glHint(GL11.GL_LINE_SMOOTH_HINT,GL11.GL_NICEST); argb(c,1); }
    private void endFlat(){ GL11.glLineWidth(1.0F); GL11.glDisable(GL11.GL_LINE_SMOOTH); GL11.glColor4f(1f,1f,1f,1f); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glEnable(GL11.GL_ALPHA_TEST); }
    private void drawRoundRectFilled(double x,double y,double w,double h,double r,int c){
        if(r<=0){ drawRect(x,y,x+w,y+h,c); return; }
        r=Model.clamp(r,0,Math.min(w,h)/2.0); beginFlat(c);
        ArrayList<double[]> path=roundRectPath(x,y,w,h,r);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(x+w/2.0,y+h/2.0);
        for(double[] pt:path) GL11.glVertex2d(pt[0],pt[1]);
        if(!path.isEmpty()) GL11.glVertex2d(path.get(0)[0],path.get(0)[1]);
        GL11.glEnd();
        endFlat();
    }
    private ArrayList<double[]> roundRectPath(double x,double y,double w,double h,double r){
        return roundRectPath(x,y,w,h,r,curveSegments(r));
    }
    private ArrayList<double[]> roundRectPath(double x,double y,double w,double h,double r,int seg){
        ArrayList<double[]> path=new ArrayList<>();
        addArcToList(path,x+w-r,y+r,r,270,360,seg);
        addArcToList(path,x+w-r,y+h-r,r,0,90,seg);
        addArcToList(path,x+r,y+h-r,r,90,180,seg);
        addArcToList(path,x+r,y+r,r,180,270,seg);
        return path;
    }
    private void addArcToList(ArrayList<double[]> path,double cx,double cy,double r,double startDeg,double endDeg){
        addArcToList(path,cx,cy,r,startDeg,endDeg,curveSegments(r));
    }
    private void addArcToList(ArrayList<double[]> path,double cx,double cy,double r,double startDeg,double endDeg,int seg){
        for(int i=0;i<=seg;i++){ double a=Math.toRadians(startDeg+(endDeg-startDeg)*i/seg); path.add(new double[]{cx+Math.cos(a)*r,cy+Math.sin(a)*r}); }
    }
    private void addRoundRectPath(double x,double y,double w,double h,double r,boolean close){
        ArrayList<double[]> path=roundRectPath(x,y,w,h,r);
        for(double[] pt:path) GL11.glVertex2d(pt[0],pt[1]);
        if(close&&!path.isEmpty()) GL11.glVertex2d(path.get(0)[0],path.get(0)[1]);
    }
    private void drawQuarterFan(double cx,double cy,double r,double startDeg,double endDeg){ int seg=curveSegments(r); GL11.glBegin(GL11.GL_TRIANGLE_FAN); GL11.glVertex2d(cx,cy); for(int i=0;i<=seg;i++){ double a=Math.toRadians(startDeg+(endDeg-startDeg)*i/seg); GL11.glVertex2d(cx+Math.cos(a)*r,cy+Math.sin(a)*r); } GL11.glEnd(); }
    private void drawRoundRectOutline(double x,double y,double w,double h,double r,double bw,int c){
        if(w<=0||h<=0||bw<=0||((c>>>24)&255)<=0)return;
        if(r<=0){ drawRectOutline(x,y,w,h,bw,c); return; }
        r=Model.clamp(r,0,Math.min(w,h)/2.0); double half=bw/2.0;
        double ox=x-half,oy=y-half,ow=w+bw,oh=h+bw,or=r+half;
        double ix=x+half,iy=y+half,iw=Math.max(0,w-bw),ih=Math.max(0,h-bw),ir=Math.max(0,r-half);
        int seg=curveSegments(Math.max(or,ir));
        ArrayList<double[]> outer=roundRectPath(ox,oy,ow,oh,or,seg);
        ArrayList<double[]> inner=roundRectPath(ix,iy,iw,ih,ir,seg);
        int n=Math.min(outer.size(),inner.size()); if(n<2)return;
        beginFlat(c);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for(int i=0;i<=n;i++){ int idx=i%n; double[] o=outer.get(idx), in=inner.get(idx); GL11.glVertex2d(o[0],o[1]); GL11.glVertex2d(in[0],in[1]); }
        GL11.glEnd();
        endFlat();
    }
    private void addArc(double cx,double cy,double r,double startDeg,double endDeg){ int seg=curveSegments(r); for(int i=0;i<=seg;i++){ double a=Math.toRadians(startDeg+(endDeg-startDeg)*i/seg); GL11.glVertex2d(cx+Math.cos(a)*r,cy+Math.sin(a)*r); } }
    private void drawRectOutline(double x,double y,double w,double h,double bw,int c){
        if(w<=0||h<=0||bw<=0||((c>>>24)&255)<=0)return; double half=bw/2.0;
        beginFlat(c);
        drawQuadRaw(x-half,y-half,x+w+half,y+half);
        drawQuadRaw(x-half,y+h-half,x+w+half,y+h+half);
        drawQuadRaw(x-half,y+half,x+half,y+h-half);
        drawQuadRaw(x+w-half,y+half,x+w+half,y+h-half);
        endFlat();
    }
    private void drawQuadRaw(double x1,double y1,double x2,double y2){ GL11.glBegin(GL11.GL_QUADS); GL11.glVertex2d(x1,y1); GL11.glVertex2d(x2,y1); GL11.glVertex2d(x2,y2); GL11.glVertex2d(x1,y2); GL11.glEnd(); }
    private int circleSegments(double radius){ if(radius<=2)return 8; if(radius<=4)return 12; if(radius<=8)return 16; if(radius<=16)return 24; return 32; }
    private void drawFilledCircle(double cx,double cy,double r,int c){ drawFilledCircleMesh(cx,cy,r,c); }
    private void drawCircleOutline(double cx,double cy,double r,double bw,int c){ drawCircleOutlineMesh(cx,cy,r,bw,c); }
    private void drawFilledCircleMeshRaw(double cx,double cy,double r,int color,double alpha){ if(r<=0||alpha<=0.001)return; GL11.glBegin(GL11.GL_TRIANGLE_FAN); argb(color,alpha); GL11.glVertex2d(cx,cy); int seg=circleSegments(r); for(int i=0;i<=seg;i++){double a=2*Math.PI*i/seg; GL11.glVertex2d(cx+Math.cos(a)*r,cy+Math.sin(a)*r);} GL11.glEnd(); }
    private void drawFilledCircleMesh(double cx,double cy,double r,int c){ if(r<=0||((c>>>24)&255)<=0)return; beginFlat(c); GL11.glBegin(GL11.GL_TRIANGLE_FAN); GL11.glVertex2d(cx,cy); int seg=circleSegments(r); for(int i=0;i<=seg;i++){double a=2*Math.PI*i/seg; GL11.glVertex2d(cx+Math.cos(a)*r,cy+Math.sin(a)*r);} GL11.glEnd(); endFlat(); }
    private void drawCircleOutlineMesh(double cx,double cy,double r,double bw,int c){ if(r<=0||bw<=0||((c>>>24)&255)<=0)return; double outer=r+bw*.5,inner=Math.max(0,r-bw*.5); beginFlat(c); int seg=circleSegments(outer); GL11.glBegin(GL11.GL_TRIANGLE_STRIP); for(int i=0;i<=seg;i++){double a=2*Math.PI*i/seg,co=Math.cos(a),si=Math.sin(a); GL11.glVertex2d(cx+co*outer,cy+si*outer); GL11.glVertex2d(cx+co*inner,cy+si*inner);} GL11.glEnd(); endFlat(); }
    private void drawRect(double x,double y,double x2,double y2,int c){ if(((c>>>24)&255)<=0||x2<=x||y2<=y)return; beginFlat(c); drawQuadRaw(x,y,x2,y2); endFlat(); }
    private int withAlpha(int c,double a){ int alpha=(int)Math.max(0,Math.min(255,((c>>>24)&255)*a)); return (c&0x00FFFFFF)|(alpha<<24); } private double stateRadius(Model.InputStyle st,Model.StateStyle s){ return s.cornerRadius==null?st.cornerRadius:s.cornerRadius; } private double stateBorder(Model.InputStyle st,Model.StateStyle s){ return s.borderWidth==null?st.borderWidth:s.borderWidth; } private double scaledBorder(double bw,double groupScale){ if(bw<=0)return 0; return Math.max(.5,bw*Math.max(.01,groupScale)); } private double smoothstep(double t){ t=Model.clamp(t,0,1); return t*t*(3.0-2.0*t); } private double easeOutCubic(double t){ t=Model.clamp(t,0,1); double u=1.0-t; return 1.0-u*u*u; } private double lerp(double a,double b,double t){ return a+(b-a)*Model.clamp(t,0,1); } private void argb(int c,double mul){ mul=Model.clamp(mul,0,1); float a=(float)(((c>>>24)&255)/255.0*mul), r=(float)((c>>16&255)/255.0), g=(float)((c>>8&255)/255.0), b=(float)((c&255)/255.0); GL11.glColor4f(r,g,b,a); } private double smooth(double x){return x*x*(3-2*x);} private double mod(double a,double b){double m=a%b;return m<0?m+b:m;} private int lerpColor(int a,int b,double t){int aa=(a>>>24)&255,ar=(a>>>16)&255,ag=(a>>>8)&255,ab=a&255, ba=(b>>>24)&255,br=(b>>>16)&255,bg=(b>>>8)&255,bb=b&255; int ca=(int)(aa+(ba-aa)*t),cr=(int)(ar+(br-ar)*t),cg=(int)(ag+(bg-ag)*t),cb=(int)(ab+(bb-ab)*t); return ca<<24|cr<<16|cg<<8|cb; }
    private static final class AnimState{boolean down;long pressAt,releaseAt;double releaseFrom,releaseAlpha;} public static final class GroupBox{ public final String key,id; public final double x,y,w,h; public final Model.GameAdjust adjust; GroupBox(String key,String id,double x,double y,double w,double h,Model.GameAdjust adjust){this.key=key;this.id=id;this.x=x;this.y=y;this.w=w;this.h=h;this.adjust=adjust;} public boolean contains(int mx,int my){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;} } private static final class TrailState{boolean initialized;double cx,cy,drawCx=Double.NaN,drawCy=Double.NaN,worldX,worldY,viewX,viewY;long lastNs,lastInputNs;LinkedList<TrailPoint> points=new LinkedList<>();} private static final class TrailPoint{double x,y;long timeNs;boolean left,right,breakBefore;TrailPoint(double x,double y,long timeNs,boolean left,boolean right,boolean breakBefore){this.x=x;this.y=y;this.timeNs=timeNs;this.left=left;this.right=right;this.breakBefore=breakBefore;}} private static final class TrailSample{double x,y;long timeNs;boolean left,right;double age01,fade,width;int color;} private static final class Clip{boolean was;Clip(boolean w){was=w;}} private static final class ShapeClip{boolean wasStencil,tex,alpha,cull;int func,ref,valueMask,writeMask,fail,zfail,zpass;ShapeClip(boolean ws,boolean tex,boolean alpha,boolean cull,int func,int ref,int valueMask,int writeMask,int fail,int zfail,int zpass){this.wasStencil=ws;this.tex=tex;this.alpha=alpha;this.cull=cull;this.func=func;this.ref=ref;this.valueMask=valueMask;this.writeMask=writeMask;this.fail=fail;this.zfail=zfail;this.zpass=zpass;}}
}
