package myfirstapp.monitor;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/** Completed requests in this JVM, including polling. No URLs or request bodies are retained. */
@Component
public class HttpMetrics {
    private final LongAdder requests = new LongAdder(), clientErrors = new LongAdder(), serverErrors = new LongAdder();
    private final LongAdder apiRequests = new LongAdder(), apiNanos = new LongAdder();
    private final AtomicLong lastApiNanos = new AtomicLong(-1);
    public synchronized void record(int status, long elapsedNanos, boolean api) {
        requests.increment();
        if (status >= 400 && status < 500) clientErrors.increment();
        if (status >= 500) serverErrors.increment();
        if (api) {
            long nanos = Math.max(0, elapsedNanos);
            apiNanos.add(nanos);
            lastApiNanos.set(nanos);
            apiRequests.increment();
        }
    }
    public record Totals(long requests, long errors, long clientErrors, long serverErrors,
                         long apiRequests, Double averageApiMs, Double lastApiMs) {}
    public synchronized Totals snapshot() {
        long count = apiRequests.sum(), last = lastApiNanos.get();
        long clients = clientErrors.sum(), servers = serverErrors.sum();
        return new Totals(requests.sum(), clients + servers, clients, servers, count,
                count == 0 ? null : apiNanos.sum() / 1_000_000.0 / count,
                last < 0 ? null : last / 1_000_000.0);
    }
}
