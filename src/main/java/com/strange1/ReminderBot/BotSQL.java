package com.strange1.ReminderBot;

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

    public static boolean isConnectionSet() {
        return connection != null;
    }

    public static Object RunCommand(String statement) throws SQLException {
        if (!isConnectionSet())
            throw new SQLException("BotSQL: Not SQL connection established.");
        return null;
    }

    public static boolean WriteSchedule(SqlScheduleBundle bundle) throws SQLException {
        if (!isConnectionSet())
            throw new SQLException("BotSQL: Not SQL connection established.");
        PreparedStatement statement = connection.prepareStatement("insert into schedules values (?,?,?,?,?,?,?,?,?,?);");
        statement.setLong(1, bundle.id);
        statement.setString(2, bundle.GuildChannelId);
        statement.setString(3, bundle.MessageChannelId);
        statement.setString(4, bundle.ClientId);
        statement.setNString(5, bundle.Message);
        statement.setLong(6, bundle.ListedTime);
        statement.setLong(7, bundle.AlarmTime);
        statement.setNString(8, bundle.to);
        statement.setLong(9, bundle.RepeatTime);
        statement.setInt(10, bundle.Status.ordinal());
        return statement.executeUpdate() > 0;
    }

    public static long getNewId() throws SQLException {
        if (!isConnectionSet())
            throw new SQLException("BotSQL: Not SQL connection established.");
        PreparedStatement statement = connection.prepareStatement("select * from schedules where id=?");
        long newId;
        Random random = new Random();
        do {
            newId = random.nextLong();
            statement.setLong(1, newId);
        } while (statement.executeQuery().getFetchSize()>0);
        return newId;
    }

    public static ResultSet ReadSchedulesById(String GuildCId, String MessageCID, String ClientId) throws SQLException
    {
        if (!isConnectionSet())
            throw new SQLException("BotSQL: Not SQL connection established.");
        PreparedStatement statement = connection.prepareStatement("select * from schedules where GuildChannelId = ? and MessageChannelId = ? and ClientId = ? order by ListedTime;");
        statement.setString(1, GuildCId);
        statement.setString(2, MessageCID);
        statement.setString(3, ClientId);
        return statement.executeQuery();
    }

    public static String ReadLocaleById(String ClientId) throws SQLException {
        if (!isConnectionSet())
            throw new SQLException("BotSQL: Not SQL connection established.");
        PreparedStatement statement = connection.prepareStatement("select * from Users where ClientId = ?;");
        statement.setString(1, ClientId);
        var rs = statement.executeQuery();
        if (rs.next()) {
            return rs.getString(2);
        }
        else
            return null;
    }
}
