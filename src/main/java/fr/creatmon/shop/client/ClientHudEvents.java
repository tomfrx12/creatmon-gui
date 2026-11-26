package fr.creatmon.shop.client;

import fr.creatmon.shop.CreatmonShop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = CreatmonShop.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientHudEvents {

    // ── Palette ───────────────────────────────────────────────────────
    private static final int BORDER   = 0xFF1A6B8A;
    private static final int CHIP_BG  = 0xBB0A1120;

    // ── Bounds du bouton Spawn (calculés à chaque frame) ─────────────
    private static int spawnX, spawnY, spawnW, spawnH;

    // ─────────────────────────────────────────────────────────────────
    // INTERCEPTION DU MENU PAUSE → CustomPauseScreen
    // ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof PauseScreen) {
            event.setNewScreen(new CustomPauseScreen());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // HUD EN JEU (en haut, chips centrés)
    // ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null) return;
        drawHud(event.getGuiGraphics(), mc,
            mc.getWindow().getGuiScaledWidth(),
            mc.getWindow().getGuiScaledHeight());
    }

    private static void drawHud(GuiGraphics g, Minecraft mc, int sw, int sh) {
        Font font   = mc.font;
        LocalPlayer p = mc.player;
        int barH    = 18;
        int barY    = 2;

        String name   = p.getName().getString();
        String lvlStr = "★ Nv." + ClientLevel.value;
        String bal    = ClientBalance.initialized ? formatBalance(ClientBalance.value) + " €" : "--- €";
        String balStr = "● " + bal;
        String spawn  = "◆ Spawn";

        // Largeurs des chips principaux
        int wName  = font.width("■ " + name) + 12;
        int wLvl   = font.width(lvlStr) + 12;
        int wBal   = font.width(balStr) + 12;
        int wSpawn = font.width(spawn) + 12;
        int gap    = 2;
        int totalMain = wName + wLvl + wBal + wSpawn + gap * 3;

        // Position centrée
        int startX = (sw - totalMain) / 2;
        int curX   = startX;

        curX = chip(g, font, curX, barY, barH, "■ " + name, 0xFF5588CC, CHIP_BG);
        curX += gap;
        curX = chip(g, font, curX, barY, barH, lvlStr, 0xFFFFD700, CHIP_BG);
        curX += gap;
        curX = chip(g, font, curX, barY, barH, balStr, 0xFF44CCBB, CHIP_BG);
        curX += gap;

        spawnX = curX; spawnY = barY; spawnW = wSpawn; spawnH = barH;
        chip(g, font, curX, barY, barH, spawn, 0xFFDD5544, 0xFF3A1510);

        // ── Droite : météo + heure ────────────────────────────────────
        long time  = mc.level.getDayTime() % 24000L;
        int  mins  = (int)((time + 6000L) % 24000L * 1440L / 24000L);
        String timeStr = String.format("%02d:%02d", mins / 60, mins % 60);

        boolean isDay = time < 12000 || time >= 23000;
        boolean rain  = mc.level.isRaining();
        String weather = rain ? "☁ Pluie" : isDay ? "☀ Clair" : "☽ Nuit";
        int wColor     = rain ? 0xFF8899BB : isDay ? 0xFFFFD700 : 0xFF8899CC;

        int wTimeW    = font.width("○ " + timeStr) + 12;
        int wWeatherW = font.width(weather) + 12;
        int rx = sw - 2 - wTimeW;
        chip(g, font, rx, barY, barH, "○ " + timeStr, 0xFFEEEEEE, CHIP_BG);
        rx -= gap + wWeatherW;
        chip(g, font, rx, barY, barH, weather, wColor, CHIP_BG);
    }

    // ─────────────────────────────────────────────────────────────────
    // CLIC BOUTON SPAWN
    // ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != 0 || event.getAction() != 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        double scale = mc.getWindow().getGuiScale();
        int mx = (int)(mc.mouseHandler.xpos() / scale);
        int my = (int)(mc.mouseHandler.ypos() / scale);
        if (mx >= spawnX && mx < spawnX + spawnW && my >= spawnY && my < spawnY + spawnH) {
            mc.player.connection.sendCommand("spawn");
            event.setCanceled(true);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────

    private static int chip(GuiGraphics g, Font font, int x, int y, int h,
                            String text, int textColor, int bg) {
        int w = font.width(text) + 12;
        g.fill(x,         y,         x + w,     y + h,     bg);
        g.fill(x,         y,         x + w,     y + 1,     BORDER);
        g.fill(x,         y + h - 1, x + w,     y + h,     BORDER);
        g.fill(x,         y,         x + 1,     y + h,     BORDER);
        g.fill(x + w - 1, y,         x + w,     y + h,     BORDER);
        g.drawString(font, text, x + 6, y + (h - 8) / 2, textColor, false);
        return x + w;
    }

    private static String formatBalance(int n) {
        String s = Integer.toString(n);
        var sb   = new StringBuilder();
        int off  = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - off) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}
