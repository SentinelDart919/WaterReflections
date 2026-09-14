package b919.graphics;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.Pixmap.Format;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.math.Mat;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Drawc;
import mindustry.gen.EffectStatec;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.Category;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;

import static mindustry.Vars.tilesize;

/**
 * Snap-mirror reflection pass for water.
 * Captures a mirrored copy of nearby buildings, units, effects, and bullets
 * into a screen-sized buffer, which the water shader then samples.
 */
public class WaterReflections {
    public static FrameBuffer buffer;

    /** When true, the whole reflection system is switched off.*/
    public static boolean disabled;

    private static final Mat baseline = new Mat();

    public static final float reflectionGroundGap = 3f;
    public static final float reflectionFlyerGap = 10f;

    public static final Color refTint = new Color(0x29619bff);
    public static final float refTintAmount = 0.42f;
    public static final float refOpacity = 0.9f;

    // per-block override (content mods can use set(); user config adds to this too)
    private static final ObjectMap<Block, ReflectConfig> config = new ObjectMap<>();
    // per-category config (player-editable via settings dialog)
    private static final ObjectMap<Category, ReflectConfig> categoryConfig = new ObjectMap<>();
    // per-unit-type config (player-editable via settings dialog)
    private static final ObjectMap<UnitType, ReflectConfig> unitConfig = new ObjectMap<>();
    // all-units override
    private static ReflectConfig unitGroupConfig;
    // global override for all blocks and units
    private static ReflectConfig globalConfig;

    private static int reflectErrors;

    /** Reads all persisted configs into memory. Call at ClientLoadEvent time. */
    public static void loadConfigs(){
        categoryConfig.clear();
        for(Category cat : Category.all){
            ReflectConfig c = ReflectConfig.read("b919-wr-cat-" + cat.name());
            if(c != null) categoryConfig.put(cat, c);
        }

        globalConfig = ReflectConfig.read("b919-wr-global");
        unitGroupConfig = ReflectConfig.read("b919-wr-units");

        config.clear();
        if(!Vars.headless){
            for(Block block : Vars.content.blocks()){
                ReflectConfig c = ReflectConfig.read("b919-wr-block-" + block.name);
                if(c != null) config.put(block, c);
            }
        }

        unitConfig.clear();
        if(!Vars.headless){
            for(UnitType type : Vars.content.units()){
                ReflectConfig c = ReflectConfig.read("b919-wr-unit-" + type.name);
                if(c != null) unitConfig.put(type, c);
            }
        }
    }

    public static void saveCategory(Category cat, ReflectConfig c){
        categoryConfig.put(cat, c);
        c.write("b919-wr-cat-" + cat.name());
    }

    public static void resetCategory(Category cat){
        categoryConfig.remove(cat);
        ReflectConfig.clearKeys("b919-wr-cat-" + cat.name());
    }

    /** Returns a copy of the effective config for a category (stored -> category default override -> global -> defaults). */
    public static ReflectConfig effective(Category cat){
        ReflectConfig c = categoryConfig.get(cat);
        if(c != null) return c.copy();
        ReflectConfig d = defaultConfigFor(cat);
        if(isOverride(d)) return d.copy();
        if(globalConfig != null) return globalConfig.copy();
        return d;
    }

    //global override (all blocks and units)
    public static ReflectConfig globalFor(){
        return globalConfig == null ? new ReflectConfig() : globalConfig.copy();
    }

    public static void saveGlobal(ReflectConfig c){
        globalConfig = c;
        c.write("b919-wr-global");
    }

    public static void resetGlobal(){
        globalConfig = null;
        ReflectConfig.clearKeys("b919-wr-global");
    }

    public static boolean isGlobalCustom(){
        return globalConfig != null;
    }

    //all-units override
    /** Returns the effective all-units config (stored -> unflipped unit default -> global). */
    public static ReflectConfig unitGroupEffective(){
        if(unitGroupConfig != null) return unitGroupConfig.copy();
        ReflectConfig d = defaultForUnits();
        if(isOverride(d)) return d.copy();
        if(globalConfig != null) return globalConfig.copy();
        return d;
    }

    public static void saveUnitGroup(ReflectConfig c){
        unitGroupConfig = c;
        c.write("b919-wr-units");
    }

    public static void resetUnitGroup(){
        unitGroupConfig = null;
        ReflectConfig.clearKeys("b919-wr-units");
    }

    public static boolean isUnitGroupCustom(){
        return unitGroupConfig != null && !equalsConfig(unitGroupConfig, defaultForUnits());
    }

