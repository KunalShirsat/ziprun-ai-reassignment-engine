package com.ziprun.reassignment.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class LiteLlmGateway implements LLMGateway {

    private final RestClient restClient;
    private final String apiKey;
    private final String completionsPath;
    private final String model;
    private final String product;

    public LiteLlmGateway(
            RestClient.Builder restClientBuilder,
            @Value("${litellm.base-url}") String baseUrl,
            @Value("${litellm.completions-path}") String completionsPath,
            @Value("${litellm.model}") String model,
            @Value("${litellm.product}") String product,
            @Value("${litellm.api-key:}") String apiKey,
            @Value("${litellm.connect-timeout:5s}") Duration connectTimeout,
            @Value("${litellm.read-timeout:20s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
        this.completionsPath = completionsPath;
        this.model = model;
        this.product = product;
    }

    @Override
    public String generate(String prompt) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("LiteLLM API key is not configured");
        }

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri(completionsPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header("product", product)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChatCompletionRequest(model, List.of(new ChatMessage("user", prompt))))
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null
                    || response.choices().isEmpty()
                    || response.choices().get(0).message() == null
                    || response.choices().get(0).message().content() == null) {
                throw new IllegalStateException("LiteLLM response did not contain message content");
            }
            return response.choices().get(0).message().content();
        } catch (RestClientException exception) {
            throw new IllegalStateException("LiteLLM request failed");
        }
    }

    private record ChatCompletionRequest(String model, List<ChatMessage> messages) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record ChatCompletionResponse(List<Choice> choices) {
    }

    private record Choice(ChatMessage message) {
    }
}
