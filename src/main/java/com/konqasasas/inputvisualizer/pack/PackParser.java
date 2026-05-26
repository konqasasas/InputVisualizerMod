package com.konqasasas.inputvisualizer.pack;

import com.google.gson.*;
import com.konqasasas.inputvisualizer.model.Model;
import org.lwjgl.input.Keyboard;
import java.util.*;

public final class PackParser {
    private final ArrayList<String> warnings = new ArrayList<>();
    private JsonObject theme = new JsonObject();
    private JsonObject tokens = new JsonObject();
    private JsonObject styles = new JsonObject();
    private final HashSet<String> ids = new HashSet<>();
    private int order = 0;

    public Model.Layout parse(JsonObject bundle) {
        Model.Layout out = new Model.Layout();
        if (bundle == null) { out.warnings.add("bundle is null"); return out; }
        theme = obj(bundle, "theme"); tokens = obj(theme, "tokens"); styles = obj(theme, "styles"); out.theme = theme;
        JsonObject profile = obj(bundle, "profile"); JsonObject canvas = obj(profile, "canvas");
        out.referenceWidth = clampi(num(canvas, "referenceWidth", 854), 160, 8192);
        out.referenceHeight = clampi(num(canvas, "referenceHeight", 480), 90, 8192);
        JsonArray els = arr(profile, "elements");
        for (JsonElement e : els) if (e.isJsonObject()) out.roots.add(parseElement(e.getAsJsonObject(), null));
        out.warnings.addAll(warnings);
        return out;
    }

    private Model.Element parseElement(JsonObject o, Model.Element parent) {
        Model.Element e = new Model.Element();
        e.id = str(o, "id", "");
        if (!e.id.isEmpty() && !ids.add(e.id)) warn("duplicate id: " + e.id);
        e.order = order++;
        String t = str(o, "type", "");
        e.kind = ("group".equals(t)) ? Model.ElementKind.GROUP : (("input".equals(t)||"key".equals(t)||"mouse_button".equals(t)) ? Model.ElementKind.INPUT : (("mouse_pad".equals(t)||"mousePad".equals(t)) ? Model.ElementKind.MOUSE_PAD : Model.ElementKind.UNKNOWN));
        e.anchor = Model.Anchor.from(str(o, "anchor", parent == null ? "top_left" : "top_left"));
        e.x = num(o, "x", 0); e.y = num(o, "y", 0); e.width = Math.max(1, num(o, "width", 40)); e.height = Math.max(1, num(o, "height", 20));
        e.scale = Model.clamp(num(o, "scale", 1), .01, 100); e.opacity = Model.clamp(num(o, "opacity", 1), 0, 1);
        e.zIndex = num(o, "zIndex", 0);
        e.gameAdjust = parseGameAdjust(obj(o, "gameAdjust"), e);
        if (e.kind == Model.ElementKind.INPUT) {
            e.input = parseInput(obj(o, "input"));
            e.label = o.has("label") && !o.get("label").isJsonNull() ? o.get("label").getAsString() : null;
            e.inputStyle = parseInputStyle(mergeStyle(o));
        } else if (e.kind == Model.ElementKind.MOUSE_PAD) {
            e.mousePad = parseMousePadStyle(mergeMousePadStyle(o), e.id);
        } else if (e.kind == Model.ElementKind.UNKNOWN) warn("unknown element type: " + t);
        JsonArray ch = arr(o, "children");
        for (JsonElement c : ch) if (c.isJsonObject()) e.children.add(parseElement(c.getAsJsonObject(), e));
        return e;
    }

    // Match the web editor: theme.styles[styleRef] -> element.style -> allowed top-level style shorthands.
    // Do NOT deep-merge the whole element, because type/x/y/input/children must not become style fields.
    private JsonObject mergeStyle(JsonObject element) {
        JsonObject base = new JsonObject();
        JsonObject styleObj = obj(element, "style");
        String ref = str(element, "styleRef", str(styleObj, "styleRef", ""));
        if (!ref.isEmpty()) {
            JsonObject st = styles.has(ref) && styles.get(ref).isJsonObject() ? styles.getAsJsonObject(ref) : null;
            if (st == null) warn("missing styleRef: " + ref); else deepMerge(base, st);
        }
        if (element.has("style") && element.get("style").isJsonObject()) deepMerge(base, element.getAsJsonObject("style"));
        String[] allow = new String[]{"styleRef","shape","cornerRadius","fillMode","fillColor","borderColor","borderWidth","textColor","opacity","scale","offsetX","offsetY","fontScale","textShadow","shadowText","horizontalAlign","verticalAlign","hAlign","vAlign","textOffsetX","textOffsetY","shadow","glow","normal","pressed","disabled","pressAnimation","releaseEffect","background","trail","contentPadding","clipShape"};
        for (String k : allow) if (element.has(k) && !"style".equals(k)) base.add(k, copy(element.get(k)));
        resolveTokens(base);
        return base;
    }

