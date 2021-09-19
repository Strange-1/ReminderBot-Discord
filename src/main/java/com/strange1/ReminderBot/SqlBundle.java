package com.strange1.ReminderBot;

public class SqlBundle
{
    long Id;
    String Channel;
    String Client;
    long Time;
    String Message;
    String Object;
    long RepeatTime;
    String Status;

    public SqlBundle(long id, String channel, String client, long time, String message, String object, long repeatTime, String status)
    {
        Id = id;
        Channel = channel;
        Client = client;
        Time = time;
        Message = message;
        Object = object;
        RepeatTime = repeatTime;
        Status = status;
    }

    public String getDataString()
    {
        return Id + " | " + Channel + " | " + Client + " | " + Time + " | " + Message + " | " + Object + " | " + RepeatTime + " | " + Status;
    }
}