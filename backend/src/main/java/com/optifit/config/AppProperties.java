package com.optifit.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Builder;

@Builder(toBuilder = true)
@ConfigurationProperties("optifit")
public record AppProperties(String mode, String geminiKey, String model, String searchKey,
        List<String> allowedMerchants, int sessionLimit, int ipLimit, int dailyLimit, int ttlSeconds,
        int timeoutSeconds) {

    @Override
    public String toString() {
        return "AppProperties[mode=" + mode + ", model=" + model + ", credentials=REDACTED]";
    }

    public boolean demo() {
        return "demo".equals(mode);
    }

    public AppProperties {
        if (sessionLimit <= 0 || ipLimit <= 0 || dailyLimit <= 0 || ttlSeconds <= 0 || timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Analysis limits and timeouts must be positive");
        }
        allowedMerchants = allowedMerchants == null ? List.of() : List.copyOf(allowedMerchants);

        if (!"demo".equals(mode) && !"live".equals(mode)) {
            throw new IllegalArgumentException("OPTIFIT_MODE must be demo or live");
        }
        if ("live".equals(mode)
                && (geminiKey == null || geminiKey.isBlank() || searchKey == null || searchKey.isBlank())) {
            throw new IllegalArgumentException("Live mode requires GEMINI_API_KEY and TAVILY_API_KEY");
        }
    }
}
