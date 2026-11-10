package com.valomap;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Team;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side minimap for the Valorant datapack.
 * - Shown only while the datapack's sidebar scoreboard "valo.sb" is displayed (= a match is running)
 *   and the local player is in one of the teams "valo_a" / "valo_b".
 * - Terrain: top-down view generated from the blocks around the player. The ceiling above the player is
 *   ignored so the floor you are standing on stays visible.
 * - Teammates are always shown. Enemies are shown only while at least one living teammate (or you)
 *   sees them: line of sight + field of view + max distance.
 */
public final class MinimapRenderer {
    private static final int TEX = 128;            // texture size in pixels
    private static final int HUD_SIZE = 100;       // size on screen (GUI pixels)
    private static final int HUD_X = 6;
    private static final int HUD_Y = 6;
    private static final float[] ZOOMS = {0.5f, 1.0f, 1.5f};   // blocks per pixel
    private static final int ROWS_PER_TICK = 16;   // terrain refresh: 8 ticks for the full texture
    private static final int CEILING_ABOVE_HEAD = 1;
    private static final int MAX_DEPTH = 28;
    private static final double SEE_DISTANCE = 60.0;
    private static final double SEE_CONE_COS = Math.cos(Math.toRadians(55.0));

    private static boolean enabled = true;
    private static int zoomIdx = 1;
    private static DynamicTexture texture;
    private static ResourceLocation textureId;
    private static int nextRow = 0;
    private static int snapX, snapY, snapZ;
    private static float snapBpp = 1.0f;
    private static int tickCounter = 0;

    private static final List<Marker> markers = new ArrayList<>();

    private static final class Marker {
        double x, z;
        float yaw;
        boolean ally;
        boolean self;
    }

    private MinimapRenderer() {
    }

    // ------------------------------------------------------------------ logic

    private static boolean isActive(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        Objective sidebar = mc.level.getScoreboard().getDisplayObjective(1);
        if (sidebar == null || !"valo.sb".equals(sidebar.getName())) {
            return false;
        }
        Team team = mc.player.getTeam();
        return team != null && team.getName().startsWith("valo_");
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (ClientModEvents.TOGGLE.consumeClick()) {
            enabled = !enabled;
        }
        while (ClientModEvents.ZOOM.consumeClick()) {
            zoomIdx = (zoomIdx + 1) % ZOOMS.length;
            nextRow = 0;
        }
        if (!enabled || !isActive(mc)) {
            markers.clear();
            return;
        }
        if (texture == null) {
            texture = new DynamicTexture(TEX, TEX, true);
            textureId = mc.getTextureManager().register("valomap_terrain", texture);
        }
        fillRows(mc);
        tickCounter++;
        if (tickCounter % 3 == 0) {
            updateMarkers(mc);
        }
    }

    private static void fillRows(Minecraft mc) {
        Entity cam = mc.getCameraEntity();
        ClientLevel level = mc.level;
        if (cam == null || level == null || texture == null || texture.getPixels() == null) {
            return;
        }
        if (nextRow == 0) {
            snapX = cam.getBlockX();
            snapY = cam.getBlockY();
            snapZ = cam.getBlockZ();
            snapBpp = ZOOMS[zoomIdx];
        }
        NativeImage img = texture.getPixels();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight();
        for (int i = 0; i < ROWS_PER_TICK; i++) {
            int row = nextRow;
            int wz = snapZ + Math.round((row - TEX / 2) * snapBpp);
            for (int col = 0; col < TEX; col++) {
                int wx = snapX + Math.round((col - TEX / 2) * snapBpp);
                img.setPixelRGBA(col, row, colorAt(level, pos, wx, wz, snapY + CEILING_ABOVE_HEAD, minY));
            }
            nextRow = (nextRow + 1) % TEX;
        }
        texture.upload();
    }

