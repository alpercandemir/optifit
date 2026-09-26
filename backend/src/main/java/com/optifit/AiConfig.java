package com.optifit;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.optifit.Models.FaceProfile;
import com.optifit.Models.Shape;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
class AiConfig {
    @Bean
    @ConditionalOnProperty(name = "optifit.mode", havingValue = "live", matchIfMissing = true)
    GoogleGenAiChatModel geminiChatModel(AppProperties properties) {
        var client = Client.builder().apiKey(properties.geminiKey()).vertexAI(false).httpOptions(HttpOptions.builder()
                .timeout(22_000).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build();
        var options = GoogleGenAiChatOptions.builder().model(properties.model()).maxOutputTokens(700).build();
        // Bound retries at one layer; the SDK itself makes only one attempt.
        return GoogleGenAiChatModel.builder().genAiClient(client).options(options).retryTemplate(new RetryTemplate(
                RetryPolicy.builder().maxRetries(1).includes(com.google.genai.errors.ServerException.class).build()))
                .build();
    }

    @Bean
    FaceAnalyzer faceAnalyzer(AppProperties properties, MeterRegistry metrics,
            ObjectProvider<GoogleGenAiChatModel> model) {
        if (properties.demo()) {
            return jpeg -> new FaceProfile(true, 1, "Demo: no photo analysis was performed.",
                    List.of(Shape.RECTANGULAR, Shape.ROUND, Shape.AVIATOR));
        }
        return new GeminiFaceAnalyzer(ChatClient.create(model.getObject()), metrics, properties.model());
    }
}
