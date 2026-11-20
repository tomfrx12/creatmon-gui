package fr.creatmon.shop.auction;

import fr.creatmon.shop.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class AuctionMenu extends AbstractContainerMenu {

    private static final int INV_X = (256 - 9 * 18) / 2; // = 47

    private int balance;
    private List<AHListingData>    listings   = new ArrayList<>();
    private List<ItemStack>        inboxItems = new ArrayList<>();
    private Map<String, Double>    avgPrices  = new HashMap<>();

    private final ContainerData data = new ContainerData() {
        @Override public int get(int i)     { return i == 0 ? balance : 0; }
        @Override public void set(int i, int v) { if (i == 0) balance = v; }
        @Override public int getCount()     { return 1; }
    };

    // ── Server constructor ────────────────────────────────────────────

    public AuctionMenu(int id, Inventory inv, int balance) {
        super(ModMenus.AUCTION_MENU.get(), id);
        this.balance = balance;
        addDataSlots(data);
        addPlayerSlots(inv);
    }

    // ── Client constructor (called by IMenuTypeExtension) ─────────────

    public AuctionMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(ModMenus.AUCTION_MENU.get(), id);
        var rb = (RegistryFriendlyByteBuf) buf;
        this.balance = rb.readInt();

        int n = rb.readInt();
        for (int i = 0; i < n; i++) {
            listings.add(new AHListingData(
                rb.readUUID(), rb.readUUID(), rb.readUtf(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(rb),
                rb.readInt(), rb.readLong(), rb.readLong()));
        }
        int m = rb.readInt();
        for (int i = 0; i < m; i++) inboxItems.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(rb));
        int p = rb.readInt();
        for (int i = 0; i < p; i++) avgPrices.put(rb.readUtf(), rb.readDouble());

        addDataSlots(data);
        addPlayerSlots(inv);
    }

    private void addPlayerSlots(Inventory inv) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, 198 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, INV_X + col * 18, 256));
    }

    // ── Getters / setters ─────────────────────────────────────────────

    public int               getBalance()   { return balance; }
    public void              updateBalance(int v) { this.balance = v; }
    public List<AHListingData> getListings() { return listings; }
    public List<ItemStack>   getInboxItems() { return inboxItems; }
    public int               getInboxCount() { return inboxItems.size(); }
    public double            getAvgPrice(String itemId) { return avgPrices.getOrDefault(itemId, 0.0); }

    public void updateListings(List<AHListingData> l)   { this.listings   = new ArrayList<>(l); }
    public void updateInbox(List<ItemStack> items)       { this.inboxItems = new ArrayList<>(items); }
    public void updateAvgPrices(Map<String, Double> m)  { this.avgPrices  = new HashMap<>(m); }

    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) { return true; }
}
