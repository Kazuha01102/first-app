package myfirstapp;

import myfirstapp.monitor.HttpMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MonitorApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired HttpMetrics metrics;

    @Test void allMonitoringEndpointsReturnJsonAndDoNotCacheSystemDetails() throws Exception {
        for (String endpoint : new String[]{"", "/overview", "/cpu", "/memory", "/jvm", "/network", "/health", "/history", "/logs"}) {
            mvc.perform(get("/api/monitor" + endpoint))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
        mvc.perform(get("/api/monitor"))
                .andExpect(jsonPath("$.os", not(emptyString())))
                .andExpect(jsonPath("$.cpuCores", greaterThan(0)))
                .andExpect(jsonPath("$.jvm.threadCount", greaterThan(0)))
                .andExpect(jsonPath("$.overview.status").value("UP"));
    }
    @Test void boundedInputsAndUnsupportedMethodsAreRejectedAndCounted() throws Exception {
        long errors = metrics.snapshot().clientErrors();
        mvc.perform(get("/api/monitor/history?seconds=901")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/monitor/history?seconds=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/monitor/logs?limit=1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/monitor/logs?level=DEBUG")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/monitor")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/monitor/logs?path=/etc/passwd")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("root:x:"))));
        assertThat(metrics.snapshot().clientErrors()).isGreaterThanOrEqualTo(errors + 5);
    }
    @Test void calculatorAndExistingRoutesStillWork() throws Exception {
        String[][] cases = {{"add", "15.0"}, {"subtract", "5.0"}, {"multiply", "50.0"}, {"divide", "2.0"}};
        for (var item : cases) {
            mvc.perform(get("/calculate").param("a", "10").param("b", "5").param("op", item[0]))
                    .andExpect(status().isOk()).andExpect(content().string("計算結果:" + item[1]));
        }
        mvc.perform(get("/calculate?a=1&b=0&op=divide")).andExpect(content().string("0では割れません"));
        mvc.perform(get("/add?a=2&b=3")).andExpect(content().string("計算結果:5"));
        mvc.perform(get("/multiply?a=2&b=3")).andExpect(content().string("計算結果:6"));
        mvc.perform(get("/hello?name=Taro")).andExpect(status().isOk());
        mvc.perform(get("/hello-world")).andExpect(status().isOk());
        mvc.perform(get("/test")).andExpect(status().isOk());
    }
    @Test void dashboardAndCalculatorHaveNoExternalDependenciesOrInlineScripts() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(containsString("Monitoring dashboard")));
        mvc.perform(get("/calculator.html")).andExpect(status().isOk())
                .andExpect(result -> assertThat(new String(result.getResponse().getContentAsByteArray(),
                        java.nio.charset.StandardCharsets.UTF_8)).contains("Java計算機").doesNotContain("<script>", "https://"));
        mvc.perform(get("/js/dashboard.js")).andExpect(status().isOk());
        mvc.perform(get("/css/dashboard.css")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/heapdump")).andExpect(status().isNotFound());
    }
}
