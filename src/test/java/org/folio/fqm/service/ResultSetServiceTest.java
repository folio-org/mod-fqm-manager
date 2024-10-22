package org.folio.fqm.service;

import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;

import java.sql.Timestamp;

import java.util.*;

import feign.FeignException;
import org.folio.fqm.client.ConfigurationClient;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class ResultSetServiceTest {

  private static final EntityType DATE_ENTITY_TYPE = new EntityType()
    .name("test_entity")
    .id(UUID.randomUUID().toString())
    .columns(
      List.of(
        new EntityTypeColumn().name("id").isIdColumn(true),
        new EntityTypeColumn().name("dateField").dataType(new DateType()),
        new EntityTypeColumn().name("timestampField").dataType(new DateType()),
        new EntityTypeColumn().name("offsetDateField").dataType(new DateType())
      )
    )
    .sources(List.of(
        new EntityTypeSource()
          .type("db")
          .alias("source1")
          .target("target1")
      )
    );
  private static final String LOCALE_SETTINGS = """
    {
      "configs": [
        {
           "id":"2a132a01-623b-4d3a-9d9a-2feb777665c2",
           "module":"ORG",
           "configName":"localeSettings",
           "enabled":true,
           "value":"{\\"locale\\":\\"en-US\\",\\"timezone\\":\\"Africa/Lagos\\",\\"currency\\":\\"USD\\"}"
        }
      ],
      "totalRecords": 1
    }""";
  private ResultSetRepository resultSetRepository;
  private EntityTypeFlatteningService entityTypeFlatteningService;
  private ConfigurationClient configurationClient;
  private ResultSetService service;

  @BeforeEach
  void setUp() {
    this.resultSetRepository = mock(ResultSetRepository.class);
    this.entityTypeFlatteningService = mock(EntityTypeFlatteningService.class);
    this.configurationClient = mock(ConfigurationClient.class);
    this.service = new ResultSetService(resultSetRepository, entityTypeFlatteningService, configurationClient);
  }

  @Test
  void shouldGetResultSet() {
    UUID entityTypeId = UUID.randomUUID();
    UUID deletedContentId = UUID.randomUUID();
    List<Map<String, Object>> expectedResult = new ArrayList<>(TestDataFixture.getEntityContents());
    List<Map<String, Object>> reversedContent = new ArrayList<>(Lists.reverse(expectedResult));
    expectedResult.add(Map.of("id", deletedContentId.toString(), "_deleted", true));
    List<String> fields = List.of("id", "key1", "key2");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = new ArrayList<>();
    EntityType entityType = new EntityType()
      .name("test_entity")
      .id(entityTypeId.toString())
      .columns(
        List.of(
          new EntityTypeColumn().name("id").isIdColumn(true),
          new EntityTypeColumn().name("key1"),
          new EntityTypeColumn().name("key2")
        )
      )
      .sources(List.of(
          new EntityTypeSource()
            .type("db")
            .alias("source1")
            .target("target1")
        )
      );
    expectedResult.forEach(content ->
      listIds.add(List.of(content.get(ID_FIELD_NAME).toString()))
    );
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(entityType);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01")).thenReturn(entityType);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds)).thenReturn(reversedContent);
    List<Map<String, Object>> actualResult = service.getResultSet(
      entityTypeId,
      fields,
      listIds,
      tenantIds,
      false
    );
    assertEquals(expectedResult, actualResult);
  }

  @Test
  void shouldLocalizeDatesIfRequested() {
    UUID entityTypeId = UUID.fromString(DATE_ENTITY_TYPE.getId());
    UUID contentId = UUID.randomUUID();
    // For a UTC+1 timezone, a record at 2024-10-23T23:30:00.000Z UTC should get localized to 2024-10-24T00:30:00.000Z,
    // which will then be truncated to 2024-10-24
    List<Map<String, Object>> repositoryResponse = List.of(
      Map.of(
        "id", contentId,
        "dateField", "2024-10-23T23:30:00.000Z",
        "timestampField", Timestamp.valueOf("2024-10-23 23:30:00"),
        "offsetDateField", "2024-10-23T23:30:00.000+00:00"
      )
    );
    List<Map<String, Object>> expectedResult = List.of(
      Map.of(
        "id", contentId,
        "dateField", "2024-10-24",
        "timestampField", "2024-10-24",
        "offsetDateField", "2024-10-24"
      )
    );
    List<String> fields = List.of("id", "dateField", "timestampField", "offsetDateField");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(
      List.of(contentId.toString())
    );

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(DATE_ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01")).thenReturn(DATE_ENTITY_TYPE);
    when(configurationClient.getLocaleSettings()).thenReturn(LOCALE_SETTINGS);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds)).thenReturn(repositoryResponse);

    List<Map<String, Object>> actualResult = service.getResultSet(
      entityTypeId,
      fields,
      listIds,
      tenantIds,
      true
    );
    assertEquals(expectedResult, actualResult);
  }

  @Test
  void shouldUseUtcOffsetIfConfigurationClientThrowsError() {
    UUID entityTypeId = UUID.fromString(DATE_ENTITY_TYPE.getId());
    UUID contentId = UUID.randomUUID();
    List<Map<String, Object>> repositoryResponse = List.of(
      Map.of(
        "id", contentId, "dateField", "2024-10-24T00:00:00.000Z")
    );
    List<Map<String, Object>> expectedResult = List.of(
      Map.of("id", contentId, "dateField", "2024-10-24")
    );
    List<String> fields = List.of("id", "dateField");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(
      List.of(contentId.toString())
    );


    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(DATE_ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01")).thenReturn(DATE_ENTITY_TYPE);
    when(configurationClient.getLocaleSettings()).thenThrow(FeignException.NotFound.class);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds)).thenReturn(repositoryResponse);

    List<Map<String, Object>> actualResult = service.getResultSet(
      entityTypeId,
      fields,
      listIds,
      tenantIds,
      true
    );
    assertEquals(expectedResult, actualResult);
  }
}