    private JsonObject mergeMousePadStyle(JsonObject element) {
        JsonObject base = new JsonObject();
        JsonObject styleObj = obj(element, "style");
        String ref = str(element, "styleRef", str(styleObj, "styleRef", ""));
        if (!ref.isEmpty()) {
            JsonObject st = styles.has(ref) && styles.get(ref).isJsonObject() ? styles.getAsJsonObject(ref) : null;
            if (st == null) warn("missing styleRef: " + ref); else deepMerge(base, st);
        }
        if (element.has("style") && element.get("style").isJsonObject()) deepMerge(base, element.getAsJsonObject("style"));
        String[] allow = new String[]{"styleRef","shape","cornerRadius","fillMode","fillColor","borderColor","borderWidth","opacity","contentPadding","clipShape"};
        for (String k : allow) if (element.has(k) && !"style".equals(k)) base.add(k, copy(element.get(k)));

        JsonObject bg = new JsonObject();
        if (base.has("background") && base.get("background").isJsonObject()) deepMerge(bg, base.getAsJsonObject("background"));
        if (element.has("background") && element.get("background").isJsonObject()) deepMerge(bg, element.getAsJsonObject("background"));
        if (bg.entrySet().size() > 0) base.add("background", bg);

        JsonObject trail = new JsonObject();
        if (base.has("trail") && base.get("trail").isJsonObject()) deepMerge(trail, base.getAsJsonObject("trail"));
        if (element.has("trail") && element.get("trail").isJsonObject()) deepMerge(trail, element.getAsJsonObject("trail"));
        if (trail.entrySet().size() > 0) base.add("trail", trail);

        resolveTokens(base);
        return base;
    }

