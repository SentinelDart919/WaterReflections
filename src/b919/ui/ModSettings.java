package b919.ui;

import arc.Core;
import arc.func.Boolp;
import arc.func.Cons;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import b919.graphics.WaterReflections;
import b919.graphics.WaterReflections.ReflectConfig;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.type.Category;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable.Setting;
import mindustry.world.Block;

import java.util.Locale;

public class ModSettings{

    private static boolean initialized;

    public static void init(){
        if(initialized) return;
        initialized = true;

        WaterReflections.loadConfigs();

        Vars.ui.settings.addCategory(Core.bundle.get("settings.b919-wr.title"), (Drawable)null, root -> {

            //global config
            root.pref(new Setting("b919-wr-global"){
                @Override public void add(SettingsTable t){
                    t.button(title, Icon.refresh, ModSettings::showGlobalConfig).left().padTop(4f).padBottom(4f);
                    t.row();
                }
            });

            //per category config button
            root.pref(new Setting("b919-wr-categories"){
                @Override public void add(SettingsTable t){
                    t.button(title, Icon.settings, ModSettings::showCategoryConfig).left().padTop(4f).padBottom(4f);
                    t.row();
                }
            });

            //per block/unit config button
            root.pref(new Setting("b919-wr-blocks"){
                @Override public void add(SettingsTable t){
                    t.button(title, Icon.list, ModSettings::showBlockConfig).left().padTop(4f).padBottom(4f);
                    t.row();
                }
            });
        });
    }

    //global config dialog
    private static void showGlobalConfig(){
        showEditor(Core.bundle.get("b919-wr.global"), WaterReflections.globalFor(),
            WaterReflections::saveGlobal,
            WaterReflections::resetGlobal,
            WaterReflections.isGlobalCustom());
    }

    //category config dialog
    private static void showCategoryConfig(){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("dialog.b919-wr-categories.title"));
        dialog.addCloseButton();

        dialog.cont.pane(p -> {
            // block categories
            p.add(Core.bundle.get("b919-wr.blocks")).left().padBottom(4f).padTop(4f).padLeft(4f).row();
            for(Category cat : Category.all){
                p.table(row -> addConfigRow(dialog, row, catName(cat),
                    WaterReflections.effective(cat),
                    cfg -> WaterReflections.saveCategory(cat, cfg),
                    () -> WaterReflections.isCustom(cat),
                    () -> WaterReflections.resetCategory(cat))
                ).left().padBottom(4f).padTop(4f).row();
            }
            // all units
            p.add(Core.bundle.get("b919-wr.units")).left().padBottom(4f).padTop(8f).padLeft(4f).row();
            p.table(row -> addConfigRow(dialog, row, Core.bundle.get("b919-wr.units"),
                WaterReflections.unitGroupEffective(),
                WaterReflections::saveUnitGroup,
                WaterReflections::isUnitGroupCustom,
                WaterReflections::resetUnitGroup)
            ).left().padBottom(4f).padTop(4f).row();
        }).fill();
        dialog.show();
    }

    private static void addConfigRow(BaseDialog dialog, Table row, String name, ReflectConfig c, Cons<ReflectConfig> save, Boolp isCustom, Runnable reset){
        row.left();
        row.add(name).width(120f).left();

        row.add(Core.bundle.get("b919-wr.sizeX")).padRight(6f);
        TextField xf = new TextField(String.valueOf(c.reflectXdisplace));
        row.add(xf).width(90f).padRight(8f);

        row.add(Core.bundle.get("b919-wr.sizeY")).padRight(6f);
        TextField yf = new TextField(String.valueOf(c.reflectYdisplace));
        row.add(yf).width(90f).padRight(8f);

        row.add(Core.bundle.get("b919-wr.posX")).padRight(6f);
        TextField pxf = new TextField(String.valueOf(c.positionX));
        row.add(pxf).width(90f).padRight(8f);

        row.add(Core.bundle.get("b919-wr.posY")).padRight(6f);
        TextField pyf = new TextField(String.valueOf(c.positionY));
        row.add(pyf).width(90f).padRight(8f);

        row.add("Rot:");
        TextField rf = new TextField(String.valueOf(c.rotationDeg));
        row.add(rf).width(90f).padRight(6f);

        CheckBox fl = new CheckBox(Core.bundle.get("b919-wr.flip"));
        fl.setChecked(c.reflectionFlip);
        row.add(fl).padLeft(6f);

ImageButton resetBtn = new ImageButton(Tex.whiteui, Styles.clearNonei);
        resetBtn.getStyle().imageUp = new TextureRegionDrawable(Icon.cancel);
        resetBtn.resizeImage(8 * 4f);
        resetBtn.clicked(() -> confirmReset(name, () -> {
            reset.run();
            dialog.hide();
            showCategoryConfig();
        }));

        Runnable applyResetVisibility = () -> resetBtn.visible = isCustom.get();

        Runnable saveCfg = () -> {
            ReflectConfig cfg = new ReflectConfig(safeFloat(xf.getText()), safeFloat(yf.getText()), fl.isChecked(),
                safeFloat(rf.getText()), safeFloat(pxf.getText()), safeFloat(pyf.getText()));
            if(WaterReflections.equalsConfig(cfg, c)) save.get(cfg);
            applyResetVisibility.run();
        };
        xf.changed(saveCfg);
        yf.changed(saveCfg);
        pxf.changed(saveCfg);
        pyf.changed(saveCfg);
        rf.changed(saveCfg);
        fl.changed(saveCfg);

        row.add(resetBtn).padLeft(6f);
        applyResetVisibility.run();
    }


    //blocks

