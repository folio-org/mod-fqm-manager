package org.folio.fqm.service;

import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;

import java.util.*;

import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResultSetServiceTest {

  private ResultSetRepository resultSetRepository;
  private EntityTypeRepository entityTypeRepository;
  private ResultSetService service;

  @BeforeEach
  void setUp() {
    this.resultSetRepository = mock(ResultSetRepository.class);
    this.entityTypeRepository = mock(EntityTypeRepository.class);
    this.service = new ResultSetService(resultSetRepository, entityTypeRepository);
  }

  @Test
  void shouldGetResultSet() {
    UUID entityTypeId = UUID.randomUUID();
    UUID deletedContentId = UUID.randomUUID();
    List<Map<String, Object>> expectedResult = new ArrayList<>(TestDataFixture.getEntityContents());
    List<Map<String, Object>> reversedContent = new ArrayList<>(Lists.reverse(expectedResult));
    expectedResult.add(Map.of("id", deletedContentId.toString(), "_deleted", true));
    List<String> fields = List.of("id", "key1", "key2");
    List<List<String>> listIds = new ArrayList<>();
    expectedResult.forEach(content ->
      listIds.add(List.of(content.get(ID_FIELD_NAME).toString()))
    );

    when(
      entityTypeRepository.getEntityTypeDefinition(entityTypeId)
    )
    .thenReturn(
      Optional.of(
        new EntityType()
        .name("test_entity")
        .id(entityTypeId.toString())
        .columns(
          List.of(
            new EntityTypeColumn().name("id").isIdColumn(true),
            new EntityTypeColumn().name("key1"),
            new EntityTypeColumn().name("key2")
          )
        )
      )
    );
    when(
      resultSetRepository.getResultSet(entityTypeId, fields, listIds)
    )
      .thenReturn(reversedContent);
    List<Map<String, Object>> actualResult = service.getResultSet(
      entityTypeId,
      fields,
      listIds
    );
    assertEquals(expectedResult, actualResult);
  }
}
