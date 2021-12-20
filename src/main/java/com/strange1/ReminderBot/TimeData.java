package com.strange1.ReminderBot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

public class TimeData {
    enum Field {
        YEAR,
        MONTH, //1~12
        DAY,
        HOUR,
        MINUTE,
        SECOND,
        MILLISECOND
    }

    private ArrayList<Integer> tokens;
    private long time;
    private TimeZone timeZone;

    public TimeData(long time, TimeZone timeZone) {
        init(time, timeZone);
    }

    public TimeData(long time) {
        init(time, TimeZone.getTimeZone("UTC"));
    }

    public TimeData() {
        init(0, TimeZone.getTimeZone("UTC"));
    }

    private void init(long time, TimeZone timeZone) {
        tokens = new ArrayList<>(7);
        this.time = time;
        this.timeZone = timeZone;
        posixToTokens();
    }

    private void posixToTokens() {
        var builder = new Calendar.Builder();
        builder.setTimeZone(timeZone);
        builder.setInstant(time);
        var calendar = builder.build();
        tokens.add(calendar.get(Calendar.YEAR));
        tokens.add(calendar.get(Calendar.MONTH) + 1); //CAUTION: zero-base
        tokens.add(calendar.get(Calendar.DAY_OF_MONTH));
        tokens.add(calendar.get(Calendar.HOUR_OF_DAY));
        tokens.add(calendar.get(Calendar.MINUTE));
        tokens.add(calendar.get(Calendar.SECOND));
        tokens.add(calendar.get(Calendar.MILLISECOND));
    }

    private void fieldsToPosix() {
        var builder = new Calendar.Builder();
        builder.setTimeZone(timeZone);
        builder.setDate(tokens.get(0), tokens.get(1) - 1, tokens.get(2));
        builder.setTimeOfDay(tokens.get(3), tokens.get(4), tokens.get(5));
        var calendar = builder.build();
        time = calendar.getTimeInMillis();
    }

    public int getField(Field field) {
        return tokens.get(field.ordinal());
    }

    public TimeData setField(Field field, int value) {
        tokens.set(field.ordinal(), value);
        fieldsToPosix();
        return this;
    }

    public long getPosixTime() {
        return time;
    }

    public TimeData setTime(long time) {
        init(time, this.timeZone);
        return this;
    }

    public TimeData setTimeZone(TimeZone timeZone) {
        init(this.time, timeZone);
        return this;
    }
}
