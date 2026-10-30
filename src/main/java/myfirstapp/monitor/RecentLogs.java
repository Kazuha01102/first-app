package myfirstapp.monitor;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.regex.Pattern;

/** Bounded application-only log view; no journal/file access or stack traces. */
public final class RecentLogs {
    public static final int CAPACITY = 300;
    private static final ArrayDeque<Entry> BUFFER = new ArrayDeque<>();
    private static long sequence;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|secret|token|authorization|api[_-]?key)([\\s\"']*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern URL_CREDENTIAL = Pattern.compile("(://)[^/@\\s]+:[^/@\\s]+@");
    private RecentLogs() {}
    public record Entry(long id, Instant timestamp, String level, String logger, String message) {}
    public static String redact(String message) {
        String text = message == null ? "" : message.replaceAll("[\\r\\n\\t]", " ");
        text = BEARER.matcher(text).replaceAll("Bearer [REDACTED]");
        text = SECRET.matcher(text).replaceAll("$1$2[REDACTED]");
        text = URL_CREDENTIAL.matcher(text).replaceAll("$1[REDACTED]@");
        return text.length() > 1000 ? text.substring(0, 1000) + "…" : text;
    }
    public static synchronized void append(Instant timestamp, String level, String logger, String message) {
        BUFFER.addLast(new Entry(++sequence, timestamp, level, logger, redact(message)));
        while (BUFFER.size() > CAPACITY) BUFFER.removeFirst();
    }
    public static synchronized List<Entry> entries(String level, int limit) {
        var filtered = BUFFER.stream().filter(e -> level.equals("ALL") || e.level().equals(level)).toList();
        return filtered.subList(Math.max(0, filtered.size() - limit), filtered.size());
    }
}
