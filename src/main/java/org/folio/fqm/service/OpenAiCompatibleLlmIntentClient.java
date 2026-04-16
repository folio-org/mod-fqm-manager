package org.folio.fqm.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpenAiCompatibleLlmIntentClient implements LlmIntentClient {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String model;
  private final String apiKey;
  private final String chatCompletionsPath;
  private final double temperature;
  private final boolean jsonMode;

  @Autowired
  public OpenAiCompatibleLlmIntentClient(
    ObjectMapper objectMapper,
    @Value("${mod-fqm-manager.query-suggestions.llm.base-url:}") String baseUrl,
    @Value("${mod-fqm-manager.query-suggestions.llm.api-key:}") String apiKey,
    @Value("${mod-fqm-manager.query-suggestions.llm.model:}") String model,
    @Value("${mod-fqm-manager.query-suggestions.llm.chat-completions-path:/chat/completions}") String chatCompletionsPath,
    @Value("${mod-fqm-manager.query-suggestions.llm.temperature:0.0}") double temperature,
    @Value("${mod-fqm-manager.query-suggestions.llm.json-mode:true}") boolean jsonMode
  ) {
    this(
      RestClient.builder().baseUrl(baseUrl == null ? "" : baseUrl).build(),
      objectMapper,
      model,
      apiKey,
      chatCompletionsPath,
      temperature,
      jsonMode
    );
  }

  OpenAiCompatibleLlmIntentClient(
    RestClient restClient,
    ObjectMapper objectMapper,
    String model,
    String apiKey,
    String chatCompletionsPath,
    double temperature,
    boolean jsonMode
  ) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.model = model;
    this.apiKey = apiKey;
    this.chatCompletionsPath = chatCompletionsPath;
    this.temperature = temperature;
    this.jsonMode = jsonMode;
  }

  @Override
  public String complete(String systemPrompt, String userPrompt) {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", model);
    requestBody.put("temperature", temperature);
    requestBody.put("messages", List.of(
      Map.of("role", "system", "content", systemPrompt),
      Map.of("role", "user", "content", userPrompt)
    ));
    if (jsonMode) {
      requestBody.put("response_format", Map.of("type", "json_object"));
    }

    JsonNode response = restClient.post()
      .uri(chatCompletionsPath)
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
      .contentType(MediaType.APPLICATION_JSON)
      .accept(MediaType.APPLICATION_JSON)
      .body(requestBody)
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
