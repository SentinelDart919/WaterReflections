package b919.graphics;

import arc.Core;
import arc.graphics.Texture;
import arc.graphics.gl.Shader;
import arc.util.Nullable;
import arc.util.Time;
import mindustry.Vars;
import mindustry.graphics.CacheLayer;
import mindustry.graphics.Shaders;

import static arc.Core.assets;
import static mindustry.Vars.renderer;
import static mindustry.Vars.tree;

/**
 * Water reflection shaders replace the vanilla water surface shader with one that samples the reflection buffer*/
public class ReflectionShaders {

    public static @Nullable WaterReflectShader waterReflect;

    public static void init(){
        waterReflect = new WaterReflectShader();
        ((CacheLayer.ShaderLayer)CacheLayer.water).shader = waterReflect;
    }

    public static void dispose(){
        if(!Vars.headless){
            if(waterReflect != null) waterReflect.dispose();
        }
    }

    public static class SurfaceShader extends Shader{
        Texture noiseTex;

        public SurfaceShader(String frag){
            super(Shaders.getShaderFi("screenspace.vert"), tree.get("shaders/" + frag + ".frag"));
            loadNoise();
        }

        public String textureName(){
            return "noise";
        }

        public void loadNoise(){
            assets.load("sprites/" + textureName() + ".png", Texture.class).loaded = t -> {
                t.setFilter(Texture.TextureFilter.linear);
                t.setWrap(Texture.TextureWrap.repeat);
            };
        }

        @Override
        public void apply(){
            setUniformf("u_campos", Core.camera.position.x - Core.camera.width / 2, Core.camera.position.y - Core.camera.height / 2);
            setUniformf("u_resolution", Core.camera.width, Core.camera.height);
            setUniformf("u_time", Time.time);

            if(hasUniform("u_noise")){
                if(noiseTex == null){
                    noiseTex = assets.get("sprites/" + textureName() + ".png", Texture.class);
                }

                noiseTex.bind(2);
                renderer.effectBuffer.getTexture().bind(0);

                setUniformi("u_noise", 2);
            }
        }
    }

    public static class WaterReflectShader extends SurfaceShader {
        public WaterReflectShader() {
            super("water");
        }
        //see https://en.wikipedia.org/wiki/Reflection_(mathematics) if you're unsure how reflections work
        @Override
        public void apply() {
            super.apply();
            if (WaterReflections.buffer != null) {
                WaterReflections.buffer.getTexture().bind(1);
                setUniformi("u_reflection", 1);
                setUniformf("u_refTint", WaterReflections.refTint.r, WaterReflections.refTint.g, WaterReflections.refTint.b, WaterReflections.refTintAmount);
                setUniformf("u_refOpacity", WaterReflections.refOpacity);
            }
        }
    }
}