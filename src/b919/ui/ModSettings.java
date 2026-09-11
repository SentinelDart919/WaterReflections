package b919.ui;

import arc.Core;
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

            //master toggle
            //redundant??
            root.checkPref("b919-wr-unflip-turrets", false, WaterReflections::setTurretsUnflipped);

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

    //category config dialog
    private static void showCategoryConfig(){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("dialog.b919-wr-categories.title"));
        dialog.addCloseButton();

        dialog.cont.pane(p -> {
            for(Category cat : Category.all){
                ReflectConfig c = WaterReflections.effective(cat);
                p.table(row -> {
                    row.left();
                    row.add(catName(cat)).width(120f).left();

                    row.add("X:");
                    TextField xf = new TextField(String.valueOf(c.reflectXdisplace));
                    row.add(xf).width(60f).padRight(6f);

                    row.add("Y:");
                    TextField yf = new TextField(String.valueOf(c.reflectYdisplace));
                    row.add(yf).width(60f).padRight(6f);

                    CheckBox fl = new CheckBox(Core.bundle.get("b919-wr.flip"));
                    fl.setChecked(c.reflectionFlip);
                    row.add(fl).padLeft(6f);

                    Runnable save = () -> {
                        ReflectConfig cfg = WaterReflections.categoryFor(cat);
                        cfg.reflectXdisplace = safeFloat(xf.getText());
                        cfg.reflectYdisplace = safeFloat(yf.getText());
                        cfg.reflectionFlip = fl.isChecked();
                        WaterReflections.saveCategory(cat, cfg);
                    };
                    xf.changed(save);
                    yf.changed(save);
                    fl.changed(save);

                    if(WaterReflections.hasCategoryConfig(cat)){
                        row.button(Icon.cancel, () -> confirmReset(catName(cat), () -> {
                            WaterReflections.resetCategory(cat);
                            dialog.hide();
                            showCategoryConfig(); // refresh
                        })).padLeft(6f);
                    }
                }).left().padBottom(4f).padTop(4f).row();
            }
        }).fill();
        dialog.show();
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
            () -> WaterReflections.resetBlock(block));
    }

    private static void showUnitEditor(UnitType type){
        ReflectConfig c = WaterReflections.effective(type);
        showEditor(type.localizedName, c,
            cfg -> WaterReflections.saveUnit(type, cfg),
            () -> WaterReflections.resetUnit(type));
    }

    private static void showEditor(String name, ReflectConfig current, Cons<ReflectConfig> save, Runnable reset){
        BaseDialog dialog = new BaseDialog(Core.bundle.format("dialog.b919-wr-editor.title", name));
        dialog.addCloseButton();

        TextField[] xf = new TextField[1], yf = new TextField[1];
        CheckBox[] fl = new CheckBox[1];

        dialog.cont.table(t -> {
            t.add("X:");
            xf[0] = new TextField(String.valueOf(current.reflectXdisplace));
            t.add(xf[0]).width(70f).padRight(10f);

            t.add("Y:");
            yf[0] = new TextField(String.valueOf(current.reflectYdisplace));
            t.add(yf[0]).width(70f).padRight(10f);

            t.add("Flip:");
            fl[0] = new CheckBox("");
            fl[0].setChecked(current.reflectionFlip);
            t.add(fl[0]).padLeft(6f);
        }).left().pad(8f).row();

        dialog.cont.table(btn -> {
            btn.button("@ok", () -> {
                save.get(new ReflectConfig(
                    safeFloat(xf[0].getText()),
                    safeFloat(yf[0].getText()),
                    fl[0].isChecked()));
                dialog.hide();
            });
            btn.button(Core.bundle.get("b919-wr.reset"), () -> confirmReset(name, () -> {
                reset.run();
                dialog.hide();
            })).padLeft(8f);
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