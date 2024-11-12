package org.folio.fqm.service;

import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import org.folio.fqm.client.ConfigurationClient;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


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

  static List<Arguments> dateLocalizationTestCases() {
    return List.of(
      // (tz, date string, timestamp, offset date string, expected)

      // Africa/Ceuta is UTC+1 in normal time, UTC+2 in summer

      // For a UTC+1 timezone, a record at 2024-12-23T23:30:00.000Z UTC should get localized to 2024-12-24T00:30:00.000Z,
      // which will then be truncated to 2024-12-24
      Arguments.of(ZoneId.of("Africa/Ceuta"),
        "2024-12-23T23:30:00.000Z", Timestamp.from(Instant.parse("2024-12-23T23:30:00Z")), "2024-12-23T23:30:00.000+00:00",
        "2024-12-24"),
      Arguments.of(ZoneId.of("Africa/Ceuta"),
        "2024-12-23T22:30:00.000Z", Timestamp.from(Instant.parse("2024-12-23T22:30:00Z")), "2024-12-23T22:30:00.000+00:00",
        "2024-12-23"),

      // in summer, this tz is UTC+2
      Arguments.of(ZoneId.of("Africa/Ceuta"),
        "2024-06-23T22:30:00.000Z", Timestamp.from(Instant.parse("2024-06-23T22:30:00Z")), "2024-06-23T22:30:00.000+00:00",
        "2024-06-24"),

      // and sanity checks, in UTC
      Arguments.of(ZoneId.of("UTC"),
        "2024-12-23T23:59:59.000Z", Timestamp.from(Instant.parse("2024-12-23T23:59:59Z")), "2024-12-23T23:59:59.000+00:00",
        "2024-12-23"),
      Arguments.of(ZoneId.of("UTC"),
        "2024-12-23T00:00:00.000Z", Timestamp.from(Instant.parse("2024-12-23T00:00:00Z")), "2024-12-23T00:00:00.000+00:00",
        "2024-12-23")
    );
  }

  @ParameterizedTest(name = "should localize dates {1} to {4} for timezone {0}")
  @MethodSource("dateLocalizationTestCases")
  void testDateLocalization(ZoneId timezone, String dateField, Timestamp timestampField, String offsetDateField, String expected) {
    UUID entityTypeId = UUID.fromString(DATE_ENTITY_TYPE.getId());
    UUID contentId = UUID.fromString("900111ca-f498-5e8e-b12d-a90d275b5080");

    List<Map<String, Object>> repositoryResponse = List.of(
      Map.of(
        "id", contentId,
        "dateField", dateField,
        "timestampField", timestampField,
        "offsetDateField", offsetDateField
      )
    );
    List<Map<String, Object>> expectedResult = List.of(
      Map.of(
        "id", contentId,
        "dateField", expected,
        "timestampField", expected,
        "offsetDateField", expected
      )
    );
    List<String> fields = List.of("id", "dateField", "timestampField", "offsetDateField");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(
      List.of(contentId.toString())
    );

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null)).thenReturn(DATE_ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01")).thenReturn(DATE_ENTITY_TYPE);
    when(configurationClient.getTenantTimezone()).thenReturn(timezone);
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