    //blocks

    public static void saveBlock(Block block, ReflectConfig c){
        config.put(block, c);
        c.write("b919-wr-block-" + block.name);
    }

    public static void resetBlock(Block block){
        config.remove(block);
        ReflectConfig.clearKeys("b919-wr-block-" + block.name);
    }

    /** Returns the effective config for a block (per-block -> per-category -> category default override -> global -> defaults). */
    public static ReflectConfig effective(Block block){
        ReflectConfig c = config.get(block);
        if(c != null) return c.copy();
        ReflectConfig cat = categoryConfig.get(block.category);
        if(cat != null) return cat.copy();
        ReflectConfig d = defaultConfigFor(block);
        if(isOverride(d)) return d.copy();
        if(globalConfig != null) return globalConfig.copy();
        return d;
    }
    //units
    public static void saveUnit(UnitType type, ReflectConfig c){
        unitConfig.put(type, c);
        c.write("b919-wr-unit-" + type.name);
    }

    public static void resetUnit(UnitType type){
        unitConfig.remove(type);
        ReflectConfig.clearKeys("b919-wr-unit-" + type.name);
    }

    /** Returns a copy of the effective config for a unit type (per-unit -> all-units -> unflipped default -> global -> defaults). */
    public static ReflectConfig effective(UnitType type){
        ReflectConfig c = unitConfig.get(type);
        if(c != null) return c.copy();
        if(unitGroupConfig != null) return unitGroupConfig.copy();
        ReflectConfig d = defaultConfigFor(type);
        if(isOverride(d)) return d.copy();
        if(globalConfig != null) return globalConfig.copy();
        return d;
    }

    //custom checks

    private static ReflectConfig blockBaseline(Block block){
        ReflectConfig cat = categoryConfig.get(block.category);
        if(cat != null) return cat;
        ReflectConfig d = defaultConfigFor(block);
        if(isOverride(d)) return d;
        if(globalConfig != null) return globalConfig;
        return d;
    }

    /** Whether the block has a stored override differing from what it would use otherwise. */
    public static boolean isCustom(Block block){
        ReflectConfig c = config.get(block);
        return c != null && !equalsConfig(c, blockBaseline(block));
    }

    private static ReflectConfig categoryBaseline(Category cat){
        ReflectConfig d = defaultConfigFor(cat);
        if(isOverride(d)) return d;
        if(globalConfig != null) return globalConfig;
        return d;
    }

    /** Whether the category has a stored override differing from what it would use otherwise. */
    public static boolean isCustom(Category cat){
        ReflectConfig c = categoryConfig.get(cat);
        return c != null && !equalsConfig(c, categoryBaseline(cat));
    }

    private static ReflectConfig unitBaseline(UnitType type){
        if(unitGroupConfig != null) return unitGroupConfig;
        ReflectConfig d = defaultConfigFor(type);
        if(isOverride(d)) return d;
        if(globalConfig != null) return globalConfig;
        return d;
    }

    /** Whether the unit type has a stored override differing from what it would use otherwise. */
    public static boolean isCustom(UnitType type){
        ReflectConfig c = unitConfig.get(type);
        return c != null && !equalsConfig(c, unitBaseline(type));
    }

    //content mod API (if any mod want to use this as optional dependency)
    public static void set(Block block, float xdisplace, float ydisplace, boolean flip){
        ReflectConfig c = config.get(block);
        if(c == null) config.put(block, c = new ReflectConfig());
        c.reflectXdisplace = xdisplace;
        c.reflectYdisplace = ydisplace;
        c.reflectionFlip = flip;
    }
    //defaults
    private static boolean isOverride(ReflectConfig d){
        return !equalsConfig(d, new ReflectConfig());
    }

    public static boolean equalsConfig(ReflectConfig a, ReflectConfig b){
        return a.reflectXdisplace == b.reflectXdisplace
            && a.reflectYdisplace == b.reflectYdisplace
            && a.reflectionFlip == b.reflectionFlip
            && a.rotationDeg == b.rotationDeg
            && a.positionX == b.positionX
            && a.positionY == b.positionY;
    }

    private static ReflectConfig defaultConfigFor(Category cat){
        ReflectConfig d = new ReflectConfig();
        if(cat == Category.distribution || cat == Category.liquid){
            d.reflectYdisplace = 0.35f;
        }else if(cat == Category.turret){
            d.reflectionFlip = false;
        }
        return d;
    }

    private static ReflectConfig defaultConfigFor(Block block){
        return defaultConfigFor(block.category);
    }

