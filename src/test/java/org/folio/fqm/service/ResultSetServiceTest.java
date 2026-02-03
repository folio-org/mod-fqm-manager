package org.folio.fqm.service;

import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import org.folio.fqm.client.LocaleClient;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.querytool.domain.dto.ArrayType;
import org.folio.querytool.domain.dto.DateTimeType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSourceDatabase;
import org.folio.querytool.domain.dto.NestedObjectProperty;
import org.folio.querytool.domain.dto.ObjectType;
import org.folio.querytool.domain.dto.SourceColumn;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.i18n.service.TranslationService;
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
        new EntityTypeColumn().name("dateTimeField").dataType(new DateTimeType()),
        new EntityTypeColumn().name("timestampField").dataType(new DateTimeType()),
        new EntityTypeColumn().name("offsetdateTimeField").dataType(new DateTimeType())
      )
    )
    .sources(List.of(
        new EntityTypeSourceDatabase()
          .type("db")
          .alias("source1")
          .target("target1")
      )
    );

  private ResultSetRepository resultSetRepository;
  private EntityTypeFlatteningService entityTypeFlatteningService;
  private LocaleClient localeClient;
  private ResultSetService service;
  private FolioExecutionContext executionContext;
  private TranslationService translationService;

  @BeforeEach
  void setUp() {
    this.resultSetRepository = mock(ResultSetRepository.class);
    this.entityTypeFlatteningService = mock(EntityTypeFlatteningService.class);
    this.localeClient = mock(LocaleClient.class);
    when(this.localeClient.getLocaleSettings()).thenReturn(new LocaleClient.LocaleSettings("en-US", "USD", "UTC", "latn"));
    this.executionContext = mock(FolioExecutionContext.class);
    this.translationService = mock(TranslationService.class);
    this.service = new ResultSetService(resultSetRepository, entityTypeFlatteningService, localeClient, executionContext, translationService);
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
          new EntityTypeSourceDatabase()
            .type("db")
            .alias("source1")
            .target("target1")
        )
      );
    expectedResult.forEach(content ->
      listIds.add(List.of(content.get(ID_FIELD_NAME).toString()))
    );
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(entityType);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(entityType);
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
      Arguments.of("Africa/Ceuta",
        "2024-12-23T23:30:00.000Z", Timestamp.from(Instant.parse("2024-12-23T23:30:00Z")), "2024-12-23T23:30:00.000+00:00",
        "2024-12-24"),
      Arguments.of("Africa/Ceuta",
        "2024-12-23T22:30:00.000Z", Timestamp.from(Instant.parse("2024-12-23T22:30:00Z")), "2024-12-23T22:30:00.000+00:00",
        "2024-12-23"),

      // in summer, this tz is UTC+2
      Arguments.of("Africa/Ceuta",
        "2024-06-23T22:30:00.000Z", Timestamp.from(Instant.parse("2024-06-23T22:30:00Z")), "2024-06-23T22:30:00.000+00:00",
        "2024-06-24"),

      // and sanity checks, in UTC
      Arguments.of("UTC",
        "2024-12-23T23:59:59.000Z", Timestamp.from(Instant.parse("2024-12-23T23:59:59Z")), "2024-12-23T23:59:59.000+00:00",
        "2024-12-23"),
      Arguments.of("UTC",
        "2024-12-23T00:00:00.000Z", Timestamp.from(Instant.parse("2024-12-23T00:00:00Z")), "2024-12-23T00:00:00.000+00:00",
        "2024-12-23")
    );
  }

  @ParameterizedTest(name = "should localize dates {1} to {4} for timezone {0}")
  @MethodSource("dateLocalizationTestCases")
  void testDateLocalization(String timezone, String dateTimeField, Timestamp timestampField, String offsetdateTimeField, String expected) {
    UUID entityTypeId = UUID.fromString(DATE_ENTITY_TYPE.getId());
    UUID contentId = UUID.fromString("900111ca-f498-5e8e-b12d-a90d275b5080");

    List<Map<String, Object>> repositoryResponse = List.of(
      Map.of(
        "id", contentId,
        "dateTimeField", dateTimeField,
        "timestampField", timestampField,
        "offsetdateTimeField", offsetdateTimeField
      )
    );
    List<Map<String, Object>> expectedResult = List.of(
      Map.of(
        "id", contentId,
        "dateTimeField", expected,
        "timestampField", expected,
        "offsetdateTimeField", expected
      )
    );
    List<String> fields = List.of("id", "dateTimeField", "timestampField", "offsetdateTimeField");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(List.of(contentId.toString()));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(DATE_ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(DATE_ENTITY_TYPE);
    when(localeClient.getLocaleSettings()).thenReturn(new LocaleClient.LocaleSettings("en-US", "USD", timezone, "latn"));
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
  void testInvalidDateHandling() {
    UUID entityTypeId = UUID.fromString(DATE_ENTITY_TYPE.getId());
    UUID contentId = UUID.fromString("bb0e294a-bd66-5878-a597-a2a3ac035d8e");

    Object extraordinarilyInvalidDate = new Object() {
      public String toString() {
        return "extraordinarily invalid date";
      }
    };

    List<Map<String, Object>> repositoryResponse = List.of(
      Map.of(
        "id", contentId,
        "dateTimeField", "invalid date", // verify strings are attempted to be parsed and handled gracefully
        "offsetdateTimeField", extraordinarilyInvalidDate // verify non-strings are handled gracefully
      )
    );
    List<Map<String, Object>> expectedResult = List.of(
      Map.of(
        "id", contentId,
        "dateTimeField", "invalid date",
        "offsetdateTimeField", extraordinarilyInvalidDate
      )
    );
    List<String> fields = List.of("id", "dateTimeField");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(List.of(contentId.toString()));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(DATE_ENTITY_TYPE);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(DATE_ENTITY_TYPE);
    when(localeClient.getLocaleSettings()).thenReturn(new LocaleClient.LocaleSettings("en-US", "USD", "UTC", "latn"));
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
  void getResultSet_shouldLocalizeTopLevelCountryField() {
    UUID entityTypeId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .columns(List.of(
        new EntityTypeColumn().name("id").isIdColumn(true),
        new EntityTypeColumn()
          .name("country")
          .source(new SourceColumn(entityTypeId, "countryId")
            .type(SourceColumn.TypeEnum.FQM)
            .name("countries"))
      ));
    List<String> fields = List.of("id", "country");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(List.of(contentId.toString()));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(entityType);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(entityType);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds))
      .thenReturn(List.of(Map.of("id", contentId.toString(), "country", "US")));
    when(translationService.format("mod-fqm-manager.countries.US")).thenReturn("United States");

    List<Map<String, Object>> actual = service.getResultSet(entityTypeId, fields, listIds, tenantIds, true);

    assertEquals("United States", actual.getFirst().get("country"));
  }

  @Test
  void getResultSet_shouldLocalizeNestedCountryField() {
    UUID entityTypeId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    String addressesJson = """
      [ {"city":"Auburn","countryId":"US"}, {"city":"Auburn","countryId":"MO"} ]
      """;
    EntityTypeColumn addressesColumn = new EntityTypeColumn()
      .name("addresses")
      .dataType(
        new ArrayType().itemDataType(
          new ObjectType().properties(List.of(
            new NestedObjectProperty()
              .name("country_id")
              .property("countryId")
              .source(new SourceColumn(entityTypeId, "country_id")
                .type(SourceColumn.TypeEnum.FQM)
                .name("countries"))
          ))
        )
      );
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .columns(List.of(
        new EntityTypeColumn().name("id").isIdColumn(true),
        addressesColumn
      ));
    List<String> fields = List.of("id", "addresses");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(List.of(contentId.toString()));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(entityType);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(entityType);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds))
      .thenReturn(List.of(Map.of("id", contentId.toString(), "addresses", addressesJson)));
    when(translationService.format("mod-fqm-manager.countries.US")).thenReturn("United States");
    when(translationService.format("mod-fqm-manager.countries.MO")).thenReturn("Macao");

    List<Map<String, Object>> actual = service.getResultSet(entityTypeId, fields, listIds, tenantIds, true);

    assertEquals(
      "[{\"city\":\"Auburn\",\"countryId\":\"United States\"},{\"city\":\"Auburn\",\"countryId\":\"Macao\"}]",
      actual.getFirst().get("addresses")
    );
  }

  @Test
  void getResultSet_shouldNotUpdateRootFieldWhenNoCountryCodeIsTranslated() {
    UUID entityTypeId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    String addressesJson = "[{\"city\":\"Auburn\",\"countryId\":\"ZZZ\"}]";
    EntityTypeColumn addressesColumn = new EntityTypeColumn()
      .name("addresses")
      .dataType(
        new ArrayType().itemDataType(
          new ObjectType().properties(List.of(
            new NestedObjectProperty()
              .name("country_id")
              .property("countryId")
              .source(new SourceColumn(entityTypeId, "country_id")
                .type(SourceColumn.TypeEnum.FQM)
                .name("countries"))
          ))
        )
      );
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .columns(List.of(
        new EntityTypeColumn().name("id").isIdColumn(true),
        addressesColumn
      ));
    List<String> fields = List.of("id", "addresses");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(List.of(contentId.toString()));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(entityType);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(entityType);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds))
      .thenReturn(List.of(Map.of("id", contentId.toString(), "addresses", addressesJson)));
    when(translationService.format("mod-fqm-manager.countries.ZZZ")).thenReturn(null);

    List<Map<String, Object>> actual = service.getResultSet(entityTypeId, fields, listIds, tenantIds, true);

    assertEquals(addressesJson, actual.getFirst().get("addresses"));
    verify(translationService).format("mod-fqm-manager.countries.ZZZ");
  }

  @Test
  void getResultSet_shouldNotLocalizeCountryFieldWhenFieldPathIsEmpty() {
    UUID entityTypeId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .name("test_entity")
      .id(entityTypeId.toString())
      .columns(List.of(
        new EntityTypeColumn().name("id").isIdColumn(true),
        new EntityTypeColumn()
          .name("")
          .source(new SourceColumn(entityTypeId, "countryId")
            .type(SourceColumn.TypeEnum.FQM)
            .name("countries"))
      ));
    List<String> fields = List.of("id", "");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(List.of(contentId.toString()));
    String countryCode = "US";

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(entityType);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(entityType);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds))
      .thenReturn(List.of(Map.of("id", contentId.toString(), "", countryCode)));

    List<Map<String, Object>> actual = service.getResultSet(entityTypeId, fields, listIds, tenantIds, true);

    assertEquals(countryCode, actual.getFirst().get(""));
    verify(translationService, never()).format(anyString());
  }

  @ParameterizedTest
  @MethodSource("topLevelCountryFieldInvalidCodeCases")
  void getResultSet_shouldNotLocalizeTopLevelCountryField_whenCodeIsInvalid(Object countryCode, String description) {
    UUID entityTypeId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .columns(List.of(
        new EntityTypeColumn().name("id").isIdColumn(true),
        new EntityTypeColumn()
          .name("country")
          .source(new SourceColumn(entityTypeId, "countryId")
            .type(SourceColumn.TypeEnum.FQM)
            .name("countries"))
      ));
    List<String> fields = List.of("id", "country");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(List.of(contentId.toString()));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(entityType);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(entityType);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds))
      .thenReturn(List.of(Map.of("id", contentId.toString(), "country", countryCode)));

    List<Map<String, Object>> actual = service.getResultSet(entityTypeId, fields, listIds, tenantIds, true);

    assertEquals(countryCode, actual.getFirst().get("country"), description);
    verify(translationService, never()).format(anyString());
  }

  @ParameterizedTest
  @MethodSource("nestedCountryFieldInvalidRootValueCases")
  void getResultSet_shouldNotLocalizeNestedCountryField_whenRootFieldValueIsInvalid(Object rootFieldValue, String description) {
    UUID entityTypeId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    var addressesColumn = new EntityTypeColumn()
      .name("addresses")
      .dataType(
        new ArrayType().itemDataType(
          new ObjectType().properties(List.of(
            new NestedObjectProperty()
              .name("country_id")
              .property("countryId")
              .source(new SourceColumn(entityTypeId, "country_id")
                .type(SourceColumn.TypeEnum.FQM)
                .name("countries"))
          ))
        )
      );
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .columns(List.of(
        new EntityTypeColumn().name("id").isIdColumn(true),
        addressesColumn
      ));
    List<String> fields = List.of("id", "addresses");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(List.of(contentId.toString()));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(entityType);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(entityType);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds))
      .thenReturn(List.of(Map.of("id", contentId.toString(), "addresses", rootFieldValue)));

    List<Map<String, Object>> actual = service.getResultSet(entityTypeId, fields, listIds, tenantIds, true);

    assertEquals(rootFieldValue, actual.getFirst().get("addresses"), description);
    verify(translationService, never()).format(anyString());
  }

  @ParameterizedTest
  @MethodSource("arrayElementNotExpectedTypeCases")
  void getResultSet_shouldNotChangeArray_whenElementIsNotExpectedType(String addressesJson, String description) {
    UUID entityTypeId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    var addressesColumn = new EntityTypeColumn()
      .name("addresses")
      .dataType(
        new ArrayType().itemDataType(
          new ObjectType().properties(List.of(
            new NestedObjectProperty()
              .name("country_id")
              .property("countryId")
              .source(new SourceColumn(entityTypeId, "country_id")
                .type(SourceColumn.TypeEnum.FQM)
                .name("countries"))
          ))
        )
      );
    EntityType entityType = new EntityType()
      .name("test_entity")
      .id(entityTypeId.toString())
      .columns(List.of(new EntityTypeColumn().name("id").isIdColumn(true), addressesColumn))
      .sources(List.of(new EntityTypeSourceDatabase().type("db").alias("source1").target("target1")));
    List<String> fields = List.of("id", "addresses");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(List.of(contentId.toString()));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(entityType);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(entityType);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds))
      .thenReturn(List.of(Map.of("id", contentId.toString(), "addresses", addressesJson)));

    List<Map<String, Object>> actual = service.getResultSet(entityTypeId, fields, listIds, tenantIds, true);

    assertEquals(1, actual.size(), description);
    verify(translationService, never()).format(anyString());
  }

  @ParameterizedTest
  @MethodSource("missingTranslationCases")
  void getResultSet_shouldNotLocalizeCountryField_whenTranslationIsMissing(String translationResult, String description) {
    UUID entityTypeId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .columns(List.of(
        new EntityTypeColumn().name("id").isIdColumn(true),
        new EntityTypeColumn().name("countryId")
          .source(new SourceColumn(entityTypeId, "countryId")
            .type(SourceColumn.TypeEnum.FQM)
            .name("countries"))
      ));
    List<String> fields = List.of("id", "countryId");
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(List.of(contentId.toString()));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(entityType);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(entityType);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds))
      .thenReturn(List.of(Map.of("id", contentId.toString(), "countryId", "US")));
    when(translationService.format("mod-fqm-manager.countries.US")).thenReturn(translationResult);

    List<Map<String, Object>> actual = service.getResultSet(entityTypeId, fields, listIds, tenantIds, true);

    assertEquals("US", actual.getFirst().get("countryId"), description);
  }

  @ParameterizedTest
  @MethodSource("blankFieldNameCases")
  void getResultSet_shouldNotLocalizeNestedCountryField_whenFieldNameIsBlank(
    String rootFieldName,
    String leafFieldName,
    String description) {
    UUID entityTypeId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    String addressesJson = "[{\"city\":\"Auburn\",\"countryId\":\"US\"}]";

    EntityTypeColumn addressesColumn = new EntityTypeColumn()
      .name(rootFieldName)
      .dataType(
        new ArrayType().itemDataType(
          new ObjectType().properties(List.of(
            new NestedObjectProperty()
              .name(leafFieldName)
              .property(leafFieldName)
              .source(new SourceColumn(entityTypeId, "country_id")
                .type(SourceColumn.TypeEnum.FQM)
                .name("countries"))
          ))
        )
      );
    EntityType entityType = new EntityType()
      .id(entityTypeId.toString())
      .columns(List.of(
        new EntityTypeColumn().name("id").isIdColumn(true),
        addressesColumn
      ));
    List<String> fields = List.of("id", rootFieldName);
    List<String> tenantIds = List.of("tenant_01");
    List<List<String>> listIds = List.of(List.of(contentId.toString()));

    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, null, true)).thenReturn(entityType);
    when(entityTypeFlatteningService.getFlattenedEntityType(entityTypeId, "tenant_01", true)).thenReturn(entityType);
    when(resultSetRepository.getResultSet(entityTypeId, fields, listIds, tenantIds))
      .thenReturn(List.of(Map.of("id", contentId.toString(), rootFieldName, addressesJson)));

    List<Map<String, Object>> actual = service.getResultSet(entityTypeId, fields, listIds, tenantIds, true);

    assertEquals(addressesJson, actual.getFirst().get(rootFieldName), description);
    verify(translationService, never()).format(anyString());
  }

  private static List<Arguments> topLevelCountryFieldInvalidCodeCases() {
    return List.of(
      Arguments.of(" ", "blank string"),
      Arguments.of(123, "non-string")
    );
  }

  private static List<Arguments> nestedCountryFieldInvalidRootValueCases() {
    return List.of(
      Arguments.of(" ", "blank string"),
      Arguments.of(123, "non-string"),
      Arguments.of("{\"city\":\"Auburn\",\"countryId\":\"US\"}", "valid JSON but not array"),
      Arguments.of("not-json", "invalid JSON")
    );
  }

  private static List<Arguments> arrayElementNotExpectedTypeCases() {
    return List.of(
      Arguments.of("[\"notAnObject\"]", "element is not an object"),
      Arguments.of("[{\"city\":\"Auburn\"},{\"city\":\"Auburn\",\"countryId\":123}]", "value node is not textual")
    );
  }

  static java.util.stream.Stream<Arguments>  missingTranslationCases() {
    return java.util.stream.Stream.of(
      Arguments.of(null, "TranslationService returned null"),
      Arguments.of(" ", "TranslationService returned blank string"),
      Arguments.of("mod-fqm-manager.countries.US", "TranslationService returned key")
    );
  }

  private static List<Arguments> blankFieldNameCases() {
    return List.of(
      Arguments.of("", "country_id", "blank root field name"),
      Arguments.of("addresses", "", "blank leaf field name")
    );
  }
}
