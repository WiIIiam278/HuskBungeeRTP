package me.william278.huskbungeertp.mysql;

import me.william278.huskbungeertp.HuskBungeeRTP;
import me.william278.huskbungeertp.config.Group;
import org.bukkit.Bukkit;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;

public class DataHandler {

    private static HuskBungeeRTP plugin;
    private static Database database;
    private static Connection getConnection() { return database.getConnection(); }
    public static void loadDatabase(HuskBungeeRTP instance) {
        database = new MySQL(instance);
        database.load();
        plugin = instance;
    }

    public static void addPlayerIfNotExist(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection connection = getConnection();
            try(PreparedStatement checkIfPlayerExist = connection.prepareStatement(
                    "SELECT * FROM " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " WHERE `user_uuid`=? LIMIT 1;")) {
                checkIfPlayerExist.setString(1, uuid.toString());
                final ResultSet playerExistResultSet = checkIfPlayerExist.executeQuery();
                // If the player does not exist yet
                if (!playerExistResultSet.next()) {
                    try(PreparedStatement createPlayerStatement = connection.prepareStatement(
                            "INSERT INTO " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " (`user_uuid`) VALUES (?)")) {
                        createPlayerStatement.setString(1, uuid.toString());
                        createPlayerStatement.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "An SQL exception has occurred", e);
            }
        });
    }

    /*
    Not thread safe, perform inside a RunTaskAsynchronously!
     */
    public static boolean getPlayerTeleporting(UUID uuid) {
        boolean isPlayerTeleporting = false;
        Connection connection = getConnection();
        try(PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT * FROM " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " WHERE `user_uuid`=? LIMIT 1;")) {
            preparedStatement.setString(1, uuid.toString());
            final ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                isPlayerTeleporting = resultSet.getBoolean("is_performing_rtp");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "An SQL exception has occurred", e);
        }
        return isPlayerTeleporting;
    }

    public static void setPlayerTeleporting(UUID uuid, boolean teleporting) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection connection = getConnection();
            try(PreparedStatement preparedStatement = connection.prepareStatement(
                    "UPDATE " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " SET `is_performing_rtp`=? WHERE `user_uuid`=? LIMIT 1;")) {
                preparedStatement.setBoolean(1, teleporting);
                preparedStatement.setString(2, uuid.toString());
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "An SQL exception has occurred", e);
            }
        });
    }

    /*
    Not thread safe, perform inside a RunTaskAsynchronously!
     */
    public static boolean isPlayerOnCoolDown(UUID uuid, Group group) {
        boolean isPlayerOnCoolDown = false;
        Connection connection = getConnection();
        try(PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT * FROM " + group.getGroupDatabaseTableName() + " WHERE `player_id`=(SELECT `id` FROM " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " WHERE `user_uuid`=? LIMIT 1) LIMIT 1;")) {
            preparedStatement.setString(1, uuid.toString());
            final ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                Date lastRtpDate = resultSet.getDate("last_rtp");
                if (Instant.now().getEpochSecond() >= lastRtpDate.toInstant().getEpochSecond() + (60L * group.coolDownTimeMinutes())) {
                    try(PreparedStatement deletePlayerCoolDownStatement = connection.prepareStatement(
                            "DELETE FROM " + group.getGroupDatabaseTableName() + " WHERE `player_id`=(SELECT `id` FROM " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " WHERE `user_uuid`=? LIMIT 1) LIMIT 1;")) {
                        deletePlayerCoolDownStatement.setString(1, uuid.toString());
                        deletePlayerCoolDownStatement.executeUpdate();
                    }
                } else {
                    isPlayerOnCoolDown = true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "An SQL exception has occurred", e);
        }
        return isPlayerOnCoolDown;
    }

    public static void setPlayerOnCoolDown(UUID uuid, Group group) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
           Connection connection = getConnection();
           try(PreparedStatement preparedStatement = connection.prepareStatement(
                   "INSERT INTO " + group.getGroupDatabaseTableName() + " (`player_id`) VALUES (SELECT `id` FROM + " + HuskBungeeRTP.getSettings().getDatabasePlayerTableName() + " WHERE `user_uuid`=? LIMIT 1);")) {
               preparedStatement.setString(1, uuid.toString());
               preparedStatement.executeUpdate();
           } catch (SQLException e) {
               plugin.getLogger().log(Level.SEVERE, "An SQL exception has occurred", e);
           }
        });
    }

}