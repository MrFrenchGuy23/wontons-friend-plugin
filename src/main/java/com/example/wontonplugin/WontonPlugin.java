package com.example.wontonplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.*;

public class WontonPlugin extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    private DatabaseHandler database;
    private final Map<UUID, List<String>> friendsData = new HashMap<>();
    private final Map<UUID, Set<UUID>> pendingRequests = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String dbPath = getConfig().getString("database.path", "plugins/WontonPlugin/friends.db");
        database = new DatabaseHandler(dbPath, getLogger());

        try {
            database.connect();
            loadFriends();
            loadPendingRequests();
        } catch (SQLException e) {
            getLogger().severe("Could not connect to database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (this.getCommand("friend") != null) {
            this.getCommand("friend").setExecutor(this);
            this.getCommand("friend").setTabCompleter(this);
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("WontonPlugin enabled with SQLite storage.");
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.disconnect();
        }
        friendsData.clear();
        pendingRequests.clear();
        getLogger().info("WontonPlugin disabled.");
    }

    private void loadFriends() {
        friendsData.clear();
        friendsData.putAll(database.loadAllFriends());
    }

    private void loadPendingRequests() {
        pendingRequests.clear();
        pendingRequests.putAll(database.loadAllPendingRequests());
    }

    private static Component green(String msg)  { return Component.text(msg).color(NamedTextColor.GREEN); }
    private static Component red(String msg)    { return Component.text(msg).color(NamedTextColor.RED); }
    private static Component yellow(String msg) { return Component.text(msg).color(NamedTextColor.YELLOW); }
    private static Component gray(String msg)   { return Component.text(msg).color(NamedTextColor.GRAY); }
    private static Component aqua(String msg)   { return Component.text(msg).color(NamedTextColor.AQUA); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(red("Only players can use friend commands!"));
            return true;
        }

        UUID playerUUID = player.getUniqueId();

        friendsData.putIfAbsent(playerUUID, new ArrayList<>());
        List<String> friends = friendsData.get(playerUUID);

        if (args.length == 0) {
            player.sendMessage(yellow("Available commands:"));
            player.sendMessage(yellow("/friend add <username>"));
            player.sendMessage(yellow("/friend accept <username>"));
            player.sendMessage(yellow("/friend list"));
            player.sendMessage(yellow("/friend remove <username>"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add": {
                if (args.length < 2) {
                    player.sendMessage(red("Usage: /friend add <username>"));
                    return true;
                }

                String targetInput = args[1];
                Player targetOnline = Bukkit.getPlayerExact(targetInput);

                if (targetOnline == null) {
                    targetOnline = Bukkit.getOnlinePlayers().stream()
                            .filter(p -> p.getName().equalsIgnoreCase(targetInput))
                            .findFirst()
                            .orElse(null);
                }

                if (targetOnline == null) {
                    player.sendMessage(red("That player is not online!"));
                    return true;
                }

                String actualName = targetOnline.getName();

                if (actualName.equalsIgnoreCase(player.getName())) {
                    player.sendMessage(red("You cannot add yourself as a friend!"));
                    return true;
                }

                boolean alreadyAdded = friends.stream().anyMatch(f -> f.equalsIgnoreCase(actualName));
                if (alreadyAdded) {
                    player.sendMessage(red(actualName + " is already on your friends list."));
                    return true;
                }

                UUID targetUUID = targetOnline.getUniqueId();
                pendingRequests.putIfAbsent(targetUUID, new HashSet<>());
                Set<UUID> targetPending = pendingRequests.get(targetUUID);

                if (targetPending.contains(playerUUID)) {
                    player.sendMessage(red("You have already sent a friend request to " + actualName + "!"));
                    return true;
                }

                targetPending.add(playerUUID);
                database.addPendingRequest(targetUUID, playerUUID);

                player.sendMessage(green("Friend request sent to " + actualName + "!"));
                targetOnline.sendMessage(aqua(player.getName() + " has sent you a friend request! Type /friend accept " + player.getName()));
                break;
            }

            case "accept": {
                if (args.length < 2) {
                    player.sendMessage(red("Usage: /friend accept <username>"));
                    return true;
                }

                String requesterInput = args[1];
                UUID myUUID = player.getUniqueId();
                Set<UUID> myPending = pendingRequests.get(myUUID);

                if (myPending == null || myPending.isEmpty()) {
                    player.sendMessage(red("You don't have any pending friend requests."));
                    return true;
                }

                UUID requesterUUID = null;
                String exactRequesterName = null;

                for (UUID reqUUID : myPending) {
                    String name = Bukkit.getOfflinePlayer(reqUUID).getPlayerProfile().getName();
                    if (name != null && name.equalsIgnoreCase(requesterInput)) {
                        requesterUUID = reqUUID;
                        exactRequesterName = name;
                        break;
                    }
                }

                if (requesterUUID == null) {
                    player.sendMessage(red("You don't have a pending friend request from " + requesterInput + "."));
                    return true;
                }

                myPending.remove(requesterUUID);
                database.removePendingRequest(myUUID, requesterUUID);

                friends.add(exactRequesterName);
                database.addFriend(playerUUID, exactRequesterName);

                friendsData.putIfAbsent(requesterUUID, new ArrayList<>());
                friendsData.get(requesterUUID).add(player.getName());
                database.addFriend(requesterUUID, player.getName());

                player.sendMessage(green("You are now friends with " + exactRequesterName + "!"));

                Player requesterPlayer = Bukkit.getPlayer(requesterUUID);
                if (requesterPlayer != null) {
                    requesterPlayer.sendMessage(green(player.getName() + " accepted your friend request!"));
                }
                break;
            }

            case "list": {
                if (friends.isEmpty()) {
                    player.sendMessage(yellow("Your friends list is empty."));
                } else {
                    player.sendMessage(yellow("Your Friends:"));
                    for (String friendName : friends) {
                        Player fPlayer = Bukkit.getOnlinePlayers().stream()
                                .filter(p -> p.getName().equalsIgnoreCase(friendName))
                                .findFirst()
                                .orElse(null);
                        if (fPlayer != null) {
                            player.sendMessage(green("- " + friendName + " (Online)"));
                        } else {
                            player.sendMessage(gray("- " + friendName + " (Offline)"));
                        }
                    }
                }
                break;
            }

            case "remove": {
                if (args.length < 2) {
                    player.sendMessage(red("Usage: /friend remove <username>"));
                    return true;
                }
                String targetRemove = args[1];

                boolean removed = friends.removeIf(f -> f.equalsIgnoreCase(targetRemove));

                if (removed) {
                    database.removeFriend(playerUUID, targetRemove);

                    for (Map.Entry<UUID, List<String>> entry : friendsData.entrySet()) {
                        if (!entry.getKey().equals(playerUUID)) {
                            boolean wasRemoved = entry.getValue().removeIf(f -> f.equalsIgnoreCase(player.getName()));
                            if (wasRemoved) {
                                database.removeFriendFromTarget(entry.getKey(), player.getName());
                            }
                        }
                    }

                    database.removePendingRequestsForPlayer(playerUUID);
                    for (Map.Entry<UUID, Set<UUID>> pendingEntry : pendingRequests.entrySet()) {
                        pendingEntry.getValue().removeIf(uuid -> uuid.equals(playerUUID));
                    }
                    pendingRequests.getOrDefault(playerUUID, new HashSet<>()).clear();

                    player.sendMessage(green("Removed " + targetRemove + " from your friends list."));
                } else {
                    player.sendMessage(red(targetRemove + " is not on your friends list."));
                }
                break;
            }

            default:
                player.sendMessage(red("Invalid command. Type /friend for available commands."));
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("add", "accept", "list", "remove");
            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("remove")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            } else if (sub.equals("accept") && sender instanceof Player) {
                Player player = (Player) sender;
                Set<UUID> pending = pendingRequests.get(player.getUniqueId());
                if (pending != null) {
                    for (UUID reqUUID : pending) {
                        String name = Bukkit.getOfflinePlayer(reqUUID).getPlayerProfile().getName();
                        if (name != null && name.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(name);
                        }
                    }
                }
            }
        }

        return completions;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joinedPlayer = event.getPlayer();
        String joinedName = joinedPlayer.getName();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            List<String> theirFriends = friendsData.get(onlinePlayer.getUniqueId());
            if (theirFriends != null) {
                if (theirFriends.stream().anyMatch(f -> f.equalsIgnoreCase(joinedName))) {
                    onlinePlayer.sendMessage(aqua("Your friend " + joinedName + " just joined the server!"));
                }
            }
        }
    }
}