    private static ReflectConfig defaultForUnits(){
        ReflectConfig d = new ReflectConfig();
        d.reflectionFlip = false;
        return d;
    }

    private static ReflectConfig defaultConfigFor(UnitType type){
        return defaultForUnits();
    }
    //render
    public static void captureScreen(){
        if(disabled || Core.graphics.getWidth() <= 0 || Core.graphics.getHeight() <= 0) return;

        if(buffer == null){
            buffer = new FrameBuffer(Format.rgba8888, Core.graphics.getWidth(), Core.graphics.getHeight(), false);
        }else{
            buffer.resizeCheck(Core.graphics.getWidth(), Core.graphics.getHeight());
        }

        float halfW = Core.camera.width / 2f;
        float halfH = Core.camera.height / 2f;
        float cx = Core.camera.position.x;
        float cy = Core.camera.position.y;
        float margin = tilesize * 8f;

        Draw.sort(false);
        buffer.begin(Color.clear);
        try{
            Draw.reset();
            Draw.z(0);

            forEachBuildIn(cx, cy, halfW, halfH, margin, WaterReflections::drawReflected);

            Groups.unit.each(u -> !u.dead() && Math.abs(u.x() - cx) <= halfW + margin && Math.abs(u.y() - cy) <= halfH + margin,
                WaterReflections::drawReflectedUnit);

            Groups.draw.each(e -> e instanceof EffectStatec
                    && Math.abs(e.x() - cx) <= halfW + margin && Math.abs(e.y() - cy) <= halfH + margin,
                WaterReflections::drawReflectedMoving);

            Groups.bullet.each(b -> Math.abs(b.x() - cx) <= halfW + margin && Math.abs(b.y() - cy) <= halfH + margin,
                WaterReflections::drawReflectedMoving);
        }finally{
            Draw.flush();
            Draw.reset();
            Draw.z(0);
            Draw.trans(baseline);
            buffer.end();
            Draw.sort(true);
        }
    }
    private static void forEachBuildIn(float cx, float cy, float halfW, float halfH, float margin, Cons<Building> cons){
        if(Vars.world == null || Vars.world.tiles == null) return;
        int x0 = Math.max(0, (int)Math.floor((cx - halfW - margin) / tilesize));
        int x1 = Math.min(Vars.world.width() - 1, (int)Math.floor((cx + halfW + margin) / tilesize));
        int y0 = Math.max(0, (int)Math.floor((cy - halfH - margin) / tilesize));
        int y1 = Math.min(Vars.world.height() - 1, (int)Math.floor((cy + halfH + margin) / tilesize));
        for(int x = x0; x <= x1; x++){
            for(int y = y0; y <= y1; y++){
                Tile tile = Vars.world.tile(x, y);
                Building b = tile.build;
                if(b != null && b.tile == tile) cons.get(b);
            }
        }
    }

    private static void drawReflected(Building b){
        try{
            Draw.flush();
            Draw.reset();
            Draw.z(0);
            Block block = b.block;
            float ax = b.x();
            float base = b.tile.drawy() - block.size * tilesize / 2f;

            ReflectConfig c = configFor(block);
            float s = c.reflectYdisplace;
            float h = block.size * tilesize;

            if(c.reflectionFlip){
                Tmp.m1.setToTranslation(ax, base).scale(c.reflectXdisplace, -s).translate(-ax, -base);
            }else{
                Tmp.m1.setToTranslation(ax, base - s * h).scale(c.reflectXdisplace, s).translate(-ax, -base);
            }
            if(c.rotationDeg != 0f || c.positionX != 0f || c.positionY != 0f){
                float cy = b.tile.drawy();
                Tmp.m2.setToTranslation(ax + c.positionX, cy + c.positionY).rotate(c.rotationDeg).translate(-ax, -cy);
                Tmp.m1.mulLeft(Tmp.m2);
            }
            Draw.trans(Tmp.m1);
            b.drawCached();
        }catch(Throwable t){
            if(reflectErrors++ < 5) Log.err("[reflect] block draw failed", t);
        }
    }

