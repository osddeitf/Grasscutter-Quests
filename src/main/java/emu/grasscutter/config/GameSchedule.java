package emu.grasscutter.config;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalUnit;

public final class GameSchedule {

    public static final ZoneId RESET_ZONE = ZoneId.of(Configuration.GAME_INFO.timeZone);
    public static final LocalTime DAILY_RESET_TIME = LocalTime.parse(Configuration.GAME_INFO.timeDailyReset);

    // Derived — always in sync with DAILY_RESET_TIME
    public static final String CRON_AT_DAILY_RESET = String.format(
        "0 %d %d * * ?",
        DAILY_RESET_TIME.getMinute(),
        DAILY_RESET_TIME.getHour()
    );

    private GameSchedule() {}

    public static Instant currentDayStart() {
        return calculateNextDailyReset().minusDays(1).toInstant();
    }

    public static Instant nextDailyReset() {
        return calculateNextDailyReset().toInstant();
    }

    public static Instant nextDailyResetFromNow(int days) {
        return calculateNextDailyReset().plusDays(days - 1).toInstant();
    }

    public static Instant nextWeeklyReset() {
        return calculateNextWeeklyReset().toInstant();
    }

    public static long difference(Instant a, Instant b, TemporalUnit unit) {
        if (unit.isDateBased()) {
            // Date-based units (DAYS, WEEKS, MONTHS, YEARS): compare calendar dates in reset timezone
            return unit.between(
                a.atZone(RESET_ZONE).toLocalDate(),
                b.atZone(RESET_ZONE).toLocalDate()
            );
        } else {
            // HOURS and others: pure duration math
            return unit.between(a, b);
        }
    }

    private static ZonedDateTime calculateNextDailyReset() {
        ZonedDateTime now = ZonedDateTime.now(RESET_ZONE);
        ZonedDateTime candidate = now.toLocalDate()
            .atTime(DAILY_RESET_TIME)
            .atZone(RESET_ZONE);
        if (!now.isBefore(candidate)) candidate = candidate.plusDays(1);
        return candidate;
    }

    private static ZonedDateTime calculateNextWeeklyReset() {
        ZonedDateTime now = ZonedDateTime.now(RESET_ZONE);
        return now.toLocalDate()
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            .atTime(DAILY_RESET_TIME)
            .atZone(RESET_ZONE);
    }
}