    /** ABGR color of the first visible block going down from startY (NativeImage uses ABGR). */
    private static int colorAt(ClientLevel level, BlockPos.MutableBlockPos pos, int wx, int wz, int startY, int minY) {
        int lowest = Math.max(minY, startY - MAX_DEPTH);
        for (int y = startY; y >= lowest; y--) {
            pos.set(wx, y, wz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            MapColor mapColor = state.getMapColor(level, pos);
            if (mapColor == null || mapColor == MapColor.NONE) {
                continue; // glass, barriers, ... : see through
            }
            int rgb = mapColor.col;
            float shade = 0.80f + Mth.clamp(y - (startY - CEILING_ABOVE_HEAD), -6, 6) * 0.025f;
            int r = Mth.clamp((int) (((rgb >> 16) & 255) * shade), 0, 255);
            int g = Mth.clamp((int) (((rgb >> 8) & 255) * shade), 0, 255);
            int b = Mth.clamp((int) ((rgb & 255) * shade), 0, 255);
            return 0xFF000000 | (b << 16) | (g << 8) | r;
        }
        return 0xFF101010; // nothing loaded / void
    }

    private static void updateMarkers(Minecraft mc) {
        markers.clear();
        ClientLevel level = mc.level;
        Entity cam = mc.getCameraEntity();
        if (level == null || mc.player == null || mc.player.getTeam() == null || cam == null) {
            return;
        }
        String myTeam = mc.player.getTeam().getName();
        List<Player> allies = new ArrayList<>();
        List<Player> enemies = new ArrayList<>();
        for (AbstractClientPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) {
                continue;
            }
            Team t = p.getTeam();
            if (t == null || !t.getName().startsWith("valo_")) {
                continue;
            }
            if (t.getName().equals(myTeam)) {
                allies.add(p);
            } else {
                enemies.add(p);
            }
        }
        for (Player a : allies) {
            Marker m = new Marker();
            m.x = a.getX();
            m.z = a.getZ();
            m.yaw = a.getYRot();
            m.ally = true;
            m.self = (a == cam);
            markers.add(m);
        }
        for (Player e : enemies) {
            boolean seen = false;
            for (Player a : allies) {
                if (canSee(level, a, e)) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                Marker m = new Marker();
                m.x = e.getX();
                m.z = e.getZ();
                m.yaw = e.getYRot();
                m.ally = false;
                markers.add(m);
            }
        }
    }

    private static boolean canSee(ClientLevel level, Player viewer, Player target) {
        Vec3 from = viewer.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 dir = to.subtract(from);
        double dist = dir.length();
        if (dist > SEE_DISTANCE || dist < 0.001) {
            return dist < 0.001;
        }
        Vec3 look = viewer.getViewVector(1.0F);
        if (look.dot(dir.scale(1.0 / dist)) < SEE_CONE_COS) {
            return false;
        }
        if (rayClear(level, viewer, from, to)) {
            return true;
        }
        Vec3 feet = target.position().add(0.0, 0.4, 0.0);
        return rayClear(level, viewer, from, feet);
    }

    private static boolean rayClear(ClientLevel level, Entity viewer, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer))
                .getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------ drawing

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || textureId == null || mc.options.hideGui || !isActive(mc)) {
            return;
        }
        g.fill(HUD_X - 2, HUD_Y - 2, HUD_X + HUD_SIZE + 2, HUD_Y + HUD_SIZE + 2, 0xFF000000);
        g.blit(textureId, HUD_X, HUD_Y, HUD_SIZE, HUD_SIZE, 0.0F, 0.0F, TEX, TEX, TEX, TEX);

        float guiPerTexel = HUD_SIZE / (float) TEX;
        for (Marker m : markers) {
            float tx = TEX / 2.0f + (float) ((m.x - (snapX + 0.5)) / snapBpp);
            float tz = TEX / 2.0f + (float) ((m.z - (snapZ + 0.5)) / snapBpp);
            int px = HUD_X + Math.round(tx * guiPerTexel);
            int pz = HUD_Y + Math.round(tz * guiPerTexel);
            if (px < HUD_X + 2 || pz < HUD_Y + 2 || px > HUD_X + HUD_SIZE - 3 || pz > HUD_Y + HUD_SIZE - 3) {
                continue;
            }
            int color = m.self ? 0xFFFFFFFF : (m.ally ? 0xFF35E035 : 0xFFFF3030);
            g.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF000000);
            g.fill(px - 1, pz - 1, px + 2, pz + 2, color);
            // facing direction (Minecraft yaw 0 = south = +z, 90 = west = -x)
            double rad = Math.toRadians(m.yaw);
            for (int i = 3; i <= 6; i++) {
                int lx = px + (int) Math.round(-Math.sin(rad) * i);
                int lz = pz + (int) Math.round(Math.cos(rad) * i);
                g.fill(lx, lz, lx + 1, lz + 1, color);
            }
        }
        g.drawString(mc.font, "N", HUD_X + HUD_SIZE / 2 - 2, HUD_Y + 1, 0xFFFFFFFF);
    }
}
