package com.strange1.ReminderBot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

public final class BotSQL {
    public static Connection connection = null;

    public static void setConnection(Connection connection) {
        BotSQL.connection = connection;
    }

    public static boolean CheckConnection() throws SQLException {
        if (connection == null) {
            throw new SQLException("BotSQL: Not SQL connection established.");
        } else
            return true;
    }

    public static Object RunCommand(String statement) throws SQLException {
        CheckConnection();
        return null;
    }

    public static boolean WriteSchedule(SqlScheduleBundle bundle) throws SQLException {
        CheckConnection();
        PreparedStatement statement = connection.prepareStatement("insert into schedules values (?,?,?,?,?,?,?,?,?,?,?);");
        statement.setLong(1, bundle.id);
        statement.setString(2, bundle.GuildId);
        statement.setString(3, bundle.MessageChannelId);
        statement.setString(4, bundle.ClientId);
        statement.setNString(5, bundle.Message);
        statement.setLong(6, bundle.ListedTime);
        statement.setLong(7, bundle.AlarmTime);
        statement.setNString(8, bundle.to);
        statement.setLong(9, bundle.RepeatTime);
        statement.setInt(10, bundle.Status.ordinal());
        statement.setString(11, bundle.Code);
        return statement.executeUpdate() > 0;
    }

    public static String MakeNewCode(@Nullable String RandomPool) throws SQLException {
        CheckConnection();
        PreparedStatement statement = connection.prepareStatement("select * from schedules where code=?");
        String newCode;
        String randomPool = RandomPool == null ? "ABCDEFGHJKLMNPSTUWXYZ" : RandomPool;
        Random random = new Random();
        do {
            newCode = "";
            for (var i = 0; i < 8; i++)
                newCode += randomPool.charAt(random.nextInt(randomPool.length()));
            statement.setString(1, newCode);
        } while (statement.executeQuery().next());
        return newCode;
    }

    public static long getNewId() throws SQLException {
        CheckConnection();
        PreparedStatement statement = connection.prepareStatement("select * from schedules where id=?");
        long newId;
        Random random = new Random();
        do {
            newId = random.nextLong();
            statement.setLong(1, newId);
        } while (statement.executeQuery().next());
        return newId;
    }

    public static ResultSet ReadSchedulesById(String GuildId, String MessageCID, String ClientId) throws SQLException {
        CheckConnection();
        PreparedStatement statement = connection.prepareStatement("select * from schedules where GuildId = ? and ClientId = ? and AlarmTime > ? and Status = ? order by ListedTime;");
        statement.setString(1, GuildId);
        statement.setString(2, ClientId);
        statement.setLong(3, System.currentTimeMillis());
        statement.setInt(4, SqlScheduleBundle.StatusId.ACTIVE.ordinal());

        return statement.executeQuery();
    }

    public static ResultSet ReadSchedulesByTime(long TimeLeft, long TimeRight, boolean ActiveOnly) throws SQLException {
        CheckConnection();
        PreparedStatement statement;
        statement = connection.prepareStatement(String.format("select * from schedules where AlarmTime >= ? and AlarmTime < ?%s;", ActiveOnly ? (" and Status = " + SqlScheduleBundle.StatusId.ACTIVE.ordinal()) : ""));
        statement.setLong(1, TimeLeft);
        statement.setLong(2, TimeRight);
        return statement.executeQuery();
    }

    public static int CancelSchedulesByCode(String ClientId, String Code) throws SQLException {
        CheckConnection();
        PreparedStatement statement = connection.prepareStatement("update schedules set Status = ? where ClientId = ? and code = ?;");
        statement.setInt(1, SqlScheduleBundle.StatusId.CANCELLED.ordinal());
        statement.setString(2, ClientId);
        statement.setString(3, Code);
        return statement.executeUpdate();
    }

    public static int CompleteSchedulesByCode(String Code) throws SQLException {
        CheckConnection();
        PreparedStatement statement = connection.prepareStatement("update schedules set Status = ? where code = ?;");
        statement.setInt(1, SqlScheduleBundle.StatusId.COMPLETED.ordinal());
        statement.setString(2, Code);
        return statement.executeUpdate();
    }

    public static String ReadLocaleById(String ClientId) throws SQLException {
        CheckConnection();
        PreparedStatement statement = connection.prepareStatement("select * from Users where ClientId = ?;");
        statement.setString(1, ClientId);
        var rs = statement.executeQuery();
        if (rs.next()) {
            return rs.getString(2);
        } else
            return "EN";
    }

    public static Pair<Integer, Integer> ReadTimezoneById(String ClientId) throws SQLException {
        CheckConnection();
        PreparedStatement statement = connection.prepareStatement("select * from Users where ClientId = ?;");
        statement.setString(1, ClientId);
        var rs = statement.executeQuery();
        Pair<Integer, Integer> timezone = Pair.of(0, 0);
        if (rs.next()) {
            timezone = Pair.of(rs.getInt(3), rs.getInt(4));
            return timezone;
        } else
            return timezone;
    }

    public static int ChangeTimezone(String ClientId, int hour, int minute) throws SQLException {
        CheckConnection();
        PreparedStatement statement = connection.prepareStatement("select * from Users where ClientId = ?;");
        statement.setString(1, ClientId);
        if (!statement.executeQuery().next()) {
            statement = connection.prepareStatement("insert into Users values (?, ?, ?, ?)");
            statement.setString(1, ClientId);
            statement.setString(2, "EN");
            statement.setInt(3, 0);
            statement.setInt(4, 0);
            statement.executeUpdate();
        }
        statement = connection.prepareStatement("update Users set TimezoneHour = ?, TimezoneMinute = ? where ClientId = ?;");
        statement.setInt(1, hour);
        statement.setInt(2, minute);
        statement.setString(3, ClientId);
        return statement.executeUpdate();
    }

    public static String getPermission(Guild guild) throws SQLException {
        CheckConnection();
        PreparedStatement statement = connection.prepareStatement("select * from Permissions where GuildId = ?;");
        statement.setString(1, guild.getId());
        ResultSet rs = statement.executeQuery();
        if (rs.next()) {
            return rs.getString(2);
        } else {
            return "@everyone";
        }
    }

    public static void setPermission(Guild guild, String permission) throws SQLException {
        CheckConnection();
        PreparedStatement statement = connection.prepareStatement("select * from Permissions where GuildID = ?;");
        statement.setString(1, guild.getId());
        if (!statement.executeQuery().next()) {
            statement = connection.prepareStatement("insert into Permissions values(?, ?)");
            statement.setString(1, guild.getId());
            statement.setString(2, "@everyone");
            statement.executeUpdate();
        }
        statement = connection.prepareStatement("Update Permissions set Role = ? where GuildID = ?;");
        statement.setString(1, permission);
        statement.setString(2, guild.getId());
        statement.executeUpdate();
    }
}
