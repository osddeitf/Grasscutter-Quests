package emu.grasscutter.utils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Calendar;
import java.util.Date;

public final class DateHelper {
    public static Date onlyYearMonthDay(Date now) {
        Calendar calendar = Calendar.getInstance();
		calendar.setTime(now);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTime();
    }

	public static int getUnixTime(Date localDateTime){
		return (int)(localDateTime.getTime() / 1000L);
	}

    public static long getFutureTime(long amount, TemporalUnit unit) {
        var instant = Instant.now().plus(amount, unit);
        return instant.toEpochMilli();
    }

    public static int getFutureTimeSecond(int amount) {
        return (int)(getFutureTime(amount, ChronoUnit.SECONDS) / 1000);
    }

    public static long getCurrentTime() {
        return Instant.now().toEpochMilli();
    }

    public static int getCurrentTimeSecond() {
        return (int)(getCurrentTime() / 1000);
    }
}
