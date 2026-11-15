package ru.viise.jemuxg.core.clock;

import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import ru.viise.jemuxg.api.machine.Scheduler;

/**
 * Детерминированный планировщик событий.
 */
public final class EventScheduler implements Scheduler {

    private record Event(long cycle, long sequence, Runnable action) {
    }

    private static final Comparator<Event> ORDER =
            Comparator.comparingLong(Event::cycle).thenComparingLong(Event::sequence);

    private final PriorityQueue<Event> queue = new PriorityQueue<>(ORDER);
    private long now;
    private long sequence;

    @Override
    public long now() {
        return now;
    }

    @Override
    public void schedule(long cycle, Runnable event) {
        if (cycle < now) {
            throw new IllegalArgumentException("cannot schedule in the past: " + cycle + " < " + now);
        }
        Objects.requireNonNull(event, "event");
        queue.add(new Event(cycle, sequence++, event));
    }

    @Override
    public void advanceTo(long cycle) {
        if (cycle < now) {
            throw new IllegalArgumentException("cannot travel back in time: " + cycle + " < " + now);
        }
        while (!queue.isEmpty() && queue.peek().cycle() <= cycle) {
            Event event = queue.poll();
            now = Math.max(now, event.cycle());
            event.action().run();
        }
        now = cycle;
    }
}
