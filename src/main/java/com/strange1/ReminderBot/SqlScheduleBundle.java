package com.strange1.ReminderBot;

import java.sql.SQLException;

public class SqlScheduleBundle {
    long id;
    String GuildChannelId;
    String MessageChannelId;
    String ClientId;
    String Message;
    long ListedTime;
    long AlarmTime;
    String to;
    long RepeatTime;
    StatusId Status;
    String Code;

    public enum StatusId {
        READY,
        ACTIVE,
        CANCELLED,
        COMPLETED,
        TEST
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setGuild(String id) {
        GuildChannelId = id;
    }

    public void setMessageChannel(String id) {
        MessageChannelId = id;
    }

    public void setClient(String id) {
        ClientId = id;
    }

    public void setMessage(String message) {
        Message = message;
    }

    public void setListedTime(long time) {
        ListedTime = time;
    }

    public void setAlarmTime(long time) {
        AlarmTime = time;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void setRepeatTime(long time) {
        RepeatTime = time;
    }

    public void setStatus(StatusId status) {
        this.Status = status;
    }

    public void setCode(String newCode) {
        this.Code = newCode;
    }
}