package com.optifit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.optifit.Models.Shape;
import com.sun.net.httpserver.HttpServer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import tools.jackson.databind.json.JsonMapper;

class GeminiIntegrationTest {
    @Test
    void sendsInlineJpegAndSchemaToGeminiAndRecordsUsage() throws Exception {
        var json = JsonMapper.builder().build();
        var body = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        var key = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String profile = """
                    {"usable":true,"faceCount":1,"guidance":"Visual style suggestion only.","preferredShapes":["ROUND"],
                     "suggestedModels":[{"brand":"Ray-Ban","modelCode":"RB 3447","category":"SUNGLASSES"}]}
                    """;
            byte[] response = json
                    .writeValueAsBytes(java.util.Map.of("modelVersion", "gemini-2.5-flash-lite", "candidates",
                            List.of(java.util.Map.of("content",
                                    java.util.Map.of("role", "model", "parts",
                                            List.of(java.util.Map.of("text", profile))),
                                    "finishReason", "STOP")),
                            "usageMetadata", java.util.Map.of("promptTokenCount", 24, "candidatesTokenCount", 18,
                                    "totalTokenCount", 42)));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
        var metrics = new SimpleMeterRegistry();
        try (var client = Client.builder().apiKey("test-gemini-key").vertexAI(false).httpOptions(HttpOptions.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).timeout(2000).build()).build()) {
            var model = GoogleGenAiChatModel.builder().genAiClient(client).options(
                    GoogleGenAiChatOptions.builder().model("gemini-2.5-flash-lite").maxOutputTokens(700).build())
                    .build();
            byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
            var analyzer = new GeminiFaceAnalyzer(ChatClient.create(model), metrics, "gemini-2.5-flash-lite");
            var profile = analyzer.analyze(jpeg,
                    new com.optifit.Models.Preferences(com.optifit.Models.Category.SUNGLASSES, null, "ANY", "ANY"));
            assertThat(profile.preferredShapes()).containsExactly(Shape.ROUND);
            assertThat(profile.suggestedModels()).extracting(com.optifit.Models.ModelSuggestion::modelCode)
                    .containsExactly("RB3447");
            assertThat(path.get()).endsWith("/models/gemini-2.5-flash-lite:generateContent");
            assertThat(key.get()).isEqualTo("test-gemini-key");
            var payload = json.readTree(body.get());
            var parts = payload.path("contents").get(0).path("parts");
            assertThat(parts.get(1).path("inlineData").path("mimeType").asString()).isEqualTo("image/jpeg");
            assertThat(parts.get(1).path("inlineData").path("data").asString())
                    .isEqualTo(Base64.getEncoder().encodeToString(jpeg));
            var config = payload.path("generationConfig");
            assertThat(config.path("responseMimeType").asString()).isEqualTo("application/json");
            assertThat(config.toString()).contains("preferredShapes", "faceCount", "suggestedModels", "modelCode");
            assertThat(config.path("maxOutputTokens").asInt()).isEqualTo(700);
            assertThat(parts.get(0).path("text").asString()).contains("SUNGLASSES", "Ray-Ban", "Osse", "Inesta",
                    "Mustang");
            assertThat(body.get()).doesNotContain("test-gemini-key");
            assertThat(metrics.get("optifit.ai.tokens").tag("kind", "input").counter().count()).isEqualTo(24);
            assertThat(metrics.get("optifit.ai.tokens").tag("kind", "output").counter().count()).isEqualTo(18);
        } finally {
            server.stop(0);
            metrics.close();
        }
    }

    @Test
    void demoModeDoesNotConstructGeminiClientOrRequireCredentials() {
        new ApplicationContextRunner().withUserConfiguration(AiConfig.class).withPropertyValues("optifit.mode=demo")
                .withBean(AppProperties.class, () -> new AppProperties("demo", "", "gemini-2.5-flash-lite", "",
                        List.of(), 5, 20, 100, 3600, 60))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new).run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(GoogleGenAiChatModel.class);
                    assertThat(context.getBean(FaceAnalyzer.class).analyze(new byte[]{1}).guidance())
                            .isEqualTo("Demo: no photo analysis was performed.");
                });
    }

    @Test
    void liveModeConstructsGeminiAnalyzerWithConfiguredModel() {
        new ApplicationContextRunner().withUserConfiguration(AiConfig.class).withPropertyValues("optifit.mode=live")
                .withBean(AppProperties.class,
                        () -> new AppProperties("live", "test-gemini-key", "gemini-2.5-flash-lite", "test-search-key",
                                List.of(), 5, 20, 100, 3600, 60))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(GoogleGenAiChatModel.class);
                    assertThat(context.getBean(FaceAnalyzer.class)).isInstanceOf(GeminiFaceAnalyzer.class);
                    assertThat(context.getBean(GoogleGenAiChatModel.class).getDefaultOptions().getModel())
                            .isEqualTo("gemini-2.5-flash-lite");
                });
    }

    @Test
    void liveModeRequiresGeminiKeyEvenWithSearchCredentials() {
        assertThatThrownBy(() -> new AppProperties("live", "", "gemini-2.5-flash-lite", "search-key", List.of(), 5, 20,
                100, 3600, 60)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Live mode requires GEMINI_API_KEY and TAVILY_API_KEY");
    }
}
