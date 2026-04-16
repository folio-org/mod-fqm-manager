package org.folio.fqm.service;

public interface LlmIntentClient {

  String complete(String systemPrompt, String userPrompt);
}
