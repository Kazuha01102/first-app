package myfirstapp.monitor;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class LinuxMetricsReaderTests {
    @TempDir Path proc;
    private void file(String name, String content) throws Exception {
        Path path = proc.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
    @Test void cpuExcludesGuestAndUsesIdlePlusIoWait() throws Exception {
        file("stat", "cpu  100 20 30 400 50 2 3 4 70 10\ncpu0 1 0 0 0\ncpu1 1 0 0 0\n");
        var reader = new LinuxMetricsReader(proc);
        var cpu = reader.cpu();
        assertThat(cpu.total()).isEqualTo(609);
        assertThat(cpu.idle()).isEqualTo(450);
        assertThat(cpu.cores()).isEqualTo(2);
        assertThat(LinuxMetricsReader.cpuPercent(cpu, new LinuxMetricsReader.CpuTicks(709, 510, 2))).isEqualTo(40.0);
    }
    @Test void firstSampleResetAndInvalidDeltasAreUnavailable() {
        var previous = new LinuxMetricsReader.CpuTicks(100, 70, 2);
        assertThat(LinuxMetricsReader.cpuPercent(null, previous)).isNull();
        assertThat(LinuxMetricsReader.cpuPercent(previous, previous)).isNull();
        assertThat(LinuxMetricsReader.cpuPercent(previous, new LinuxMetricsReader.CpuTicks(90, 60, 2))).isNull();
        assertThat(LinuxMetricsReader.cpuPercent(previous, new LinuxMetricsReader.CpuTicks(110, 90, 2))).isNull();
        assertThat(LinuxMetricsReader.cpuPercent(previous, new LinuxMetricsReader.CpuTicks(110, 75, 4))).isNull();
    }
    @Test void memoryUsesAvailableRatherThanFreeAndConvertsKibibytes() throws Exception {
        file("meminfo", "MemTotal: 1000 kB\nMemFree: 100 kB\nMemAvailable: 650 kB\nSwapTotal: 200 kB\nSwapFree: 150 kB\n");
        var memory = new LinuxMetricsReader(proc).memory();
        assertThat(memory.totalBytes()).isEqualTo(1024000L);
        assertThat(memory.usedBytes()).isEqualTo(350 * 1024L);
        assertThat(memory.availableBytes()).isEqualTo(650 * 1024L);
        assertThat(memory.usagePercent()).isEqualTo(35.0);
        assertThat(memory.swapUsedBytes()).isEqualTo(50 * 1024L);
    }
    @Test void missingAndMalformedMetricsNeverBecomeFakeZeroes() throws Exception {
        var reader = new LinuxMetricsReader(proc);
        assertThat(reader.cpu()).isNull();
        assertThat(reader.network()).isNull();
        assertThat(reader.uptimeSeconds()).isNull();
        assertThat(reader.memory().usagePercent()).isNull();
        file("meminfo", "MemTotal: 1000 kB\nMemAvailable: 1500 kB\nSwapTotal: -1 kB\n");
        assertThat(reader.memory().availableBytes()).isNull();
        assertThat(reader.memory().usagePercent()).isNull();
        assertThat(reader.memory().swapTotalBytes()).isNull();
        file("uptime", "NaN 10");
        assertThat(reader.uptimeSeconds()).isNull();
    }
    @Test void networkExcludesLoopbackAndUsesCorrectRxTxColumns() throws Exception {
        file("net/dev", "Inter-| Receive | Transmit\nlo: 900 0 0 0 0 0 0 0 800 0 0 0 0 0 0 0\n"
                + "eth0: 120 0 0 0 0 0 0 0 45 0 0 0 0 0 0 0\n"
                + "eth1: 80 0 0 0 0 0 0 0 55 0 0 0 0 0 0 0\n");
        var network = new LinuxMetricsReader(proc).network();
        assertThat(network.received()).isEqualTo(200L);
        assertThat(network.sent()).isEqualTo(100L);
        assertThat(network.interfaces()).containsExactly("eth0", "eth1");
    }
}
