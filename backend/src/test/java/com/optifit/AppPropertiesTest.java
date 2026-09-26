package com.optifit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.optifit.config.AppProperties;

class AppPropertiesTest {

    @Test
    void liveModeRequiresTavilyCredentials() {
        assertThatThrownBy(() -> new AppProperties("live", "gemini-key", "model", "", List.of(), 5, 20, 100, 3600, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Live mode requires GEMINI_API_KEY and TAVILY_API_KEY");
    }

    @Test
    void configurationDiagnosticsNeverIncludeCredentials() {
        var properties = new AppProperties("live", "private-gemini-key", "model", "private-search-key",
                List.of("shop.example"), 5, 20, 100, 3600, 60);
        assertThat(properties.toString()).contains("REDACTED").doesNotContain("private-gemini-key",
                "private-search-key");
    }

    @Test
    void rejectsInvalidModeAndNonPositiveLimits() {
        assertThatThrownBy(() -> new AppProperties(null, "", "model", "", List.of(), 5, 20, 100, 3600, 60))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("OPTIFIT_MODE must be demo or live");
        assertThatThrownBy(() -> new AppProperties("demo", "", "model", "", List.of(), 0, 20, 100, 3600, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Analysis limits and timeouts must be positive");
    }
}
