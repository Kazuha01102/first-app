package myfirstapp.monitor;

import java.time.Instant;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RecentLogsTests {
    @Test void credentialsAndMultilineContentAreRedacted() {
        var safe = RecentLogs.redact("password=hunter2 token='secret with spaces' \"api_key\": \"abc def\" "
                + "Authorization: Bearer abc.def.ghi https://alice:pass@example.com/\nnext");
        assertThat(safe).doesNotContain("hunter2", "secret with spaces", "abc def", "abc.def.ghi", "alice", "\n");
        assertThat(safe).contains("[REDACTED]", "next");
    }
    @Test void ringAndMessageSizesAreBoundedAndFilteringWorks() {
        for (int i = 0; i < 350; i++) RecentLogs.append(Instant.now(), i % 2 == 0 ? "INFO" : "ERROR", "test", "x".repeat(1200));
        assertThat(RecentLogs.entries("ALL", 300)).hasSize(300);
        assertThat(RecentLogs.entries("ERROR", 5)).hasSize(5).allMatch(e -> e.level().equals("ERROR"));
        assertThat(RecentLogs.entries("ALL", 1).getFirst().message()).hasSize(1001);
    }
    @Test void appenderIgnoresUnrelatedLoggersAndDebugOutput() {
        var appender = new DashboardLogAppender();
        appender.start();
        long previous = RecentLogs.entries("ALL", 1).stream().mapToLong(RecentLogs.Entry::id).max().orElse(0);
        var ignored = new LoggingEvent();
        ignored.setLoggerName("third.party.credentials");
        ignored.setLevel(Level.ERROR);
        ignored.setMessage("not exposed");
        appender.doAppend(ignored);
        var event = new LoggingEvent();
        event.setLoggerName("myfirstapp.Example");
        event.setLevel(Level.WARN);
        event.setMessage("safe application warning");
        event.setTimeStamp(System.currentTimeMillis());
        appender.doAppend(event);
        var last = RecentLogs.entries("ALL", 1).getFirst();
        assertThat(last.id()).isEqualTo(previous + 1);
        assertThat(last.message()).isEqualTo("safe application warning");
        appender.stop();
    }
}
