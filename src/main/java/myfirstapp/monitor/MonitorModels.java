package myfirstapp.monitor;

import java.time.Instant;
import java.util.List;

/** Null means unavailable; zero is reserved for an actual measured zero. */
public final class MonitorModels {
    private MonitorModels() {}
    public record Snapshot(String os, int cpuCores, Instant collectedAt, Overview overview,
                           Cpu cpu, Memory memory, Jvm jvm, Network network) {}
    public record Overview(String status, String application, String osName, String kernelVersion,
                           String javaVersion, String environment, String systemScope, int cpuCores,
                           Long systemUptimeSeconds, long jvmUptimeSeconds, Instant collectedAt) {}
    public record Cpu(Double usagePercent, int cores, Double averagePercent, Double maxPercent,
                      int windowSeconds, int sampleCount, String source) {}
    public record Memory(Long totalBytes, Long usedBytes, Long availableBytes, Double usagePercent,
                         Long swapTotalBytes, Long swapUsedBytes, String source) {}
    public record Jvm(long heapUsedBytes, Long heapMaxBytes, long nonHeapUsedBytes,
                      int threadCount, long uptimeSeconds, int availableProcessors, String scope) {}
    public record Network(Long receivedBytes, Long sentBytes, Double receiveBytesPerSecond,
                          Double sendBytesPerSecond, List<String> interfaces, String scope,
                          long httpRequests, long httpErrors, long clientErrors, long serverErrors,
                          long apiRequests, Double averageApiResponseMs, Double lastApiResponseMs) {}
    public record Sample(Instant timestamp, Double cpuPercent, Double memoryPercent, long heapUsedBytes) {}
    public record History(int windowSeconds, int sampleIntervalSeconds, int capacity, List<Sample> samples) {}
    public record Health(String status, Instant checkedAt, String service) {}
}
