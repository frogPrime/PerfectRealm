package Star.perfectRealm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class PerfectRealm extends JavaPlugin implements Listener, CommandExecutor {
    private final HashMap<UUID, Integer> playerRealmLevels = new HashMap<>();
    private FileConfiguration config;
    private FileConfiguration dataConfig;
    private File dataConfigFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();  // 确保配置文件存在
        this.config = getConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        // 注册指令
        Objects.requireNonNull(getCommand("perfectrealm")).setExecutor(this);

        loadDataConfig();
        loadPlayerData();
        getLogger().info("完全境界插件已成功启用！");
    }

    @Override
    public void onDisable() {
        savePlayerData();
        getLogger().info("完全境界插件已成功关闭！");
    }

    public void setPerfectRealmLevel(Player player, int level) {
        level = Math.max(0, Math.min(level, 10));
        playerRealmLevels.put(player.getUniqueId(), level);

        // 立即保存数据
        dataConfig.set("players." + player.getUniqueId(), level);
        saveDataConfig();

        player.sendMessage(Component.text("你的完全境界等级已设置为: " + level, NamedTextColor.AQUA));
    }

    public int getPerfectRealmLevel(Player player) {
        return playerRealmLevels.getOrDefault(player.getUniqueId(), 0);
    }

    public void addPerfectRealmLevel(Player player, int amount) {
        setPerfectRealmLevel(player, getPerfectRealmLevel(player) + amount);
    }

    public void takePerfectRealmLevel(Player player, int amount) {
        setPerfectRealmLevel(player, getPerfectRealmLevel(player) - amount);
    }

    private void loadDataConfig() {
        dataConfigFile = new File(getDataFolder(), "data.yml");
        if (!dataConfigFile.exists()) {
            try {
                dataConfigFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("无法创建 data.yml 文件！");
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataConfigFile);
    }

    private void saveDataConfig() {
        try {
            dataConfig.save(dataConfigFile);
        } catch (IOException e) {
            getLogger().severe("无法保存玩家数据到 data.yml");
            e.printStackTrace();
        }
    }

    private void loadPlayerData() {
        if (dataConfig.contains("players")) {
            for (String key : Objects.requireNonNull(dataConfig.getConfigurationSection("players")).getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                int level = dataConfig.getInt("players." + key);
                playerRealmLevels.put(uuid, level);
            }
        }
    }

    private void savePlayerData() {
        for (UUID uuid : playerRealmLevels.keySet()) {
            dataConfig.set("players." + uuid.toString(), playerRealmLevels.get(uuid));
        }
        saveDataConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        int realmLevel = getPerfectRealmLevel(player);
        double multiplier = config.getDouble("damage_multiplier_base", 2.0);
        double finalMultiplier = Math.pow(multiplier, realmLevel);
        event.setDamage(event.getDamage() * finalMultiplier);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        int realmLevel = getPerfectRealmLevel(player);
        double reductionBase = config.getDouble("damage_reduction_base", 2.0);
        double reductionFactor = Math.pow(reductionBase, realmLevel);
        event.setDamage(event.getDamage() / reductionFactor);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("perfectrealm.admin")) {
            sender.sendMessage(Component.text("你没有权限使用此命令。", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("用法: /perfectrealm <set|add|take|reload> <玩家> [等级]", NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadDataConfig();
            loadPlayerData();
            this.config = getConfig();

            sender.sendMessage(Component.text("PerfectRealm 配置已重载！", NamedTextColor.GREEN));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("用法: /perfectrealm <set|add|take> <玩家> [等级]", NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("玩家不存在或不在线。", NamedTextColor.RED));
            return true;
        }

        try {
            int level = Integer.parseInt(args[2]);
            if (args[0].equalsIgnoreCase("set")) {
                setPerfectRealmLevel(target, level);
                sender.sendMessage(Component.text("已将 " + target.getName() + " 的完全境界设置为 " + level, NamedTextColor.GREEN));
            } else if (args[0].equalsIgnoreCase("add")) {
                addPerfectRealmLevel(target, level);
                sender.sendMessage(Component.text("已增加 " + target.getName() + " 的完全境界 " + level + " 级。", NamedTextColor.GREEN));
            } else if (args[0].equalsIgnoreCase("take")) {
                takePerfectRealmLevel(target, level);
                sender.sendMessage(Component.text("已减少 " + target.getName() + " 的完全境界 " + level + " 级。", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("用法: /perfectrealm <set|add|take|reload> <玩家> [等级]", NamedTextColor.YELLOW));
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("请输入有效的数字等级！", NamedTextColor.RED));
        }

        return true;
    }
}