private static void showBlockConfig(){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("dialog.b919-wr-blocks.title"));
        dialog.addCloseButton();

        TextField search = new TextField();
        search.setMessageText(Core.bundle.get("b919-wr.search"));
        Table listTable = new Table();

        Runnable rebuild = () -> rebuildBlockList(listTable, search.getText());
        search.changed(rebuild);
        rebuild.run();

        dialog.cont.top();
        dialog.cont.add(search).width(300f).padBottom(8f).row();
        ScrollPane pane = dialog.cont.pane(listTable).expand().fill().top().get();
        pane.setScrollingDisabled(true, false);
        dialog.show();
    }

    private static void rebuildBlockList(Table table, String query){
        table.clearChildren();
        table.top().left();
        String q = query.toLowerCase(Locale.ROOT).trim();

        // fixed
        int cols = Math.max(6, Math.min(16, (int)((Core.graphics.getWidth() / Scl.scl() - 120f) / 48f)));

        // blocks
        table.add(Core.bundle.get("b919-wr.blocks")).left().padBottom(6f).padTop(4f).padLeft(4f).row();
        int i = 0;
        for(Block block : Vars.content.blocks()){
            if(!isReflectable(block)) continue;
            if(!q.isEmpty() && !block.localizedName.toLowerCase(Locale.ROOT).contains(q)
                    && !block.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            ImageButton b = new ImageButton(Tex.whiteui, Styles.clearNonei);
            b.getStyle().imageUp = new TextureRegionDrawable(iconOf(block));
            b.resizeImage(8 * 4f);
            b.clicked(() -> showBlockEditor(block));
            table.add(b).size(48f).tooltip(block.localizedName);
            if(++i % cols == 0) table.row();
        }
        table.row();

        // units
        table.add(Core.bundle.get("b919-wr.units")).left().padBottom(6f).padTop(8f).padLeft(4f).row();
        i = 0;
        for(UnitType type : Vars.content.units()){
            if(!q.isEmpty() && !type.localizedName.toLowerCase(Locale.ROOT).contains(q)
                    && !type.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            ImageButton b = new ImageButton(Tex.whiteui, Styles.clearNonei);
            b.getStyle().imageUp = new TextureRegionDrawable(iconOf(type));
            b.resizeImage(8 * 4f);
            b.clicked(() -> showUnitEditor(type));
            table.add(b).size(48f).tooltip(type.localizedName);
            if(++i % cols == 0) table.row();
        }
    }


    //per block/unit editor
    private static void showBlockEditor(Block block){
        ReflectConfig c = WaterReflections.effective(block);
        showEditor(block.localizedName, c,
            cfg -> WaterReflections.saveBlock(block, cfg),
            () -> WaterReflections.resetBlock(block),
            WaterReflections.isCustom(block));
    }

    private static void showUnitEditor(UnitType type){
        ReflectConfig c = WaterReflections.effective(type);
        showEditor(type.localizedName, c,
            cfg -> WaterReflections.saveUnit(type, cfg),
            () -> WaterReflections.resetUnit(type),
            WaterReflections.isCustom(type));
    }

    private static void showEditor(String name, ReflectConfig current, Cons<ReflectConfig> save, Runnable reset, boolean canReset){
        BaseDialog dialog = new BaseDialog(Core.bundle.format("dialog.b919-wr-editor.title", name));
        dialog.addCloseButton();

        TextField[] xf = new TextField[1], yf = new TextField[1], rf = new TextField[1], pxf = new TextField[1], pyf = new TextField[1];
        CheckBox[] fl = new CheckBox[1];

        dialog.cont.table(t -> {
            t.add(Core.bundle.get("b919-wr.sizeX")).padRight(6f);
            xf[0] = new TextField(String.valueOf(current.reflectXdisplace));
            t.add(xf[0]).width(90f).padRight(10f);

            t.add(Core.bundle.get("b919-wr.sizeY")).padRight(6f);
            yf[0] = new TextField(String.valueOf(current.reflectYdisplace));
            t.add(yf[0]).width(90f).padRight(10f);

            t.add(Core.bundle.get("b919-wr.posX")).padRight(6f);
            pxf[0] = new TextField(String.valueOf(current.positionX));
            t.add(pxf[0]).width(90f).padRight(10f);

            t.add(Core.bundle.get("b919-wr.posY")).padRight(6f);
            pyf[0] = new TextField(String.valueOf(current.positionY));
            t.add(pyf[0]).width(90f).padRight(10f);

            t.add("Rot:");
            rf[0] = new TextField(String.valueOf(current.rotationDeg));
            t.add(rf[0]).width(90f).padRight(10f);

            t.add("Flip:");
            fl[0] = new CheckBox("");
            fl[0].setChecked(current.reflectionFlip);
            t.add(fl[0]).padLeft(6f);
        }).left().pad(8f).row();

        dialog.cont.table(btn -> {
            btn.button("@ok", () -> {
                ReflectConfig cfg = new ReflectConfig(
                    safeFloat(xf[0].getText()),
                    safeFloat(yf[0].getText()),
                    fl[0].isChecked(),
                    safeFloat(rf[0].getText()),
                    safeFloat(pxf[0].getText()),
                    safeFloat(pyf[0].getText()));
                if(WaterReflections.equalsConfig(cfg, current)) save.get(cfg);
                dialog.hide();
            });
            if(canReset){
                btn.button(Core.bundle.get("b919-wr.reset"), () -> confirmReset(name, () -> {
                    reset.run();
                    dialog.hide();
                })).padLeft(8f);
            }
        }).pad(8f).row();

        dialog.show();
    }
    //misc
    private static String catName(Category cat){
        return Core.bundle.get("b919-wr.cat." + cat.name(), cat.name());
    }

    private static void confirmReset(String name, Runnable onConfirm){
        BaseDialog dialog = new BaseDialog("@confirm");
        dialog.addCloseButton();
        dialog.cont.add(Core.bundle.format("b919-wr.reset.message", name)).pad(16f).row();
        dialog.buttons.defaults().size(140f, 50f);
        dialog.buttons.button("@ok", Icon.ok, () -> {
            dialog.hide();
            onConfirm.run();
        }).size(140f, 50f);
        dialog.buttons.button("@cancel", Icon.cancel, dialog::hide).size(140f, 50f);
        dialog.show();
    }

    private static boolean isReflectable(Block block){
        return !block.isFloor() && !block.isOverlay() && block.canBeBuilt();
    }

    private static TextureRegion iconOf(UnlockableContent c){
        if(c.uiIcon != null && c.uiIcon.found()) return c.uiIcon;
        if(c.fullIcon != null && c.fullIcon.found()) return c.fullIcon;
        if(c instanceof Block b && b.region != null && b.region.found()) return b.region;
        return Core.atlas.find("error");
    }

    private static float safeFloat(String s){
        try{ return Float.parseFloat(s.replace(",", ".")); }
        catch(Throwable e){ return 1f; }
    }
}