    private static void drawReflectedUnit(Unit u){
        try{
            Draw.flush();
            Draw.reset();
            Draw.z(0);
            float gap = reflectionGroundGap + (reflectionFlyerGap - reflectionGroundGap) * u.elevation();

            UnitType type = u.type;
            float shadowElev = type.shadowElevation;
            boolean softShadow = type.drawSoftShadow;
            float unitElev = u.elevation();
            try{
                type.shadowElevation = -1f;
                type.drawSoftShadow = false;
                u.elevation(0f);

                ReflectConfig c = configForUnit(type);
                float yScl = c.reflectionFlip ? -c.reflectYdisplace : c.reflectYdisplace;
                Tmp.m1.setToTranslation(u.x(), u.y() - 2f * gap)
                     .scale(c.reflectXdisplace, yScl)
                     .translate(-u.x(), -u.y());
                if(c.rotationDeg != 0f || c.positionX != 0f || c.positionY != 0f){
                    // rotate the whole unit reflection around its own center, then shift it by the position offset
                    Tmp.m2.setToTranslation(u.x() + c.positionX, u.y() - 2f * gap + c.positionY).rotate(c.rotationDeg).translate(-u.x(), -(u.y() - 2f * gap));
                    Tmp.m1.mulLeft(Tmp.m2);
                }
                Draw.trans(Tmp.m1);
                u.draw();
            }finally{
                u.elevation(unitElev);
                type.shadowElevation = shadowElev;
                type.drawSoftShadow = softShadow;
            }
        }catch(Throwable t){
            if(reflectErrors++ < 5) Log.err("[reflect] unit draw failed", t);
        }
    }

    private static void drawReflectedMoving(Drawc e){
        try{
            Draw.flush();
            Draw.reset();
            Draw.z(0);
            Draw.trans(Tmp.m1.setToTranslation(0f, -2f * reflectionGroundGap));
            e.draw();
        }catch(Throwable t){
            if(reflectErrors++ < 5) Log.err("[reflect] draw failed", t);
        }
    }

    private static ReflectConfig configFor(Block block){
        ReflectConfig c = config.get(block);
        if(c != null) return c;
        ReflectConfig cat = categoryConfig.get(block.category);
        if(cat != null) return cat;
        ReflectConfig d = defaultConfigFor(block);
        if(isOverride(d)) return d;
        if(globalConfig != null) return globalConfig;
        return d;
    }

    private static ReflectConfig configForUnit(UnitType type){
        ReflectConfig c = unitConfig.get(type);
        if(c != null) return c;
        if(unitGroupConfig != null) return unitGroupConfig;
        ReflectConfig d = defaultConfigFor(type);
        if(isOverride(d)) return d;
        if(globalConfig != null) return globalConfig;
        return d;
    }

    public static class ReflectConfig{
        public float reflectXdisplace = 1f;
        public float reflectYdisplace = 0.75f;
        public boolean reflectionFlip = true;
        public float rotationDeg = 0f;
        public float positionX = 0f;
        public float positionY = 0f;

        public ReflectConfig(){}

        public ReflectConfig(float x, float y, boolean flip, float rot, float posX, float posY){
            this.reflectXdisplace = x;
            this.reflectYdisplace = y;
            this.reflectionFlip = flip;
            this.rotationDeg = rot;
            this.positionX = posX;
            this.positionY = posY;
        }

        public ReflectConfig copy(){
            return new ReflectConfig(reflectXdisplace, reflectYdisplace, reflectionFlip, rotationDeg, positionX, positionY);
        }

        public static ReflectConfig read(String keyPrefix){
            if(!Core.settings.has(keyPrefix + "-x")) return null;
            return new ReflectConfig(
                Core.settings.getFloat(keyPrefix + "-x", 1f),
                Core.settings.getFloat(keyPrefix + "-y", 0.75f),
                Core.settings.getBool(keyPrefix + "-flip", true),
                Core.settings.getFloat(keyPrefix + "-rot", 0f),
                Core.settings.getFloat(keyPrefix + "-posx", 0f),
                Core.settings.getFloat(keyPrefix + "-posy", 0f)
            );
        }

        public void write(String keyPrefix){
            Core.settings.putFloat(keyPrefix + "-x", reflectXdisplace);
            Core.settings.putFloat(keyPrefix + "-y", reflectYdisplace);
            Core.settings.put(keyPrefix + "-flip", reflectionFlip);
            Core.settings.putFloat(keyPrefix + "-rot", rotationDeg);
            Core.settings.putFloat(keyPrefix + "-posx", positionX);
            Core.settings.putFloat(keyPrefix + "-posy", positionY);
        }

        public static void clearKeys(String keyPrefix){
            Core.settings.remove(keyPrefix + "-x");
            Core.settings.remove(keyPrefix + "-y");
            Core.settings.remove(keyPrefix + "-flip");
            Core.settings.remove(keyPrefix + "-rot");
            Core.settings.remove(keyPrefix + "-posx");
            Core.settings.remove(keyPrefix + "-posy");
        }
    }
}
