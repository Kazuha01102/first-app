package myfirstapp.monitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Reads fixed procfs paths only. No commands or process environment access. */
@Component
public class LinuxMetricsReader {
    private final Path proc;
    public LinuxMetricsReader() { this(Path.of("/proc")); }
    LinuxMetricsReader(Path proc) { this.proc = proc; }
    public record CpuTicks(long total, long idle, int cores) {}
    public record NetworkCounters(long received, long sent, List<String> interfaces) {}

    private List<String> lines(String file) {
        try { return Files.readAllLines(proc.resolve(file)); }
        catch (IOException | SecurityException ex) { return List.of(); }
    }

    public CpuTicks cpu() {
        List<String> lines = lines("stat");
        try {
            String aggregate = lines.stream().filter(line -> line.startsWith("cpu ")).findFirst().orElseThrow();
            String[] parts = aggregate.trim().split("\\s+");
            if (parts.length < 5) return null;
            long total = 0;
            // guest/guest_nice are already included in user/nice.
            for (int i = 1; i <= Math.min(8, parts.length - 1); i++) {
                long value = Long.parseLong(parts[i]);
                if (value < 0) return null;
                total = Math.addExact(total, value);
            }
            long idle = Long.parseLong(parts[4]);
            if (parts.length > 5) idle = Math.addExact(idle, Long.parseLong(parts[5]));
            int cores = (int) lines.stream().filter(line -> line.matches("cpu\\d+\\s+.*")).count();
            return new CpuTicks(total, idle, cores);
        } catch (RuntimeException ex) { return null; }
    }

    static Double cpuPercent(CpuTicks previous, CpuTicks current) {
        if (previous == null || current == null || previous.cores() != current.cores()) return null;
        long total = current.total() - previous.total();
        long idle = current.idle() - previous.idle();
        if (total <= 0 || idle < 0 || idle > total) return null;
        return (total - idle) * 100.0 / total;
    }

    public MonitorModels.Memory memory() {
        Map<String, Long> values = new HashMap<>();
        for (String line : lines("meminfo")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 3 || !parts[2].equals("kB")) continue;
            try {
                long kib = Long.parseLong(parts[1]);
                if (kib >= 0) values.put(parts[0].replace(":", ""), Math.multiplyExact(kib, 1024));
            } catch (NumberFormatException | ArithmeticException ignored) { /* unavailable field */ }
        }
        Long total = values.get("MemTotal"), available = values.get("MemAvailable");
        if (total != null && total <= 0) total = null;
        if (available != null && total != null && available > total) available = null;
        Long used = total != null && available != null ? total - available : null;
        Double percent = used != null ? used * 100.0 / total : null;
        Long swapTotal = values.get("SwapTotal"), swapFree = values.get("SwapFree");
        Long swapUsed = swapTotal != null && swapFree != null && swapFree <= swapTotal ? swapTotal - swapFree : null;
        return new MonitorModels.Memory(total, used, available, percent, swapTotal, swapUsed, "/proc/meminfo (Linux)");
    }

    public Long uptimeSeconds() {
        try {
            double value = Double.parseDouble(lines("uptime").getFirst().trim().split("\\s+")[0]);
            return Double.isFinite(value) && value >= 0 ? (long) value : null;
        } catch (RuntimeException ex) { return null; }
    }

    public NetworkCounters network() {
        long received = 0, sent = 0;
        List<String> interfaces = new ArrayList<>();
        for (String line : lines("net/dev")) {
            if (!line.contains(":")) continue;
            String[] sides = line.split(":", 2);
            String name = sides[0].trim();
            if (name.equals("lo")) continue;
            String[] fields = sides[1].trim().split("\\s+");
            try {
                if (fields.length < 16) return null;
                long rx = Long.parseLong(fields[0]), tx = Long.parseLong(fields[8]);
                if (rx < 0 || tx < 0) return null;
                received = Math.addExact(received, rx);
                sent = Math.addExact(sent, tx);
                interfaces.add(name);
            } catch (RuntimeException ex) { return null; }
        }
        return interfaces.isEmpty() ? null : new NetworkCounters(received, sent, interfaces.stream().sorted().toList());
    }

    public boolean isWsl() {
        return System.getProperty("os.version", "").toLowerCase(java.util.Locale.ROOT).contains("microsoft")
                || lines("sys/kernel/osrelease").stream().anyMatch(s -> s.toLowerCase(java.util.Locale.ROOT).contains("microsoft"));
    }

    public String osName() {
        if (Files.isDirectory(proc)) {
            try {
                return Files.readAllLines(Path.of("/etc/os-release")).stream()
                        .filter(s -> s.startsWith("PRETTY_NAME="))
                        .map(s -> s.substring(12).replace("\"", ""))
                        .findFirst().orElse(System.getProperty("os.name"));
            } catch (IOException | SecurityException ignored) { /* MXBean fallback */ }
        }
        return java.lang.management.ManagementFactory.getOperatingSystemMXBean().getName();
    }
}
