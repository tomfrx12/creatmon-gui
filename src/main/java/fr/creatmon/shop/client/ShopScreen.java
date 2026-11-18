package fr.creatmon.shop.client;

import fr.creatmon.shop.ShopData;
import fr.creatmon.shop.ShopMenu;
import fr.creatmon.shop.network.BuyItemPacket;
import fr.creatmon.shop.network.SellItemPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ShopScreen extends AbstractContainerScreen<ShopMenu> {

    // ── Dimensions ────────────────────────────────────────────────────
    private static final int W          = 256;
    private static final int H          = 280;
    private static final int HEADER_H   = 36;   // title (y=3) + balance pill (y=16)
    // Sub-header: 2 rows — row1=category name (14px), row2=search bar (14px)
    private static final int SUBHEAD_H  = 28;
    private static final int SIDEBAR_W  = 26;
    private static final int SLOT_SIZE  = 18;
    private static final int GRID_X     = SIDEBAR_W + 4;
    private static final int GRID_Y     = HEADER_H + SUBHEAD_H + 2;  // = 66
    private static final int FOOTER_Y   = 190;
    private static final int INV_X      = (W - 9 * SLOT_SIZE) / 2;   // = 47

    // ── Colours ───────────────────────────────────────────────────────
    private static final int BG         = 0xFF0E1626;
    private static final int SIDEBAR_BG = 0xFF0A1020;
    private static final int SLOT_BG    = 0xFF1A2B40;
    private static final int SLOT_BDR   = 0xFF0C131E;
    private static final int GOLD       = 0xFFD49E2A;
    private static final int GOLD_DIM   = 0xFF7A5A18;
    private static final int ORANGE     = 0xFFFF8800;
    private static final int WHITE      = 0xFFFFFFFF;
    private static final int INV_LABEL  = 0xFF8899AA;
    private static final int FOOTER_BG  = 0xFF07101A;
    private static final int PILL_BG    = 0xFF152235;
    private static final int SEARCH_BG  = 0xFF0A1520;

    // ── State ─────────────────────────────────────────────────────────
    private EditBox searchBox;
    private int hoveredItem = -1;

    public ShopScreen(ShopMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        // Son d'ouverture
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHEST_OPEN, 1.3f));
        // Search bar on its own row (sub-header row 2), full content width
        int searchX = leftPos + SIDEBAR_W + 4;
        int searchW = W - SIDEBAR_W - 8;
        searchBox = new EditBox(font, searchX, topPos + HEADER_H + 16, searchW, 10,
                Component.literal("Rechercher..."));
        searchBox.setMaxLength(32);
        searchBox.setHint(Component.literal("Rechercher..."));
        searchBox.setBordered(false);
        addRenderableWidget(searchBox);
    }

    // ── Background ────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        g.fill(x, y, x + W, y + H, BG);

        // Sidebar
        g.fill(x, y, x + SIDEBAR_W, y + FOOTER_Y, SIDEBAR_BG);
        g.fill(x + SIDEBAR_W, y, x + SIDEBAR_W + 1, y + FOOTER_Y, GOLD_DIM);

        // Header separator
        g.fill(x, y + HEADER_H, x + W, y + HEADER_H + 1, GOLD_DIM);

        // Sub-header row 1: category name
        g.fill(x + SIDEBAR_W + 1, y + HEADER_H + 1,  x + W, y + HEADER_H + 14, 0xFF0B1522);
        g.fill(x + SIDEBAR_W + 1, y + HEADER_H + 14, x + W, y + HEADER_H + 15, SLOT_BDR);

        // Sub-header row 2: search bar
        g.fill(x + SIDEBAR_W + 1, y + HEADER_H + 15, x + W, y + HEADER_H + SUBHEAD_H,     SEARCH_BG);
        g.fill(x + SIDEBAR_W + 1, y + HEADER_H + SUBHEAD_H, x + W, y + HEADER_H + SUBHEAD_H + 1, SLOT_BDR);

        // Balance pill
        String balStr = formatBalance(menu.getBalance()) + " €";
        int pw = font.width(balStr) + 20;
        int px = x + (W - pw) / 2;
        g.fill(px,          y + 15, px + pw,     y + 28, PILL_BG);
        g.fill(px,          y + 15, px + pw,     y + 16, GOLD_DIM);
        g.fill(px,          y + 27, px + pw,     y + 28, GOLD_DIM);
        g.fill(px,          y + 15, px + 1,      y + 28, GOLD_DIM);
        g.fill(px + pw - 1, y + 15, px + pw,     y + 28, GOLD_DIM);

        // Footer
        g.fill(x, y + FOOTER_Y,      x + W, y + FOOTER_Y + 1,  GOLD_DIM);
        g.fill(x, y + FOOTER_Y + 1,  x + W, y + FOOTER_Y + 15, FOOTER_BG);
        g.fill(x, y + FOOTER_Y + 15, x + W, y + H,              BG);

        // Gold border
        g.fill(x,         y,         x + W, y + 1,     GOLD);
        g.fill(x,         y + H - 1, x + W, y + H,     GOLD);
        g.fill(x,         y,         x + 1, y + H,     GOLD);
        g.fill(x + W - 1, y,         x + W, y + H,     GOLD);

        // Corner dots
        int d = 3;
        g.fill(x + 2,         y + 2,         x + 2 + d, y + 2 + d,     GOLD);
        g.fill(x + W - 2 - d, y + 2,         x + W - 2, y + 2 + d,     GOLD);
        g.fill(x + 2,         y + H - 2 - d, x + 2 + d, y + H - 2,     GOLD);
        g.fill(x + W - 2 - d, y + H - 2 - d, x + W - 2, y + H - 2,     GOLD);

        renderSidebar(g, x, y, mouseX, mouseY);
        renderItemGrid(g, x, y, mouseX, mouseY);
        renderInventoryBg(g, x, y);
    }

    private void renderSidebar(GuiGraphics g, int x, int y, int mx, int my) {
        int tabH = 26;
        for (int i = 0; i < ShopData.CATEGORIES.size(); i++) {
            ShopData.ShopCategory cat = ShopData.CATEGORIES.get(i);
            int ty  = y + HEADER_H + 4 + i * (tabH + 2);
            boolean act = menu.getCategoryIndex() == i;
            boolean hov = mx >= x + 1 && mx < x + SIDEBAR_W - 1 && my >= ty && my < ty + tabH;
            g.fill(x + 1, ty, x + SIDEBAR_W - 1, ty + tabH, act ? 0xFF1A2B40 : (hov ? 0xFF0F1D2E : SIDEBAR_BG));
            if (act) {
                g.fill(x + 1,             ty, x + 3,              ty + tabH, cat.color());
                g.fill(x + SIDEBAR_W - 1, ty, x + SIDEBAR_W + 1,  ty + tabH, 0xFF1A2B40);
            }
            g.renderItem(resolveItem(cat.iconId()), x + 4, ty + 5);
        }
    }

    private void renderItemGrid(GuiGraphics g, int x, int y, int mx, int my) {
        List<ShopData.ShopItem> items = filteredItems();
        hoveredItem = -1;
        for (int i = 0; i < items.size(); i++) {
            int col = i % ShopMenu.COLS;
            int row = i / ShopMenu.COLS;
            int sx  = x + GRID_X + col * SLOT_SIZE;
            int sy  = y + GRID_Y  + row * SLOT_SIZE;
            boolean hov = mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE;
            if (hov) hoveredItem = i;
            g.fill(sx,     sy,     sx + SLOT_SIZE,     sy + SLOT_SIZE,     SLOT_BDR);
            g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, hov ? 0xFF223D5A : SLOT_BG);
            g.renderItem(resolveItem(items.get(i).itemId()), sx + 1, sy + 1);
        }
    }

    private void renderInventoryBg(GuiGraphics g, int x, int y) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) {
                int sx = x + INV_X + col * SLOT_SIZE, sy = y + 198 + row * SLOT_SIZE;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BDR);
                g.fill(sx, sy, sx + 16, sy + 16, SLOT_BG);
            }
        for (int col = 0; col < 9; col++) {
            int sx = x + INV_X + col * SLOT_SIZE, sy = y + 256;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BDR);
            g.fill(sx, sy, sx + 16, sy + 16, SLOT_BG);
        }
    }

    // ── Labels & tooltip ──────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (hoveredItem >= 0) {
            List<ShopData.ShopItem> items = filteredItems();
            if (hoveredItem < items.size()) {
                ShopData.ShopItem it = items.get(hoveredItem);
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(it.name()));
                if (!it.desc().isEmpty())
                    lines.add(Component.literal(it.desc()).withStyle(s -> s.withColor(0xAAAAAA)));
                if (it.buyable())
                    lines.add(Component.literal("Clic gauche : Acheter " + it.buy() + " €").withStyle(s -> s.withColor(0x88FF88)));
                if (it.sellable())
                    lines.add(Component.literal("Clic droit : Vendre " + it.sell() + " €").withStyle(s -> s.withColor(0xFFAA44)));
                g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // Title "SHOP" centered
        int titleW = font.width("SHOP");
        g.drawString(font, "SHOP", (W - titleW) / 2, 4, GOLD, false);

        // Balance in pill
        String balStr = formatBalance(menu.getBalance()) + " €";
        int pw = font.width(balStr) + 20;
        int px = (W - pw) / 2;
        g.drawString(font, balStr, px + 6, 18, GOLD, false);

        // Category name (sub-header row 1)
        ShopData.ShopCategory cat = ShopData.CATEGORIES.get(menu.getCategoryIndex());
        g.drawString(font, "■", GRID_X,                        HEADER_H + 3, cat.color(),  false);
        g.drawString(font, cat.label(), GRID_X + font.width("■ "), HEADER_H + 3, 0xFFCCDDEE, false);

        // Inventory label
        int invLabelW = font.width("Inventaire");
        g.drawString(font, "Inventaire", (W - invLabelW) / 2, FOOTER_Y + 18, INV_LABEL, false);
    }

    // ── Mouse ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;

        // Category tabs
        int tabH = 26;
        for (int i = 0; i < ShopData.CATEGORIES.size(); i++) {
            int ty = y + HEADER_H + 4 + i * (tabH + 2);
            if (mx >= x + 1 && mx < x + SIDEBAR_W - 1 && my >= ty && my < ty + tabH) {
                menu.setCategory(i);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
                return true;
            }
        }

        // Item grid — left=buy, right=sell
        List<ShopData.ShopItem> items = filteredItems();
        for (int i = 0; i < items.size(); i++) {
            int col = i % ShopMenu.COLS;
            int row = i / ShopMenu.COLS;
            int sx  = x + GRID_X + col * SLOT_SIZE;
            int sy  = y + GRID_Y  + row * SLOT_SIZE;
            if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
                if (button == 0) {
                    PacketDistributor.sendToServer(new BuyItemPacket(items.get(i).id()));
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                if (button == 1) {
                    PacketDistributor.sendToServer(new SellItemPacket(items.get(i).id()));
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private List<ShopData.ShopItem> filteredItems() {
        String q = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        var items = menu.currentPageItems();
        if (q.isEmpty()) return items;
        return items.stream()
            .filter(it -> it.name().toLowerCase().contains(q) || it.desc().toLowerCase().contains(q))
            .toList();
    }

    private static ItemStack resolveItem(String itemId) {
        try {
            var loc = ResourceLocation.tryParse(itemId);
            if (loc == null) return new ItemStack(Items.BARRIER);
            return new ItemStack(BuiltInRegistries.ITEM.get(loc));
        } catch (Exception e) {
            return new ItemStack(Items.BARRIER);
        }
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
