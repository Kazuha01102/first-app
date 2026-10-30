package myfirstapp.monitor;

import java.time.Instant;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/** Application and Spring Boot startup loggers at INFO or above only. */
public class DashboardLogAppender extends AppenderBase<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent event) {
        String logger = event.getLoggerName();
        if (event.getLevel().toInt() < ch.qos.logback.classic.Level.INFO_INT) return;
        if (!(logger.startsWith("myfirstapp.") || logger.startsWith("org.springframework.boot."))) return;
        String level = event.getLevel().toString();
        if (!java.util.Set.of("INFO", "WARN", "ERROR").contains(level)) return;
        RecentLogs.append(Instant.ofEpochMilli(event.getTimeStamp()), level, logger, event.getFormattedMessage());
    }
}
