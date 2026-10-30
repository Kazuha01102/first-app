package myfirstapp;

import java.time.Instant;
import java.util.List;
import myfirstapp.monitor.MonitorModels.*;
import myfirstapp.monitor.MonitorService;
import myfirstapp.monitor.RecentLogs;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MonitorController {
    private final MonitorService monitor;
    public MonitorController(MonitorService monitor) { this.monitor = monitor; }

    // Keep the original os/cpuCores keys, and add a complete consistent snapshot.
    @GetMapping("/api/monitor")
    public Snapshot snapshot() { return monitor.snapshot(); }
    @GetMapping("/api/monitor/overview")
    public Overview overview() { return monitor.snapshot().overview(); }
    @GetMapping("/api/monitor/cpu")
    public Cpu cpu() { return monitor.snapshot().cpu(); }
    @GetMapping("/api/monitor/memory")
    public Memory memory() { return monitor.snapshot().memory(); }
    @GetMapping("/api/monitor/jvm")
    public Jvm jvm() { return monitor.snapshot().jvm(); }
    @GetMapping("/api/monitor/network")
    public Network network() { return monitor.snapshot().network(); }
    @GetMapping("/api/monitor/health")
    public Health health() { return new Health("UP", Instant.now(), "myfirstapp"); }
    @GetMapping("/api/monitor/history")
    public History history(@RequestParam(defaultValue = "300") int seconds) {
        if (seconds < 1 || seconds > 900) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seconds must be between 1 and 900");
        return monitor.history(seconds);
    }
    @GetMapping("/api/monitor/logs")
    public List<RecentLogs.Entry> logs(@RequestParam(defaultValue = "ALL") String level,
                                      @RequestParam(defaultValue = "100") int limit) {
        if (!List.of("ALL", "INFO", "WARN", "ERROR").contains(level) || limit < 1 || limit > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid log level or limit (1–200)");
        return RecentLogs.entries(level, limit);
    }
}
