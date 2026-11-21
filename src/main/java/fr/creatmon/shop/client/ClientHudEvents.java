package fr.creatmon.shop.client;

import fr.creatmon.shop.CreatmonShop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = CreatmonShop.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientHudEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger("creatmon_hud");
    private static int debugFrames = 0;

    // ── Sync balance depuis le menu ouvert ────────────────────────────

    // ── HUD en jeu ────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (debugFrames < 3) {
            LOGGER.info("[HUD] onRenderGui frame={} screen={} initialized={} value={}",
                debugFrames, mc.screen == null ? "null" : mc.screen.getClass().getSimpleName(),
                ClientBalance.initialized, ClientBalance.value);
            debugFrames++;
        }
        if (mc.screen != null) return;
        drawBadge(event.getGuiGraphics(), mc.font,
            mc.getWindow().getGuiScaledWidth(),
            mc.getWindow().getGuiScaledHeight());
    }

    // ── Menu pause (ESC) ──────────────────────────────────────────────

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof PauseScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        drawBadge(event.getGuiGraphics(), mc.font,
            event.getScreen().width, event.getScreen().height);
    }

    // ── Rendu du badge ────────────────────────────────────────────────

    private static void drawBadge(GuiGraphics g, Font font, int sw, int sh) {
        String text = ClientBalance.initialized ? (formatBalance(ClientBalance.value) + " €") : "--- €";
        int tw = font.width(text);
        int pw = tw + 12;
        int px = 4;   // haut-gauche pour éviter le minimap Xaero (haut-droit)
        int py = 4;

        // Fond semi-transparent
        g.fill(px,    py,    px+pw,   py+14,  0xBB0E1626);
        // Bordure dorée
        g.fill(px,    py,    px+pw,   py+1,   0xFF7A5A18);
        g.fill(px,    py+13, px+pw,   py+14,  0xFF7A5A18);
        g.fill(px,    py,    px+1,    py+14,  0xFF7A5A18);
        g.fill(px+pw-1, py,  px+pw,   py+14,  0xFF7A5A18);
        // Texte
        g.drawString(font, text, px + 6, py + 3, 0xFFD49E2A, false);
    }

    private static String formatBalance(int n) {
        String s = Integer.toString(n);
        var sb  = new StringBuilder();
        int off = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - off) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}
