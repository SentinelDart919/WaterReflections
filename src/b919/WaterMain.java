package b919;

import arc.*;
import arc.util.*;
import b919.graphics.ReflectionShaders;
import b919.graphics.WaterReflections;
import b919.ui.ModSettings;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.mod.*;

import static mindustry.Vars.mods;

public class WaterMain extends Mod{

    private static final String aquarionWarnedKey = "b919-waterreflection-aquarion-warned";

    public WaterMain(){
        Log.info("Water++ load");
        if(!Vars.headless){
            Events.run(Trigger.draw, WaterReflections::captureScreen);
            Events.on(DisposeEvent.class, e -> ReflectionShaders.dispose());

            Events.on(FileTreeInitEvent.class, e -> {
                if(mods.getMod("aquarion") != null){
                    Log.warn("WaterReflections disabled: Aquarion is installed and already has its own water reflection system.");
                    WaterReflections.disabled = true;
                }else{
                    Core.settings.put(aquarionWarnedKey, false);
                    Core.app.post(ReflectionShaders::init);
                }
            });

            Events.on(ClientLoadEvent.class, e -> {
                WaterReflections.loadConfigs();
                ModSettings.init();
                if(WaterReflections.disabled) showAquarionWarning();
            });
        }
    }

    private void showAquarionWarning(){
        Core.app.post(() -> {
            if(Core.settings.getBool(aquarionWarnedKey)) return;
            Vars.ui.showInfo(Core.bundle.get("b919.waterreflection.aquarion.warning"));
            Core.settings.put(aquarionWarnedKey, true);
        });
    }

    @Override
    public void loadContent(){
    }

}