    private void deepMerge(JsonObject into, JsonObject from) {
        for (Map.Entry<String, JsonElement> en : from.entrySet()) {
            if (into.has(en.getKey()) && into.get(en.getKey()).isJsonObject() && en.getValue().isJsonObject()) deepMerge(into.getAsJsonObject(en.getKey()), en.getValue().getAsJsonObject());
            else into.add(en.getKey(), copy(en.getValue()));
        }
    }
    private JsonElement copy(JsonElement e) { return new JsonParser().parse(e.toString()); }
    private void resolveTokens(JsonElement e) {
        if (e == null) return;
        if (e.isJsonObject()) for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
            JsonElement v = en.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) en.setValue(new JsonPrimitive(resolveTokenString(v.getAsString())));
            else resolveTokens(v);
        } else if (e.isJsonArray()) for (JsonElement c : e.getAsJsonArray()) resolveTokens(c);
    }
    private String resolveTokenString(String v) {
        if (v == null) return null;
        if (v.startsWith("$")) return token(v.substring(1), v);
        if (v.startsWith("token:")) return token(v.substring(6), v);
        if (v.startsWith("{") && v.endsWith("}")) return token(v.substring(1, v.length()-1), v);
        return v;
    }
    private String tokenString(JsonObject o, String k, String d) { String v = str(o, k, d); return resolveTokenString(v); }
    private String token(String name, String fallback) { if (tokens.has(name) && tokens.get(name).isJsonPrimitive()) return tokens.get(name).getAsString(); warn("missing token: " + name); return fallback; }

    private Model.Input parseInput(JsonObject i) {
        Model.Input in = new Model.Input(); String t = str(i, "type", "");
        if ("keyBinding".equals(t)) { in.kind=Model.InputKind.KEY_BINDING; in.name=str(i,"name",""); in.autoLabel=in.name.replace("key.","").toUpperCase(Locale.ROOT); }
        else if ("keyCode".equals(t)) { in.kind=Model.InputKind.KEY_CODE; String c=str(i,"code",""); in.name=c; in.keyCode=keyCode(c); in.autoLabel=c.toUpperCase(Locale.ROOT); if(in.keyCode==Keyboard.KEY_NONE) warn("invalid keyCode: "+c); }
        else if ("mouseButton".equals(t)) { in.kind=Model.InputKind.MOUSE_BUTTON; String b=str(i,"button",""); in.name=b; in.mouseButton=mouseButton(b); in.autoLabel=autoMouse(b); if(in.mouseButton<-1) warn("invalid mouseButton: "+b); }
        else warn("invalid input type: " + t);
        return in;
    }
    private int mouseButton(String b){ if("left".equals(b))return 0; if("right".equals(b))return 1; if("middle".equals(b))return 2; if("button4".equals(b))return 3; if("button5".equals(b))return 4; return -100; }
    private int keyCode(String code){ String c=code==null?"":code.trim().toUpperCase(Locale.ROOT); if("CTRL".equals(c)||"CONTROL".equals(c))return Keyboard.KEY_LCONTROL; if("SHIFT".equals(c))return Keyboard.KEY_LSHIFT; if("SPACE".equals(c))return Keyboard.KEY_SPACE; return Keyboard.getKeyIndex(c); }
    private String autoMouse(String b){ return "left".equals(b)?"LMB":"right".equals(b)?"RMB":"middle".equals(b)?"MMB":b.toUpperCase(Locale.ROOT); }

    private Model.GameAdjust parseGameAdjust(JsonObject o, Model.Element e) {
        Model.GameAdjust g = new Model.GameAdjust();
        boolean explicit = o != null && o.entrySet().size() > 0;
        g.enabled = explicit ? bool(o,"enabled",true) : e.kind == Model.ElementKind.GROUP && e.id != null && !e.id.isEmpty();
        g.storageKey = str(o,"storageKey",e.id == null ? "" : e.id);
        g.allowMove = bool(o,"allowMove",true); g.allowScale = bool(o,"allowScale",true); g.allowOpacity = bool(o,"allowOpacity",true); g.lockAnchor = bool(o,"lockAnchor",true);
        g.minScale = Model.clamp(num(o,"minScale",.5),.05,20); g.maxScale = Model.clamp(num(o,"maxScale",3),g.minScale,20);
        return g;
    }

    private Model.InputStyle parseInputStyle(JsonObject o) {
        Model.InputStyle s = new Model.InputStyle();
        JsonObject normalObj = obj(o, "normal");
        s.styleRef = str(o, "styleRef", "");
        s.shape = Model.Shape.from(str(o,"shape",str(normalObj,"shape","rounded_rectangle")));
        s.fillMode = Model.FillMode.from(str(o,"fillMode",str(normalObj,"fillMode","filled_outline")));
        s.cornerRadius = Model.clamp(num(o,"cornerRadius",num(normalObj,"cornerRadius",6)),0,1000);
        s.borderWidth = Model.clamp(num(o,"borderWidth",num(normalObj,"borderWidth",1)),0,100);
        s.fontScale = Model.clamp(num(o,"fontScale",1),.05,10); s.textOffsetX=num(o,"textOffsetX",0); s.textOffsetY=num(o,"textOffsetY",0);
        s.hAlign = normAlign(str(o,"horizontalAlign",str(o,"hAlign","center"))); s.vAlign = normVAlign(str(o,"verticalAlign",str(o,"vAlign","middle")));
        s.textShadow = bool(o,"textShadow",bool(o,"shadowText",true));
        s.shadow = parseShadow(obj(o,"shadow"), s.shadow); s.glow = parseGlow(obj(o,"glow"), s.glow);
        s.normal = parseState(obj(o,"normal"), s.normal); s.pressed = parseState(obj(o,"pressed"), s.pressed); s.disabled = parseState(obj(o,"disabled"), s.disabled);
        JsonObject pa = obj(o,"pressAnimation"); s.press.enabled=bool(pa,"enabled",s.press.enabled); s.press.type=Model.PressAnim.from(str(pa,"type",s.press.type.name().toLowerCase(Locale.ROOT))); s.press.durationMs=clampi(num(pa,"durationMs",(int)s.press.durationMs),1,5000); s.press.scale=Model.clamp(num(pa,"scale",s.press.scale),.01,10); s.press.offsetX=num(pa,"offsetX",s.press.offsetX); s.press.offsetY=num(pa,"offsetY",s.press.offsetY);
        JsonObject re = obj(o,"releaseEffect"); s.release.type=Model.ReleaseEffect.from(str(re,"type",s.release.type.name().toLowerCase(Locale.ROOT))); s.release.durationMs=clampi(num(re,"durationMs",(int)s.release.durationMs),1,5000); s.release.color=color(re,"color",s.release.color); s.release.alpha=Model.clamp(num(re,"alpha",s.release.alpha),0,1); s.release.size=Model.clamp(num(re,"size",s.release.size),0,200);
        return s;
    }
    private String normAlign(String s){ if("start".equals(s))return "left"; if("end".equals(s))return "right"; return s; }
    private String normVAlign(String s){ if("center".equals(s))return "middle"; return s; }

    private Model.MousePadStyle parseMousePadStyle(JsonObject o, String id) {
        Model.MousePadStyle s = new Model.MousePadStyle();
        s.styleRef=str(o,"styleRef",""); s.shape=Model.Shape.from(str(o,"shape","rounded_rectangle")); s.fillMode=Model.FillMode.from(str(o,"fillMode","filled_outline")); s.clipShape=Model.ClipShape.from(str(o,"clipShape","visualShape")); s.opacity=Model.clamp(num(o,"opacity",s.opacity),0,1);
        s.cornerRadius=Model.clamp(num(o,"cornerRadius",10),0,1000); s.borderWidth=Model.clamp(num(o,"borderWidth",1),0,100); s.contentPadding=Model.clamp(num(o,"contentPadding",4),0,400);
        JsonObject bgObj=obj(o,"background");
        s.fillColor=color(o,"fillColor",s.fillColor); s.borderColor=color(o,"borderColor",s.borderColor); s.background=parseBackground(bgObj); s.trail=parseTrail(obj(o,"trail"));
        if(s.trail.mode==Model.TrailMode.PAN && !hasAny(bgObj,"scrollMode","scroll","backgroundScrollMode")) s.background.scroll=Model.ScrollMode.WORLD;
        warn("MousePad[id="+(id==null||id.isEmpty()?"<anonymous>":id)+"] background resolved: type="+s.background.type+", opacity="+s.background.opacity+", scroll="+s.background.scroll+", gridSize="+s.background.gridSize+", lineWidth="+s.background.lineWidth+", lineColor="+String.format("0x%08X",s.background.lineColor)+", cellSize="+s.background.cellSize+", spacing="+s.background.spacing+", dotSize="+s.background.dotSize+", path="+s.background.path);
        return s;
    }
    private Model.Background parseBackground(JsonObject o) {
        Model.Background b=new Model.Background();
        b.type=parseBackgroundKind(str(o,"type",str(o,"kind",str(o,"backgroundType","checkerboard")))); b.fit=Model.ImageFit.from(str(o,"imageFit",str(o,"fit","cover"))); b.scroll=Model.ScrollMode.from(str(o,"scrollMode",str(o,"scroll",str(o,"backgroundScrollMode","fixed")))); b.opacity=Model.clamp(num(o,"backgroundOpacity",num(o,"opacity",num(o,"alpha",b.opacity))),0,1);
        b.cellSize=Model.clamp(num(o,"cellSize",num(o,"size",b.cellSize)),1,1024); b.gridSize=Model.clamp(num(o,"gridSize",num(o,"size",b.gridSize)),1,1024); b.lineWidth=Model.clamp(num(o,"lineWidth",b.lineWidth),.1,100); b.spacing=Model.clamp(num(o,"spacing",num(o,"dotSpacing",num(o,"size",b.spacing))),1,1024); b.dotSize=Model.clamp(num(o,"dotSize",num(o,"dotRadius",b.dotSize)),.1,200);
        b.colorA=color(o,"colorA",b.colorA); b.colorB=color(o,"colorB",b.colorB); b.lineColor=color(o,"lineColor",color(o,"color",b.lineColor)); b.dotColor=color(o,"dotColor",color(o,"color",b.dotColor)); b.path=str(o,"path",str(o,"imagePath",b.path));
        if(b.type==Model.BackgroundKind.IMAGE && !PackPathGuard.isSafeTexturePath(b.path)){ warn("unsafe texture path: "+b.path); b.type=Model.BackgroundKind.CHECKERBOARD; b.fallbackCheckerboard=true; }
        return b;
    }
    private Model.BackgroundKind parseBackgroundKind(String v) {
        Model.BackgroundKind k = Model.BackgroundKind.from(v);
        if(k==Model.BackgroundKind.NONE && v!=null && !v.trim().isEmpty() && !"none".equalsIgnoreCase(v.trim())) warn("unknown background type: "+v+", using none");
        return k;
    }
    private Model.Trail parseTrail(JsonObject o) {
        Model.Trail t=new Model.Trail();
        t.enabled=bool(o,"enabled",true); t.mode=Model.TrailMode.from(str(o,"mode","wrap")); t.follow=Model.FollowMode.from(str(o,"followMode","instant")); t.reset=Model.ResetMode.from(str(o,"resetMode","center_on_empty")); t.smoothing=Model.Smoothing.from(str(o,"smoothing","catmull_rom"));
        JsonObject cursorObj=obj(o,"cursor"); JsonObject dotsObj=obj(o,"dots"); JsonObject layers=obj(o,"layers");
        t.cursorKind=Model.CursorKind.from(str(cursorObj,"type",str(o,"cursor",str(o,"cursorType","dot")))); t.colorMode=Model.ColorMode.from(str(o,"colorMode","fixed"));
        t.sensitivity=Model.clamp(num(o,"sensitivity",t.sensitivity),.001,50); t.lifetimeMs=Model.clamp(num(o,"lifetimeMs",t.lifetimeMs),16,30000); t.baseWidth=Model.clamp(num(o,"baseWidth",t.baseWidth),.1,100); t.tailWidth=Model.clamp(num(o,"tailWidth",t.tailWidth),.1,100);
        t.glow=bool(layers,"glow",bool(o,"glow",t.glow)); t.line=bool(layers,"line",bool(o,"line",t.line)); t.dots=bool(layers,"dots",bool(dotsObj,"enabled",bool(o,"dots",t.dots))); t.cursor=bool(layers,"cursor",bool(cursorObj,"enabled",bool(o,"showCursor",t.cursor)));
        t.dotSpacing=Model.clamp(num(dotsObj,"spacing",num(o,"dotSpacing",t.dotSpacing)),1,200); t.dotSize=Model.clamp(num(dotsObj,"size",num(o,"dotSize",t.dotSize)),.1,60); t.cursorSize=Model.clamp(num(cursorObj,"size",num(o,"cursorSize",t.cursorSize)),.1,80);
        t.deadZoneRatio=Model.clamp(num(o,"deadZoneRatio",t.deadZoneRatio),.05,1); t.followResponsiveness=Model.clamp(num(o,"followResponsiveness",t.followResponsiveness),.1,120);
        t.color=color(o,"color",t.color); t.glowColor=color(o,"glowColor",t.glowColor); t.glowWidthMultiplier=Model.clamp(num(o,"glowWidthMultiplier",t.glowWidthMultiplier),.1,20); t.tailColor=color(o,"tailColor",t.tailColor); t.dotColor=color(dotsObj,"color",t.dotColor); t.cursorColor=color(cursorObj,"color",t.cursorColor);
        t.maxPoints=Model.clampi(num(o,"maxPoints",t.maxPoints),8,4000); t.maxRenderedSamples=Model.clampi(num(o,"maxRenderedSamples",t.maxRenderedSamples),8,2048); t.maxSmoothingSamples=Model.clampi(num(o,"maxSmoothingSamples",t.maxSmoothingSamples),8,3000);
        parseHi(obj(o,"leftHighlight"),t.left); parseHi(obj(o,"lmbHighlight"),t.left); parseHi(obj(o,"rightHighlight"),t.right); parseHi(obj(o,"rmbHighlight"),t.right);
        return t;
    }
    private void parseHi(JsonObject o, Model.Highlight h){ h.color=color(o,"color",h.color); h.widthMultiplier=Model.clamp(num(o,"widthMultiplier",h.widthMultiplier),.1,10); h.glowMultiplier=Model.clamp(num(o,"glowMultiplier",h.glowMultiplier),.1,10); }
    private Model.Shadow parseShadow(JsonObject o, Model.Shadow d){ d.enabled=bool(o,"enabled",d.enabled); d.offsetX=num(o,"offsetX",d.offsetX); d.offsetY=num(o,"offsetY",d.offsetY); d.color=color(o,"color",d.color); d.alpha=Model.clamp(num(o,"alpha",d.alpha),0,1); return d; }
    private Model.Glow parseGlow(JsonObject o, Model.Glow d){ d.enabled=bool(o,"enabled",d.enabled); d.color=color(o,"color",d.color); d.alpha=Model.clamp(num(o,"alpha",d.alpha),0,1); d.size=Model.clamp(num(o,"size",d.size),0,200); return d; }
    private Model.StateStyle parseState(JsonObject o, Model.StateStyle d){ d.fill=color(o,"fillColor",d.fill); d.border=color(o,"borderColor",d.border); d.text=color(o,"textColor",d.text); if(o.has("shape")) d.shape=Model.Shape.from(str(o,"shape","")); if(o.has("fillMode")) d.fillMode=Model.FillMode.from(str(o,"fillMode","")); if(o.has("cornerRadius")) d.cornerRadius=Model.clamp(num(o,"cornerRadius",d.cornerRadius==null?0:d.cornerRadius),0,1000); if(o.has("borderWidth")) d.borderWidth=Model.clamp(num(o,"borderWidth",d.borderWidth==null?0:d.borderWidth),0,100); d.opacity=Model.clamp(num(o,"opacity",d.opacity),0,1); d.scale=Model.clamp(num(o,"scale",d.scale),.01,10); d.offsetX=num(o,"offsetX",d.offsetX); d.offsetY=num(o,"offsetY",d.offsetY); if(o.has("textShadow")) d.textShadow=bool(o,"textShadow",d.textShadow==null?true:d.textShadow); d.glow=parseGlow(obj(o,"glow"),d.glow); return d; }

    // Web uses #RRGGBBAA. Mod stores 0xAARRGGBB.
    private int color(JsonObject o, String k, int d) {
        String s = tokenString(o,k,null); if (s == null) return d;
        try {
            if (s.startsWith("#")) {
                if (s.length()==7) { int rgb=Integer.parseInt(s.substring(1),16); return 0xFF000000 | rgb; }
                if (s.length()==9) { long rgba=Long.parseLong(s.substring(1),16); int rr=(int)((rgba>>24)&255),gg=(int)((rgba>>16)&255),bb=(int)((rgba>>8)&255),aa=(int)(rgba&255); return (aa<<24)|(rr<<16)|(gg<<8)|bb; }
            }
            if (s.startsWith("0x")||s.startsWith("0X")) { long v=Long.parseLong(s.substring(2),16); if(s.length()<=8) v|=0xFF000000L; return (int)v; }
            return (int)Long.parseLong(s);
        } catch(Exception e) { warn("invalid color for "+k+": "+s); return 0xFFFF00FF; }
    }

    private JsonObject obj(JsonObject o,String k){ return o!=null&&o.has(k)&&o.get(k).isJsonObject()?o.getAsJsonObject(k):new JsonObject(); }
    private JsonArray arr(JsonObject o,String k){ return o!=null&&o.has(k)&&o.get(k).isJsonArray()?o.getAsJsonArray(k):new JsonArray(); }
    private String str(JsonObject o,String k,String d){ try{ if(o!=null&&o.has(k)&&!o.get(k).isJsonNull()&&o.get(k).isJsonPrimitive()) return o.get(k).getAsString(); }catch(Exception ignored){} return d; }
    private boolean bool(JsonObject o,String k,boolean d){ try{ if(o!=null&&o.has(k)&&!o.get(k).isJsonNull()&&o.get(k).isJsonPrimitive()) return o.get(k).getAsBoolean(); }catch(Exception ignored){} return d; }
    private int num(JsonObject o,String k,int d){ return (int)Math.round(num(o,k,(double)d)); }
    private double num(JsonObject o,String k,double d){ try{ return o!=null&&o.has(k)&&!o.get(k).isJsonNull()&&o.get(k).isJsonPrimitive()?o.get(k).getAsDouble():d; }catch(Exception e){ return d; } }
    private boolean hasAny(JsonObject o,String... keys){ if(o==null)return false; for(String k:keys) if(o.has(k)&&!o.get(k).isJsonNull()) return true; return false; }
    private int clampi(int v,int a,int b){ return v<a?a:v>b?b:v; }
    private void warn(String s){ warnings.add(s); }
}
