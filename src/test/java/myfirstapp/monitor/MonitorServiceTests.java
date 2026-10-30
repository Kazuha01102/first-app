package myfirstapp.monitor;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class MonitorServiceTests {
    @TempDir Path proc;
    @Test void historyIsBoundedAndJvmStillWorksWithoutProcfs() {
        var service = new MonitorService(new LinuxMetricsReader(proc), new HttpMetrics());
        service.initialize();
        assertThat(service.snapshot().cpu().usagePercent()).isNull();
        assertThat(service.snapshot().memory().usedBytes()).isNull();
        assertThat(service.snapshot().network().receivedBytes()).isNull();
        assertThat(service.snapshot().network().receiveBytesPerSecond()).isNull();
        assertThat(service.snapshot().jvm().threadCount()).isPositive();
        for (int i = 0; i < 905; i++) service.sample();
        assertThat(service.history(900).samples()).hasSize(MonitorService.HISTORY_CAPACITY);
    }
    @Test void counterResetsAndInterfaceChangesDoNotCreateNegativeRates() throws Exception {
        Files.createDirectory(proc.resolve("net"));
        Path net = proc.resolve("net/dev");
        Files.writeString(net, "eth0: 1000 0 0 0 0 0 0 0 500 0 0 0 0 0 0 0");
        var service = new MonitorService(new LinuxMetricsReader(proc), new HttpMetrics());
        service.initialize();
        Files.writeString(net, "eth0: 10 0 0 0 0 0 0 0 5 0 0 0 0 0 0 0");
        service.sample();
        assertThat(service.snapshot().network().receiveBytesPerSecond()).isNull();
        Files.writeString(net, "eth1: 1000 0 0 0 0 0 0 0 500 0 0 0 0 0 0 0");
        service.sample();
        assertThat(service.snapshot().network().sendBytesPerSecond()).isNull();
        Files.writeString(net, "eth1: 2000 0 0 0 0 0 0 0 800 0 0 0 0 0 0 0");
        service.sample();
        assertThat(service.snapshot().network().receiveBytesPerSecond()).isPositive();
    }
    @Test void httpCountsErrorsAndUsesOnlyApiRequestsForResponseTime() {
        var metrics = new HttpMetrics();
        assertThat(metrics.snapshot().averageApiMs()).isNull();
        metrics.record(200, 2_000_000, true);
        metrics.record(404, 4_000_000, true);
        metrics.record(503, 99_000_000, false);
        var totals = metrics.snapshot();
        assertThat(totals.requests()).isEqualTo(3);
        assertThat(totals.errors()).isEqualTo(2);
        assertThat(totals.clientErrors()).isEqualTo(1);
        assertThat(totals.serverErrors()).isEqualTo(1);
        assertThat(totals.averageApiMs()).isEqualTo(3);
        assertThat(totals.lastApiMs()).isEqualTo(4);
    }
}
