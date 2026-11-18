package fr.creatmon.shop;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ShopMenu extends AbstractContainerMenu {

    public static final int COLS         = 10;
    public static final int ROWS         = 4;
    public static final int PAGE_SIZE    = COLS * ROWS;

    private final Player player;
    private int balance;
    private int categoryIndex = 0;
    private int page          = 0;

    // 0=balance, 1=categoryIndex, 2=page
    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> balance;
                case 1 -> categoryIndex;
                case 2 -> page;
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {
            switch (i) {
                case 0 -> balance       = v;
                case 1 -> categoryIndex = v;
                case 2 -> page          = v;
            }
        }
        @Override public int getCount() { return 3; }
    };

    // Client factory
    public ShopMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readInt());
    }

    // Server constructor
    public ShopMenu(int containerId, Inventory playerInventory, int balance) {
        super(ModMenus.SHOP_MENU.get(), containerId);
        this.player  = playerInventory.player;
        this.balance = balance;
        addDataSlots(data);

        // Player inventory — centered horizontally in 256px
        int invX = (256 - 9 * 18) / 2; // = 47
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, invX + col * 18, 198 + row * 18));
        // Hotbar
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, invX + col * 18, 256));
    }

    public int getBalance()          { return balance;       }
    public void updateBalance(int v) { this.balance = v;     }
    public int getCategoryIndex() { return categoryIndex; }
    public int getPage()          { return page;          }

    public void setCategory(int index) {
        categoryIndex = Math.max(0, Math.min(index, ShopData.CATEGORIES.size() - 1));
        page = 0;
    }

    public void nextPage() {
        int itemCount = ShopData.CATEGORIES.get(categoryIndex).items().size();
        int maxPage   = Math.max(0, (itemCount - 1) / PAGE_SIZE);
        if (page < maxPage) page++;
    }

    public void prevPage() {
        if (page > 0) page--;
    }

    public ShopData.ShopCategory currentCategory() {
        return ShopData.CATEGORIES.get(categoryIndex);
    }

    public java.util.List<ShopData.ShopItem> currentPageItems() {
        var items = currentCategory().items();
        int from  = page * PAGE_SIZE;
        int to    = Math.min(from + PAGE_SIZE, items.size());
        return from < items.size() ? items.subList(from, to) : java.util.List.of();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) { return true; }
}
