package engine;

import java.util.concurrent.atomic.AtomicInteger;

public class SequenceGenerator {

    private final AtomicInteger counter;

    public SequenceGenerator(int initialValue) {
        this.counter = new AtomicInteger(initialValue);
    }

    public int next() {
        return counter.incrementAndGet();
    }
}
