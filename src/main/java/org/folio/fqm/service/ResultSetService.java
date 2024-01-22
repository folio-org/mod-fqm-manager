package org.folio.fqm.service;

import org.folio.fqm.repository.ResultSetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ResultSetService {

  private final ResultSetRepository resultSetRepository;

  public List<Map<String, Object>> getResultSet(UUID entityTypeId,
                                                List<String> fields,
                                                List<UUID> ids) {
    List<Map<String, Object>> unsortedResults = resultSetRepository.getResultSet(entityTypeId, fields, ids);
    // Sort the contents in Java code as sorting in DB views run very slow intermittently
    return getSortedContents(ids, unsortedResults);
  }

  private static List<Map<String, Object>> getSortedContents(List<UUID> contentIds, List<Map<String, Object>> unsortedResults) {
    Map<UUID, Map<String, Object>> contentsMap = unsortedResults.stream()
      .collect(Collectors.toMap(content -> (UUID) content.get(ID_FIELD_NAME), Function.identity()));

    return contentIds.stream()
      .map(id -> contentsMap.getOrDefault(id, Map.of("id", id, "_deleted", true)))
      .toList();
  }
}
