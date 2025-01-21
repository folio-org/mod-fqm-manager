package org.folio.fqm.migration.strategies;

import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.MigrationStrategy;
import org.jooq.DSLContext;
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

import java.util.List;
import java.util.UUID;

import static org.folio.fqm.migration.strategies.V13CustomFieldRename.CUSTOM_FIELD_SOURCE_VIEW;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_NAME;
import static org.folio.fqm.repository.EntityTypeRepository.CUSTOM_FIELD_TYPE;
import static org.folio.fqm.repository.EntityTypeRepository.SUPPORTED_CUSTOM_FIELD_TYPES;
import static org.jooq.impl.DSL.field;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class V13CustomFieldRenameTest extends TestTemplate {

  private static final String CUSTOM_FIELD_ID_1 = "97ebe38d-4733-4631-b264-5d29f4f50b07";
  private static final String CUSTOM_FIELD_ID_2 = "48e035c3-d2fe-4575-b387-e7fdc07dde58";
  private static final String CUSTOM_FIELD_NAME_1 = "a_custom_field";
  private static final String CUSTOM_FIELD_NAME_2 = "another_custom_field";

  @Mock
  DSLContext jooqContext;

  @BeforeEach
  public void setup() {
    DSLContext creator = DSL.using(new DefaultConfiguration()); // for creating results

    Result<Record2<Object, Object>> result = creator.newResult(
      DSL.field("id", Object.class),
      DSL.field("custom_field_name", Object.class)
    );
    result.add(creator.newRecord(
      DSL.field("id", Object.class),
      DSL.field(CUSTOM_FIELD_NAME, Object.class)
    ).values(CUSTOM_FIELD_ID_1, CUSTOM_FIELD_NAME_1));
    result.add(creator.newRecord(
      DSL.field("id", Object.class),
      DSL.field(CUSTOM_FIELD_NAME, Object.class)
    ).values(CUSTOM_FIELD_ID_2, CUSTOM_FIELD_NAME_2));

    SelectSelectStep<Record2<Object, Object>> mockSelect = mock(SelectSelectStep.class);
    lenient().when(jooqContext.select(field("id"), field(CUSTOM_FIELD_NAME))).thenReturn(mockSelect);

    SelectJoinStep<Record2<Object, Object>> selectJoinStep = mock(SelectJoinStep.class);
    lenient().when(mockSelect.from(CUSTOM_FIELD_SOURCE_VIEW)).thenReturn(selectJoinStep);

    SelectConditionStep<Record2<Object, Object>> selectConditionStep = mock(SelectConditionStep.class);
    lenient().when(selectJoinStep.where(field(CUSTOM_FIELD_TYPE).in(SUPPORTED_CUSTOM_FIELD_TYPES))).thenReturn(selectConditionStep);

    lenient().when(selectConditionStep.fetch()).thenReturn(result);
  }

  @Override
  public MigrationStrategy getStrategy() {
    return new V13CustomFieldRename(jooqContext);
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
          .fqlQuery("{\"code\": {\"$ne\": \"active\"}, \"_version\":\"14\"}")
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
          .fqlQuery("{\"_custom_field_97ebe38d-4733-4631-b264-5d29f4f50b07\": {\"$ne\": \"active\"}, \"_version\":\"14\"}")
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
          .fqlQuery("{\"_custom_field_97ebe38d-4733-4631-b264-5d29f4f50b07\": {\"$in\": [\"active\", \"inactive\"]}, \"_version\":\"14\"}")
          .fields(List.of())
          .build()
      ),

      Arguments.of(
        "$and query with custom field",
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"$and\": [{\"a_custom_field\": {\"$ne\": \"val1\"}}, {\"not_a_custom_field\": {\"$ne\": \"val2\"}}, {\"another_custom_field\": {\"$eq\": \"val3\"}}]}")
          .fields(List.of())
          .build(),
        MigratableQueryInformation
          .builder()
          .entityTypeId(UUID.fromString("ddc93926-d15a-4a45-9d9c-93eadc3d9bbf"))
          .fqlQuery("{\"$and\": [{\"_custom_field_97ebe38d-4733-4631-b264-5d29f4f50b07\": {\"$ne\": \"val1\"}}, {\"not_a_custom_field\": {\"$ne\": \"val2\"}}, {\"_custom_field_48e035c3-d2fe-4575-b387-e7fdc07dde58\": {\"$eq\": \"val3\"}}], \"_version\":\"14\"}")
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
          .fqlQuery("{\"not_a_custom_field\": {\"$in\": [\"active\", \"inactive\"]}, \"_version\":\"14\"}")
          .fields(List.of("not_a_custom_field", "_custom_field_97ebe38d-4733-4631-b264-5d29f4f50b07", "_custom_field_48e035c3-d2fe-4575-b387-e7fdc07dde58"))
          .build()
      )
    );
  }
}
