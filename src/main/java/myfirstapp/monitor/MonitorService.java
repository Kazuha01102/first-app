package myfirstapp.monitor;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import myfirstapp.monitor.MonitorModels.*;

/** One background sampler; every endpoint reads the same immutable snapshot. */
@Service
public class MonitorService {
    public static final int HISTORY_CAPACITY = 900;
    private final LinuxMetricsReader linux;
    private final HttpMetrics http;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private final String osName, environment, scope;
    private LinuxMetricsReader.CpuTicks previousCpu;
    private LinuxMetricsReader.NetworkCounters previousNetwork;
    private long previousNanos;
    private volatile Snapshot latest;

    public MonitorService(LinuxMetricsReader linux, HttpMetrics http) {
        this.linux = linux;
        this.http = http;
        osName = linux.osName();
        boolean wsl = linux.isWsl();
        environment = wsl ? "WSL2" : System.getProperty("os.name");
        scope = wsl ? "WSL2 Linux VM / current network namespace — not the Windows host"
                : "Linux kernel / current network namespace; system metrics require procfs";
    }
    @PostConstruct
    public void initialize() { sample(); }

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public synchronized void sample() {
        Instant now = Instant.now();
        long nanos = System.nanoTime();
        var ticks = linux.cpu();
        Double cpuPercent = LinuxMetricsReader.cpuPercent(previousCpu, ticks);
        previousCpu = ticks;
        int cores = ticks != null && ticks.cores() > 0 ? ticks.cores() : Runtime.getRuntime().availableProcessors();
        var memory = linux.memory();
        var memoryBean = ManagementFactory.getMemoryMXBean();
        var heap = memoryBean.getHeapMemoryUsage();
        long jvmUptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        var jvm = new Jvm(heap.getUsed(), heap.getMax() < 0 ? null : heap.getMax(),
                memoryBean.getNonHeapMemoryUsage().getUsed(), ManagementFactory.getThreadMXBean().getThreadCount(),
                jvmUptime, Runtime.getRuntime().availableProcessors(), "This Java process only");
        samples.addLast(new Sample(now, cpuPercent, memory.usagePercent(), heap.getUsed()));
        while (samples.size() > HISTORY_CAPACITY || samples.getFirst().timestamp().isBefore(now.minusSeconds(900))) {
            samples.removeFirst();
        }
        List<Double> cpuValues = samples.stream().map(Sample::cpuPercent).filter(java.util.Objects::nonNull).toList();
        var cpu = new Cpu(cpuPercent, cores,
                cpuValues.isEmpty() ? null : cpuValues.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                cpuValues.isEmpty() ? null : cpuValues.stream().mapToDouble(Double::doubleValue).max().orElseThrow(),
                900, cpuValues.size(), "/proc/stat delta (idle includes iowait; guest not double-counted)");
        var counters = linux.network();
        Double rxRate = null, txRate = null;
        if (counters != null && previousNetwork != null && counters.interfaces().equals(previousNetwork.interfaces())
                && counters.received() >= previousNetwork.received() && counters.sent() >= previousNetwork.sent()
                && nanos > previousNanos) {
            double seconds = (nanos - previousNanos) / 1_000_000_000.0;
            rxRate = (counters.received() - previousNetwork.received()) / seconds;
            txRate = (counters.sent() - previousNetwork.sent()) / seconds;
        }
        previousNetwork = counters;
        previousNanos = nanos;
        var requests = http.snapshot();
        var network = new Network(counters == null ? null : counters.received(), counters == null ? null : counters.sent(),
                rxRate, txRate, counters == null ? List.of() : counters.interfaces(),
                "Non-loopback Linux interfaces; totals since interface creation, HTTP since this JVM started",
                requests.requests(), requests.errors(), requests.clientErrors(), requests.serverErrors(),
                requests.apiRequests(), requests.averageApiMs(), requests.lastApiMs());
        var overview = new Overview("UP", "myfirstapp", osName, System.getProperty("os.version"),
                System.getProperty("java.version"), environment, scope, cores, linux.uptimeSeconds(), jvmUptime, now);
        latest = new Snapshot(osName, cores, now, overview, cpu, memory, jvm, network);
    }
    public Snapshot snapshot() { return latest; }
    public synchronized History history(int seconds) {
        Instant cutoff = Instant.now().minusSeconds(seconds);
        return new History(seconds, 1, HISTORY_CAPACITY,
                samples.stream().filter(s -> !s.timestamp().isBefore(cutoff)).toList());
    }
}
