package org.folio.fqm.service;

import lombok.extern.log4j.Log4j2;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.fqm.utils.EntityTypeUtils;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Log4j2
public class ResultSetService {

  private final ResultSetRepository resultSetRepository;
  private final EntityTypeFlatteningService entityTypeFlatteningService;

  public List<Map<String, Object>> getResultSet(UUID entityTypeId,
                                                List<String> fields,
                                                List<List<String>> ids, List<String> tenantsToQuery) {
    List<Map<String, Object>> unsortedResults = resultSetRepository.getResultSet(entityTypeId, fields, ids, tenantsToQuery);

    // Sort the contents in Java code as sorting in DB views run very slow intermittently
    return getSortedContents(entityTypeId, ids, unsortedResults);
  }

  private List<Map<String, Object>> getSortedContents(UUID entityTypeId, List<List<String>> contentIds, List<Map<String, Object>> unsortedResults) {
    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null);
    List<String> idColumnNames = EntityTypeUtils.getIdColumnNames(entityType);
    Map<List<String>, Map<String, Object>> contentsMap = unsortedResults.stream()
      .collect(Collectors.toMap(content -> {
            List<String> keys = new ArrayList<>();
            idColumnNames.forEach(columnName -> keys.add(content.containsKey(columnName) ? content.get(columnName).toString() : "NULL"));
            return keys;
          },
          Function.identity())
      );

    return contentIds
      .stream()
      .map(id -> {
        var contents = contentsMap.get(id);
        if (contents == null) {
          // Record has been deleted. Populate the idColumns of the record, and add a _deleted key to indicate deletion.
          Map<String, Object> deletedRecordMap = new HashMap<>();
          AtomicInteger columnCount = new AtomicInteger(0);
          deletedRecordMap.put("_deleted", true);
          idColumnNames.forEach(idColumnName -> deletedRecordMap.put(idColumnName, id.get(columnCount.getAndIncrement())));
          return deletedRecordMap;
        }
        return contents;
      })
      .toList();
  }
}
