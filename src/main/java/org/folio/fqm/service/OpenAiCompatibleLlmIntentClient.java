package org.folio.fqm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleLlmIntentClient implements LlmIntentClient {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String model;
  private final String apiKey;
  private final String chatCompletionsPath;
  private final double temperature;

  public OpenAiCompatibleLlmIntentClient(
    ObjectMapper objectMapper,
    @Value("${mod-fqm-manager.query-suggestions.llm.base-url:}") String baseUrl,
    @Value("${mod-fqm-manager.query-suggestions.llm.api-key:}") String apiKey,
    @Value("${mod-fqm-manager.query-suggestions.llm.model:}") String model,
    @Value("${mod-fqm-manager.query-suggestions.llm.chat-completions-path:/chat/completions}") String chatCompletionsPath,
    @Value("${mod-fqm-manager.query-suggestions.llm.temperature:0.0}") double temperature
  ) {
    this(
      RestClient.builder().baseUrl(baseUrl == null ? "" : baseUrl).build(),
      objectMapper,
      model,
      apiKey,
      chatCompletionsPath,
      temperature
    );
  }

  OpenAiCompatibleLlmIntentClient(
    RestClient restClient,
    ObjectMapper objectMapper,
    String model,
    String apiKey,
    String chatCompletionsPath,
    double temperature
  ) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.model = model;
    this.apiKey = apiKey;
    this.chatCompletionsPath = chatCompletionsPath;
    this.temperature = temperature;
  }

  @Override
  public String complete(String systemPrompt, String userPrompt) {
    JsonNode response = restClient.post()
      .uri(chatCompletionsPath)
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
      .contentType(MediaType.APPLICATION_JSON)
      .accept(MediaType.APPLICATION_JSON)
      .body(Map.of(
        "model", model,
        "temperature", temperature,
        "messages", List.of(
          Map.of("role", "system", "content", systemPrompt),
          Map.of("role", "user", "content", userPrompt)
        )
      ))
      .retrieve()
      .body(JsonNode.class);

    if (response == null) {
      throw new IllegalStateException("LLM returned an empty response body.");
    }

    JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
    if (contentNode.isMissingNode() || contentNode.isNull() || contentNode.asText().isBlank()) {
      throw new IllegalStateException("LLM response did not contain message content.");
    }

    return contentNode.asText();
  }
}
