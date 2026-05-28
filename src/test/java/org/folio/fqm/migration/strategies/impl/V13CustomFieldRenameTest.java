package org.folio.fqm.migration.strategies.impl;

import static org.folio.fqm.migration.strategies.impl.V13CustomFieldRename.CUSTOM_FIELD_SOURCE_VIEW;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_NAME;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_TYPE;
import static org.folio.fqm.repository.EntityTypeRepository.SUPPORTED_CUSTOM_FIELD_TYPES;
import static org.jooq.impl.DSL.field;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.strategies.MigrationStrategy;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectSelectStep;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.folio.fqm.migration.strategies.impl.V13CustomFieldRename;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_PREPENDER;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V13CustomFieldRenameTest extends TestTemplate {

  private static final String CUSTOM_FIELD_ID_1 = "97ebe38d-4733-4631-b264-5d29f4f50b07";
  private static final String CUSTOM_FIELD_ID_2 = "48e035c3-d2fe-4575-b387-e7fdc07dde58";
  private static final String CUSTOM_FIELD_NAME_1 = "a_custom_field";
  private static final String CUSTOM_FIELD_NAME_2 = "another_custom_field";
  private static final Field<Object> ID_FIELD = field("id", Object.class);
  private static final Field<Object> NAME_FIELD = field(CUSTOM_FIELD_NAME, Object.class);

    private static final UUID USERS_ENTITY_TYPE_ID = UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf");

  @Mock
  DSLContext jooqContext;

  @Mock
  FolioExecutionContext executionContext;

    @InjectMocks
private V13CustomFieldRename v13CustomFieldRename;

  @BeforeEach
  void setup() {
    DSLContext creator = DSL.using(new DefaultConfiguration()); // for creating results

    Result<Record2<Object, Object>> result = creator.newResult(ID_FIELD, NAME_FIELD);
    result.add(creator.newRecord(ID_FIELD, NAME_FIELD).values(CUSTOM_FIELD_ID_1, CUSTOM_FIELD_NAME_1));
    result.add(creator.newRecord(ID_FIELD, NAME_FIELD).values(CUSTOM_FIELD_ID_2, CUSTOM_FIELD_NAME_2));

    lenient().when(executionContext.getTenantId()).thenReturn("tenant_01");
    SelectSelectStep<Record2<Object, Object>> mockSelect = mock(SelectSelectStep.class);
    lenient().when(jooqContext.select(ID_FIELD, field(CUSTOM_FIELD_NAME))).thenReturn(mockSelect);

    SelectJoinStep<Record2<Object, Object>> selectJoinStep = mock(SelectJoinStep.class);
    lenient().when(mockSelect.from(CUSTOM_FIELD_SOURCE_VIEW)).thenReturn(selectJoinStep);

    SelectConditionStep<Record2<Object, Object>> selectConditionStep = mock(SelectConditionStep.class);
    lenient()
      .when(selectJoinStep.where(field(CUSTOM_FIELD_TYPE).in(SUPPORTED_CUSTOM_FIELD_TYPES)))
      .thenReturn(selectConditionStep);

    lenient().when(selectConditionStep.fetch()).thenReturn(result);
  }

  @Override
  public MigrationStrategy getStrategy() {
    return new V13CustomFieldRename(executionContext, jooqContext);
  }

  @Override
  public List<Arguments> getExpectedTransformations() {
    return List.of(
      Arguments.of(
        "Query with non-matching entity type",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1"))
          .fqlQuery("{\"code\": {\"$ne\": \"active\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("a9112682-958f-576c-b46c-d851abc62cd1"))
          .fqlQuery("{\"code\": {\"$ne\": \"active\"}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "$eq query with custom field",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"a_custom_field\": {\"$ne\": \"active\"}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"_custom_field_97ebe38d-4733-4631-b264-5d29f4f50b07\": {\"$ne\": \"active\"}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "$in query with custom field",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"a_custom_field\": {\"$in\": [\"active\", \"inactive\"]}}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"_custom_field_97ebe38d-4733-4631-b264-5d29f4f50b07\": {\"$in\": [\"active\", \"inactive\"]}}")
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "$and query with custom field",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery(
            "{\"$and\": [{\"a_custom_field\": {\"$ne\": \"val1\"}}, {\"not_a_custom_field\": {\"$ne\": \"val2\"}}, {\"another_custom_field\": {\"$eq\": \"val3\"}}]}"
          )
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery(
            "{\"$and\": [{\"_custom_field_97ebe38d-4733-4631-b264-5d29f4f50b07\": {\"$ne\": \"val1\"}}, {\"not_a_custom_field\": {\"$ne\": \"val2\"}}, {\"_custom_field_48e035c3-d2fe-4575-b387-e7fdc07dde58\": {\"$eq\": \"val3\"}}]}"
          )
          .fields(List.of())
          .build()
      ),
      Arguments.of(
        "query with custom fields in field array",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"not_a_custom_field\": {\"$in\": [\"active\", \"inactive\"]}}")
          .fields(List.of("not_a_custom_field", "a_custom_field", "another_custom_field"))
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"not_a_custom_field\": {\"$in\": [\"active\", \"inactive\"]}}")
          .fields(
            List.of(
              "not_a_custom_field",
              "_custom_field_97ebe38d-4733-4631-b264-5d29f4f50b07",
              "_custom_field_48e035c3-d2fe-4575-b387-e7fdc07dde58"
            )
          )
          .build()
      )
    );
  }

    @Test
    void testGetFieldChangesShouldHandleDuplicateCustomFieldNames() {
      // TestMate-45cb1c613cc20dd211ab277591709559
      // Given
      // Use a unique tenant ID to avoid hitting the internal cache populated by other tests
      String tenantId = "tenant_duplicate_test";
      String duplicateFieldName = "duplicate_field_name";
      String id1 = "11111111-1111-1111-1111-111111111111";
      String id2 = "22222222-2222-2222-2222-222222222222";
      // Create a fresh instance to ensure the internal ConcurrentHashMap cache is empty for this test
      V13CustomFieldRename localStrategy = new V13CustomFieldRename(executionContext, jooqContext);
      // Define fields matching the types used in the implementation's select call
      Field<String> idField = field("id", String.class);
      Field<String> nameField = field(CUSTOM_FIELD_NAME, String.class);
      DSLContext creator = DSL.using(new DefaultConfiguration());
      Result<Record2<String, String>> result = creator.newResult(idField, nameField);
      result.add(creator.newRecord(idField, nameField).values(id1, duplicateFieldName));
      result.add(creator.newRecord(idField, nameField).values(id2, duplicateFieldName));
      // Mock the tenant ID for this specific execution
      when(executionContext.getTenantId()).thenReturn(tenantId);
      // Use flexible matchers for jOOQ fluent API to avoid issues with Field object identity/equality in Mockito
      SelectSelectStep selectStep = mock(SelectSelectStep.class);
      SelectJoinStep joinStep = mock(SelectJoinStep.class);
      SelectConditionStep conditionStep = mock(SelectConditionStep.class);
      // Mock the chain: select -> from -> where -> fetch
      // We use any() to ensure this mock takes precedence over the lenient mocks in setup()
      when(jooqContext.select(org.mockito.ArgumentMatchers.any(Field.class), org.mockito.ArgumentMatchers.any(Field.class)))
        .thenReturn(selectStep);
      when(selectStep.from(CUSTOM_FIELD_SOURCE_VIEW)).thenReturn(joinStep);
      when(joinStep.where(org.mockito.ArgumentMatchers.any(org.jooq.Condition.class))).thenReturn(conditionStep);
      when(conditionStep.fetch()).thenReturn(result);
      // When
      Map<UUID, Map<String, String>> actualChanges = localStrategy.getFieldChanges();
      // Then
      assertThat(actualChanges).containsOnlyKeys(USERS_ENTITY_TYPE_ID);
      Map<String, String> userFieldChanges = actualChanges.get(USERS_ENTITY_TYPE_ID);
      // Verify collision resolution: the last entry in the result set should overwrite the first
      assertThat(userFieldChanges)
        .hasSize(1)
        .containsEntry(duplicateFieldName, CUSTOM_FIELD_PREPENDER + id2);
    }
}
