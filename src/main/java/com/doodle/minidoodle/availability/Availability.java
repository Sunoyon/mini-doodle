package com.doodle.minidoodle.availability;

import com.doodle.minidoodle.dtos.SlotResponse;

import java.time.Instant;
import java.util.*;

public final class Availability {
    private Availability() {
    }

    public enum State {
        FREE,
        BUSY,
        UNDECLARED
    }

    public record Interval(Instant start, Instant end, State status) {
    }

    public record Summary(Instant from, Instant to, List<Interval> intervals) {
    }

    /**
     * Builds an availability summary for the half-open window {@code [from, to)}.
     * Clips slots to the window, fills gaps with {@link State#UNDECLARED}, and
     * merges adjacent intervals with the same state.
     *
     * @param from  inclusive start of the previously validated query window
     * @param to    exclusive end of the query window; must be after {@code from}
     * @param slots non-overlapping slots for one calendar, in any order;
     *              slots outside the window are ignored
     * @return a summary covering the entire window with chronologically ordered
     * intervals and an unmodifiable interval list
     */
    public static Summary aggregate(Instant from, Instant to, List<SlotResponse> slots) {
        var result = new ArrayList<Interval>();
        var cursor = from;
        for (var slot :
                slots.stream().sorted(Comparator.comparing(SlotResponse::start)).toList()) {
            var start = slot.start().isBefore(from) ? from : slot.start();
            var end = slot.end().isAfter(to) ? to : slot.end();
            if (!start.isBefore(end)) continue;
            if (cursor.isBefore(start))
                append(result, new Interval(cursor, start, State.UNDECLARED));
            append(result, new Interval(start, end, State.valueOf(slot.status().name())));
            cursor = end;
        }
        if (cursor.isBefore(to)) append(result, new Interval(cursor, to, State.UNDECLARED));
        return new Summary(from, to, List.copyOf(result));
    }

    private static void append(List<Interval> result, Interval next) {
        if (!result.isEmpty()) {
            var previous = result.getLast();
            if (previous.status() == next.status() && previous.end().equals(next.start())) {
                result.set(
                        result.size() - 1,
                        new Interval(previous.start(), next.end(), next.status()));
                return;
            }
        }
        result.add(next);
    }
}
