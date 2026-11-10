package com.valomap;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod bus events (client only): key bindings and the HUD overlay. */
@Mod.EventBusSubscriber(modid = ValoMapMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    public static final KeyMapping TOGGLE =
            new KeyMapping("key.valomap.toggle", InputConstants.KEY_K, "key.categories.valomap");
    public static final KeyMapping ZOOM =
            new KeyMapping("key.valomap.zoom", InputConstants.KEY_L, "key.categories.valomap");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(ZOOM);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("minimap", MinimapRenderer::render);
    }
}
