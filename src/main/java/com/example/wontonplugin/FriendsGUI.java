package com.example.wontonplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class FriendsGUI {

    private static final String FRIENDS_TITLE = "\u00a76Your Friends";
    private static final String ACTION_TITLE = "\u00a76Friend Options";

    private final WontonPlugin plugin;

    public FriendsGUI(WontonPlugin plugin) {
        this.plugin = plugin;
    }

    public void openFriendsList(Player player, int page) {
        UUID uuid = player.getUniqueId();
        List<String> friends = plugin.getFriends(uuid);
        List<UUID> pending = plugin.getPendingRequesters(uuid);

        int perPage = 9;
        int totalPages = Math.max(1, (int) Math.ceil((double) friends.size() / perPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = FRIENDS_TITLE + " (" + (page + 1) + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(null, 27, title);

        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int idx = start + i;
            if (idx >= friends.size()) break;
            String name = friends.get(idx);
            inv.setItem(i, makeFriendHead(name, uuid));
        }

        int slot = 9;
        for (UUID reqUUID : pending) {
            if (slot >= 18) break;
            String name = Bukkit.getOfflinePlayer(reqUUID).getPlayerProfile().getName();
            if (name == null) continue;
            inv.setItem(slot, makePendingHead(name));
            slot++;
        }

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.setDisplayName(" ");
        pane.setItemMeta(paneMeta);
        for (int i = slot; i < 18; i++) {
            inv.setItem(i, pane);
        }

        boolean requestsOn = plugin.getReceiveRequests(uuid);
        inv.setItem(18, makeToggleItem(requestsOn));

        if (page > 0) {
            inv.setItem(19, makePageItem("Previous Page", Material.ARROW));
        }
        if (page < totalPages - 1) {
            inv.setItem(20, makePageItem("Next Page", Material.ARROW));
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("\u00a7cClose");
        close.setItemMeta(closeMeta);
        inv.setItem(26, close);

        player.openInventory(inv);
    }

    public void openActionMenu(Player player, String friendName) {
        Inventory inv = Bukkit.createInventory(null, 9, ACTION_TITLE + " - " + friendName);

        ItemStack remove = new ItemStack(Material.RED_WOOL);
        ItemMeta rMeta = remove.getItemMeta();
        rMeta.setDisplayName("\u00a7cRemove " + friendName);
        rMeta.setLore(Arrays.asList("\u00a77Click to remove " + friendName, "\u00a77from your friends list."));
        remove.setItemMeta(rMeta);
        inv.setItem(2, remove);

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = cancel.getItemMeta();
        cMeta.setDisplayName("\u00a7cCancel");
        cancel.setItemMeta(cMeta);
        inv.setItem(6, cancel);

        player.openInventory(inv);
    }

    private ItemStack makeFriendHead(String name, UUID viewerUUID) {
        ItemStack head = getHead(name);
        ItemMeta meta = head.getItemMeta();
        meta.setDisplayName("\u00a7a" + name);

        List<String> lore = new ArrayList<>();
        Player online = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
        lore.add(online != null ? "\u00a7a\u00a7l\u25cf ONLINE" : "\u00a77\u00a7l\u25cf OFFLINE");
        lore.add("\u00a7eClick for options");
        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack makePendingHead(String name) {
        ItemStack head = getHead(name);
        ItemMeta meta = head.getItemMeta();
        meta.setDisplayName("\u00a7e" + name);
        meta.setLore(Arrays.asList("\u00a77Wants to be your friend!", "\u00a7eClick to accept"));
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack makeToggleItem(boolean on) {
        ItemStack item = new ItemStack(on ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("\u00a76Friend Requests: " + (on ? "\u00a7aON" : "\u00a7cOFF"));
        meta.setLore(Arrays.asList("\u00a77Click to toggle receiving", "\u00a77friend requests from others."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makePageItem(String name, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("\u00a7e" + name);
        item.setItemMeta(meta);
        return item;
    }

    @SuppressWarnings("deprecation")
    private ItemStack getHead(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
            head.setItemMeta(meta);
        }
        return head;
    }
}
