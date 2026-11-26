package fr.creatmon.shop.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class CustomPauseScreen extends Screen {

    // ── URLs serveur (à personnaliser) ────────────────────────────────
    private static final String URL_SITE    = "https://creatmon.fr";
    private static final String URL_WIKI    = "https://wiki.creatmon.fr";
    private static final String URL_DISCORD = "https://discord.gg/creatmon";
    private static final String SERVER_NAME = "Votre Serveur";

    // ── Palette ───────────────────────────────────────────────────────
    private static final int PANEL_BG   = 0xCC0A1120;
    private static final int BORDER     = 0xFF1A6B8A;
    private static final int TEXT_WHITE = 0xFFEEEEEE;
    private static final int TEXT_GOLD  = 0xFFFFD700;
    private static final int TEXT_BLUE  = 0xFF4499DD;
    private static final int TEXT_GRAY  = 0xFF778899;

    // ── Layout ────────────────────────────────────────────────────────
    private static final int PANEL_W   = 160;
    private static final int PANEL_MRG = 150;

    private static final int BTN_W     = 200;
    private static final int BTN_H     = 20;
    private static final int BTN_GAP   = 4;

    public CustomPauseScreen() {
        super(Component.literal("Pause"));
    }

    @Override
    protected void init() {
        int cx    = width / 2;
        int btnX  = cx - BTN_W / 2;
        // Centre vertical : on vise le milieu moins la moitié de la hauteur totale des boutons
        // 4 lignes + 3 gaps + titre
        int totalH = 20 + BTN_GAP + BTN_H + BTN_GAP + BTN_H + BTN_GAP + BTN_H + BTN_GAP + BTN_H;
        int startY = (height - totalH) / 2;

        // Titre : pas un bouton, juste du texte rendu dans render()
        int y = startY + 20 + BTN_GAP;

        // Retour au jeu
        addRenderableWidget(Button.builder(
            Component.literal("Retour au jeu"),
            b -> onClose()
        ).bounds(btnX, y, BTN_W, BTN_H).build());
        y += BTN_H + BTN_GAP;

        // Site | Wiki | Discord (3 boutons côte à côte)
        int smallW = (BTN_W - 2 * BTN_GAP) / 3;
        addRenderableWidget(Button.builder(
            Component.literal("Site"),
            b -> Util.getPlatform().openUri(URI.create(URL_SITE))
        ).bounds(btnX, y, smallW, BTN_H).build());

        addRenderableWidget(Button.builder(
            Component.literal("Wiki"),
            b -> Util.getPlatform().openUri(URI.create(URL_WIKI))
        ).bounds(btnX + smallW + BTN_GAP, y, smallW, BTN_H).build());

        addRenderableWidget(Button.builder(
            Component.literal("Discord"),
            b -> Util.getPlatform().openUri(URI.create(URL_DISCORD))
        ).bounds(btnX + 2 * (smallW + BTN_GAP), y, smallW, BTN_H).build());
        y += BTN_H + BTN_GAP;

        // Options
        addRenderableWidget(Button.builder(
            Component.literal("Options"),
            b -> openOptions()
        ).bounds(btnX, y, BTN_W, BTN_H).build());
        y += BTN_H + BTN_GAP;

        // Quitter le jeu
        addRenderableWidget(Button.builder(
            Component.literal("Quitter le jeu"),
            b -> minecraft.disconnect()
        ).bounds(btnX, y, BTN_W, BTN_H).build());
    }

    // Empêche le parent de redessiner son overlay sombre par-dessus nos panneaux
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x60000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // 1. Notre fond (via renderBackground overridé)
        renderBackground(g, mx, my, pt);

        // 2. Panneaux latéraux
        drawLeft(g);
        drawRight(g);

        // 3. Titre + boutons
        int totalH = 20 + BTN_GAP + BTN_H + BTN_GAP + BTN_H + BTN_GAP + BTN_H + BTN_GAP + BTN_H;
        int titleY = (height - totalH) / 2;
        g.drawCenteredString(font, SERVER_NAME, width / 2, titleY + 4, TEXT_GRAY);

        // super.render() dessine les boutons mais appelle aussi renderBackground
        // → comme on l'a overridé, il ne rajoute pas d'overlay parasite
        super.render(g, mx, my, pt);
    }

    // ── Panneau gauche : profil joueur ────────────────────────────────

    private void drawLeft(GuiGraphics g) {
        int px = PANEL_MRG;
        int py = height / 6;
        int pw = PANEL_W;
        int ph = height - py * 2;
        panel(g, px, py, pw, ph);

        int cx   = px + pw / 2;
        int curY = py + 16;

        // Face du joueur (32×32)
        int face = 32;
        int fx   = cx - face / 2;
        try {
            var skin = minecraft.getSkinManager().getInsecureSkin(minecraft.player.getGameProfile());
            // UV(32,32) taille 32×32 dans 256×256 = pixel(8,8) taille 8×8 dans 64×64, rendu 32×32
            g.blit(skin.texture(), fx, curY, 32, 32, 32, 32);
        } catch (Throwable ignored) {
            g.fill(fx, curY, fx + face, curY + face, 0xFF334455);
        }
        curY += face + 8;

        // Pseudo
        g.drawCenteredString(font, minecraft.player.getName().getString(), cx, curY, TEXT_BLUE);
        curY += 12;

        // Niveau
        g.drawCenteredString(font, "Niveau " + ClientLevel.value, cx, curY, TEXT_WHITE);
        curY += 12;

        // Balance
        String bal = ClientBalance.initialized
            ? formatBalance(ClientBalance.value) + " €" : "--- €";
        g.drawCenteredString(font, bal, cx, curY, TEXT_GOLD);
    }

    // ── Panneau droit : équipe Pokémon ────────────────────────────────

    private void drawRight(GuiGraphics g) {
        int px = width - PANEL_MRG - PANEL_W;
        int py = height / 6;
        int pw = PANEL_W;
        int ph = height - py * 2;
        panel(g, px, py, pw, ph);

        int cx   = px + pw / 2;
        int curY = py + 8;

        // Header
        g.fill(px + 1, curY, px + pw - 1, curY + 16, 0x44FFFFFF);
        g.drawCenteredString(font, "Équipe Pokémon", cx, curY + 4, TEXT_WHITE);
        // Ligne accent sous le header
        g.fill(px + 1, curY + 16, px + pw - 1, curY + 18, BORDER);
        curY += 22;

        List<PokeEntry> team = readTeam();
        if (team.isEmpty()) {
            g.drawCenteredString(font, "—", cx, curY + 10, TEXT_GRAY);
        } else {
            int rowH = (ph - 30) / 6; // répartit équitablement les 6 slots
            for (PokeEntry e : team) {
                pokeRow(g, px + 6, curY, pw - 12, e);
                curY += rowH;
            }
        }
    }

    private void pokeRow(GuiGraphics g, int x, int y, int w, PokeEntry e) {
        // Nom (gauche) + Nv. (droite)
        g.drawString(font, e.name(), x, y + 1, TEXT_WHITE, false);
        String lvl = "Nv." + e.level();
        g.drawString(font, lvl, x + w - font.width(lvl), y + 1, TEXT_GRAY, false);

        // Barre HP
        int bY = y + 12, bH = 3;
        g.fill(x, bY, x + w, bY + bH, 0xFF1A2A1A);
        int filled = Math.max(1, (int)((w - 2) * e.hpPct()));
        int col = e.hpPct() > 0.5f ? 0xFF44DD55 : e.hpPct() > 0.25f ? 0xFFDDBB00 : 0xFFDD3333;
        g.fill(x + 1, bY + 1, x + 1 + filled, bY + bH - 1, col);
    }

    // ── Cobblemon team (lazy) ─────────────────────────────────────────

    record PokeEntry(String name, int level, float hpPct) {}

    private List<PokeEntry> readTeam() {
        List<PokeEntry> out = new ArrayList<>();
        try {
            Class<?> cls      = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object   instance = cls.getField("INSTANCE").get(null);
            Object   storage  = instance.getClass().getMethod("getStorage").invoke(instance);
            Object   party    = storage.getClass().getMethod("getParty").invoke(storage);
            @SuppressWarnings("unchecked")
            java.util.List<Object> list =
                (java.util.List<Object>) party.getClass().getMethod("toGappyList").invoke(party);
            for (Object poke : list) {
                if (poke == null) continue;
                Object comp = poke.getClass().getMethod("getDisplayName").invoke(poke);
                String name = (String) comp.getClass().getMethod("getString").invoke(comp);
                int    lvl  = (int)   poke.getClass().getMethod("getLevel").invoke(poke);
                int    hp   = (int)   poke.getClass().getMethod("getHp").invoke(poke);
                int    max  = (int)   poke.getClass().getMethod("getMaxHp").invoke(poke);
                out.add(new PokeEntry(name, lvl, max > 0 ? (float) hp / max : 1f));
                if (out.size() >= 6) break;
            }
        } catch (Throwable ignored) {}
        return out;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x,         y,         x + w,     y + h,     PANEL_BG);
        g.fill(x,         y,         x + w,     y + 1,     BORDER);
        g.fill(x,         y + h - 1, x + w,     y + h,     BORDER);
        g.fill(x,         y,         x + 1,     y + h,     BORDER);
        g.fill(x + w - 1, y,         x + w,     y + h,     BORDER);
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

    private void openOptions() {
        // OptionsScreen a été déplacé dans un sous-package en 1.21.1 — on le cherche dynamiquement
        for (String cls : new String[]{
            "net.minecraft.client.gui.screens.OptionsScreen",
            "net.minecraft.client.gui.screens.options.OptionsScreen"
        }) {
            try {
                Screen s = (Screen) Class.forName(cls)
                    .getDeclaredConstructors()[0]
                    .newInstance(this, minecraft.options);
                minecraft.setScreen(s);
                return;
            } catch (Throwable ignored) {}
        }
    }

    @Override public boolean isPauseScreen()    { return true; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }
}
