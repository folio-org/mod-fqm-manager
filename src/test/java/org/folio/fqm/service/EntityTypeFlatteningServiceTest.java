package org.folio.fqm.service;

import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.querytool.domain.dto.EntityTypeSourceJoin;
import org.folio.querytool.domain.dto.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityTypeFlatteningServiceTest {

  @Mock
  private EntityTypeRepository entityTypeRepository;
  @InjectMocks
  private EntityTypeFlatteningService entityTypeFlatteningService;

  private static final UUID SIMPLE_ENTITY_TYPE_ID = UUID.fromString("0686b9e4-accd-46f8-9e35-792c735733bb");
  private static final UUID COMPLEX_ENTITY_TYPE_ID = UUID.fromString("6c28028a-ca3b-4415-94e8-8525257abbab");
  private static final UUID TRIPLE_NESTED_ENTITY_TYPE_ID = UUID.fromString("2bb4d642-7cc5-4039-938b-03cb158d7b32");
  private static final UUID UNORDERED_ENTITY_TYPE_ID = UUID.fromString("8b7f0323-20e4-4344-8eb3-20225994b46b");
  private static final EntityType SIMPLE_ENTITY_TYPE = new EntityType()
    .name("simple_entity_type")
    .id(SIMPLE_ENTITY_TYPE_ID.toString())
    .columns(List.of(
      new EntityTypeColumn()
        .name("field1")
        .valueGetter(":sourceAlias.field1")
        .dataType(new StringType())
        .isIdColumn(true)
        .sourceAlias("source1"),
      new EntityTypeColumn()
        .name("field2")
        .valueGetter(":sourceAlias.field2")
        .filterValueGetter("lower(:sourceAlias.field2)")
        .dataType(new StringType())
        .sourceAlias("source1")
    ))
    .sources(List.of(
      new EntityTypeSource()
        .type("db")
        .alias("source1")
        .target("source1_target")
    ));

  private static final EntityType COMPLEX_ENTITY_TYPE = new EntityType()
    .name("complex_entity_type")
    .id(COMPLEX_ENTITY_TYPE_ID.toString())
    .columns(List.of(
      new EntityTypeColumn()
        .name("field3")
        .valueGetter(":sourceAlias.field3")
        .dataType(new StringType())
        .sourceAlias("source2")
        .isIdColumn(true),
      new EntityTypeColumn()
        .name("field4")
        .valueGetter(":sourceAlias.field4")
        .filterValueGetter("lower(:sourceAlias.field4)")
        .dataType(new StringType())
        .sourceAlias("source2"),
      new EntityTypeColumn()
        .name("field5")
        .valueGetter(":sourceAlias.field5")
        .dataType(new StringType())
        .sourceAlias("source3")
        .isIdColumn(true),
      new EntityTypeColumn()
        .name("field6")
        .valueGetter(":sourceAlias.field6")
        .filterValueGetter("lower(:sourceAlias.field6)")
        .dataType(new StringType())
        .sourceAlias("source3")
    ))
    .sources(List.of(
      new EntityTypeSource()
        .type("db")
        .alias("source3")
        .target("source3_target")
        .join(
          new EntityTypeSourceJoin()
            .type("LEFT JOIN")
            .joinTo("source2")
            .condition(":this.field5 = :that.field3")
        ),
      new EntityTypeSource()
        .type("db")
        .alias("source2")
        .target("source2_target"),
      new EntityTypeSource()
        .type("entity-type")
        .alias("simple_entity_type1")
        .id(SIMPLE_ENTITY_TYPE_ID.toString())
        .join(new EntityTypeSourceJoin()
          .type("LEFT JOIN")
          .joinTo("source2")
          .condition(":this.field1 = :that.field3")
        ),
      new EntityTypeSource()
        .type("entity-type")
        .alias("simple_entity_type2")
        .id(SIMPLE_ENTITY_TYPE_ID.toString())
        .join(new EntityTypeSourceJoin()
          .type("LEFT JOIN")
          .joinTo("source2")
          .condition(":this.field1 = :that.field4")
        )
    ));

  private static final EntityType TRIPLE_NESTED_ENTITY_TYPE = new EntityType()
    .name("triple_nested_entity_type")
    .id(TRIPLE_NESTED_ENTITY_TYPE_ID.toString())
    .columns(List.of(
      new EntityTypeColumn()
        .name("field7")
        .valueGetter(":sourceAlias.field7")
        .dataType(new StringType())
        .isIdColumn(true)
        .sourceAlias("source4"),
      new EntityTypeColumn()
        .name("field8")
        .valueGetter(":sourceAlias.field8")
        .filterValueGetter("lower(:sourceAlias.field8)")
        .dataType(new StringType())
        .sourceAlias("source4")
    ))
    .sources(List.of(
      new EntityTypeSource()
        .type("db")
        .alias("source4")
        .target("source4_target"),
      new EntityTypeSource()
        .type("entity-type")
        .alias("complex_entity_type")
        .id(COMPLEX_ENTITY_TYPE_ID.toString())
        .join(new EntityTypeSourceJoin()
          .type("LEFT JOIN")
          .joinTo("source4")
          .condition(":this.field6 = :that.field7")
        )
    ));

  private static final EntityType UNORDERED_ENTITY_TYPE = new EntityType()
    .name("simple_entity_type")
    .id(UNORDERED_ENTITY_TYPE_ID.toString())
    .columns(List.of(
        new EntityTypeColumn()
          .name("field1")
          .valueGetter(":sourceAlias.field1")
          .dataType(new StringType())
          .isIdColumn(true)
          .sourceAlias("source1")
      )
    )
    .sources(List.of(
      new EntityTypeSource()
        .type("db")
        .alias("source7")
        .target("source7_target")
        .join(new EntityTypeSourceJoin()
          .type("JOIN")
          .joinTo("source3")
          .condition(":this.field = :that.field")),
      new EntityTypeSource()
        .type("db")
        .alias("source4")
        .target("source4_target")
        .join(new EntityTypeSourceJoin()
          .type("JOIN")
          .joinTo("source3")
          .condition(":this.field = :that.field")),
      new EntityTypeSource()
        .type("db")
        .alias("source6")
        .target("source6_target")
        .join(new EntityTypeSourceJoin()
          .type("JOIN")
          .joinTo("source1")
          .condition(":this.field = :that.field")),
      new EntityTypeSource()
        .type("db")
        .alias("source5")
        .target("source5_target")
        .join(new EntityTypeSourceJoin()
          .type("JOIN")
          .joinTo("source4")
          .condition(":this.field = :that.field")),
      new EntityTypeSource()
        .type("db")
        .alias("source2")
        .target("source2_target")
        .join(new EntityTypeSourceJoin()
          .type("JOIN")
          .joinTo("source1")
          .condition(":this.field = :that.field")),
      new EntityTypeSource()
        .type("db")
        .alias("source1")
        .target("source1_target"),
      new EntityTypeSource()
        .type("db")
        .alias("source3")
        .target("source3_target")
        .join(new EntityTypeSourceJoin()
          .type("JOIN")
          .joinTo("source2")
          .condition(":this.field = :that.field"))

    ));

  @Test
  void shouldFlattenSimpleEntityType() {

    EntityType expectedEntityType = new EntityType()
      .name("simple_entity_type")
      .id(SIMPLE_ENTITY_TYPE_ID.toString())
      .columns(List.of(
        new EntityTypeColumn()
          .name("source1_field1")
          .valueGetter("\"source1\".field1")
          .dataType(new StringType())
          .sourceAlias("source1")
          .isIdColumn(true),
        new EntityTypeColumn()
          .name("source1_field2")
          .valueGetter("\"source1\".field2")
          .filterValueGetter("lower(\"source1\".field2)")
          .dataType(new StringType())
          .sourceAlias("source1")
      ))
      .sources(List.of(
        new EntityTypeSource()
          .type("db")
          .alias("source1")
          .target("source1_target")
          .useIdColumns(true)
      ));

    when(entityTypeRepository.getEntityTypeDefinition(SIMPLE_ENTITY_TYPE_ID)).thenReturn(Optional.of(SIMPLE_ENTITY_TYPE));

    EntityType actualEntityType = entityTypeFlatteningService.getFlattenedEntityType(SIMPLE_ENTITY_TYPE_ID, true)
      .orElseThrow(() -> new EntityTypeNotFoundException(SIMPLE_ENTITY_TYPE_ID));
    assertEquals(expectedEntityType, actualEntityType);
  }

  @Test
  void shouldFlattenComplexEntityType() {
    EntityType expectedEntityType = new EntityType()
      .name("complex_entity_type")
      .id(COMPLEX_ENTITY_TYPE_ID.toString())
      .columns(List.of(
        new EntityTypeColumn()
          .name("source3_field5")
          .valueGetter("\"source3\".field5")
          .dataType(new StringType())
          .sourceAlias("source3")
          .isIdColumn(true),
        new EntityTypeColumn()
          .name("source3_field6")
          .valueGetter("\"source3\".field6")
          .filterValueGetter("lower(\"source3\".field6)")
          .dataType(new StringType())
          .sourceAlias("source3"),
        new EntityTypeColumn()
          .name("source2_field3")
          .valueGetter("\"source2\".field3")
          .dataType(new StringType())
          .sourceAlias("source2")
          .isIdColumn(true),
        new EntityTypeColumn()
          .name("source2_field4")
          .valueGetter("\"source2\".field4")
          .filterValueGetter("lower(\"source2\".field4)")
          .dataType(new StringType())
          .sourceAlias("source2"),
        new EntityTypeColumn()
          .name("simple_entity_type1_field1")
          .valueGetter("\"simple_entity_type1\".field1")
          .dataType(new StringType())
          .sourceAlias("simple_entity_type1")
          .isIdColumn(false),
        new EntityTypeColumn()
          .name("simple_entity_type1_field2")
          .valueGetter("\"simple_entity_type1\".field2")
          .filterValueGetter("lower(\"simple_entity_type1\".field2)")
          .dataType(new StringType())
          .sourceAlias("simple_entity_type1")
          .isIdColumn(false),
        new EntityTypeColumn()
          .name("simple_entity_type2_field1")
          .valueGetter("\"simple_entity_type2\".field1")
          .dataType(new StringType())
          .sourceAlias("simple_entity_type2")
          .isIdColumn(false),
        new EntityTypeColumn()
          .name("simple_entity_type2_field2")
          .valueGetter("\"simple_entity_type2\".field2")
          .filterValueGetter("lower(\"simple_entity_type2\".field2)")
          .dataType(new StringType())
          .sourceAlias("simple_entity_type2")
          .isIdColumn(false)
      ))
      .sources(List.of(
        new EntityTypeSource()
          .type("db")
          .alias("source3")
          .target("source3_target")
          .join(
            new EntityTypeSourceJoin()
              .type("LEFT JOIN")
              .joinTo("source2")
              .condition(":this.field5 = :that.field3")
          )
          .useIdColumns(true),  // TODO: think about if this is right
        new EntityTypeSource()
          .type("db")
          .alias("source2")
          .target("source2_target")
          .useIdColumns(true),
        new EntityTypeSource()
          .type("db")
          .alias("simple_entity_type1")
          .target("source1_target")
          .join(new EntityTypeSourceJoin()
            .type("LEFT JOIN")
            .joinTo("source2")
            .condition(":this.field1 = :that.field3")
          )
          .flattened(true)
          .useIdColumns(false),
        new EntityTypeSource()
          .type("db")
          .alias("simple_entity_type2")
          .target("source1_target")
          .join(new EntityTypeSourceJoin()
            .type("LEFT JOIN")
            .joinTo("source2")
            .condition(":this.field1 = :that.field4")
          )
          .flattened(true)
          .useIdColumns(false)
      ));

    when(entityTypeRepository.getEntityTypeDefinition(SIMPLE_ENTITY_TYPE_ID)).thenReturn(Optional.of(SIMPLE_ENTITY_TYPE));
    when(entityTypeRepository.getEntityTypeDefinition(COMPLEX_ENTITY_TYPE_ID)).thenReturn(Optional.of(COMPLEX_ENTITY_TYPE));

    EntityType actualEntityType = entityTypeFlatteningService.getFlattenedEntityType(COMPLEX_ENTITY_TYPE_ID, true)
      .orElseThrow(() -> new EntityTypeNotFoundException(COMPLEX_ENTITY_TYPE_ID));
    assertEquals(expectedEntityType, actualEntityType);
  }

  // TODO: current problem: we can join to an entity-type source, but what if that entity-type source consists of multiple db sources?
  //  How can we know which source to join to?
  @Test
  void shouldFlattenTripleNestedEntityType() {
    EntityType expectedEntityType = new EntityType()
      .name("triple_nested_entity_type")
      .id(TRIPLE_NESTED_ENTITY_TYPE_ID.toString())
      .columns(List.of(
        new EntityTypeColumn()
          .name("source4_field7")
          .valueGetter("\"source4\".field7")
          .dataType(new StringType())
          .sourceAlias("source4")
          .isIdColumn(true),
        new EntityTypeColumn()
          .name("source4_field8")
          .valueGetter("\"source4\".field8")
          .filterValueGetter("lower(\"source4\".field8)")
          .dataType(new StringType())
          .sourceAlias("source4"),
        new EntityTypeColumn()
          .name("complex_entity_type_source2_field3")
          .valueGetter("\"complex_entity_type_source2\".field3")
          .dataType(new StringType())
          .sourceAlias("complex_entity_type_source2")
          .isIdColumn(false),
        new EntityTypeColumn()
          .name("complex_entity_type_source2_field4")
          .valueGetter("\"complex_entity_type_source2\".field4")
          .filterValueGetter("lower(\"complex_entity_type_source2\".field4)")
          .dataType(new StringType())
          .sourceAlias("complex_entity_type_source2")
          .isIdColumn(false),
        new EntityTypeColumn()
          .name("complex_entity_type_source3_field5")
          .valueGetter("\"complex_entity_type_source3\".field5")
          .dataType(new StringType())
          .sourceAlias("complex_entity_type_source3")
          .isIdColumn(false),
        new EntityTypeColumn()
          .name("complex_entity_type_source3_field6")
          .valueGetter("\"complex_entity_type_source3\".field6")
          .filterValueGetter("lower(\"complex_entity_type_source3\".field6)")
          .dataType(new StringType())
          .sourceAlias("complex_entity_type_source3")
          .isIdColumn(false),
        new EntityTypeColumn()
          .name("simple_entity_type1_field1")
          .valueGetter("\"simple_entity_type1\".field1")
          .dataType(new StringType())
          .sourceAlias("simple_entity_type1")
          .isIdColumn(false),
        new EntityTypeColumn()
          .name("simple_entity_type1_field2")
          .valueGetter("\"simple_entity_type1\".field2")
          .filterValueGetter("lower(\"simple_entity_type1\".field2)")
          .dataType(new StringType())
          .sourceAlias("simple_entity_type1")
          .isIdColumn(false),
        new EntityTypeColumn()
          .name("simple_entity_type2_field1")
          .valueGetter("\"simple_entity_type2\".field1")
          .dataType(new StringType())
          .sourceAlias("simple_entity_type2")
          .isIdColumn(false),
        new EntityTypeColumn()
          .name("simple_entity_type2_field2")
          .valueGetter("\"simple_entity_type2\".field2")
          .filterValueGetter("lower(\"simple_entity_type2\".field2)")
          .dataType(new StringType())
          .sourceAlias("simple_entity_type2")
          .isIdColumn(false)
      ))
      .sources(List.of(
        new EntityTypeSource()
          .type("db")
          .alias("source4")
          .target("source4_target"),
        new EntityTypeSource()
          .type("db")
          .alias("complex_entity_type_source2")
          .target("source2_target"),
        new EntityTypeSource()
          .type("db")
          .alias("complex_entity_type_source3")
          .target("source3_target")
          .join(
            new EntityTypeSourceJoin()
              .type("LEFT JOIN")
              .joinTo("source2")
              .condition(":this.field5 = :that.field3")
          ),
        new EntityTypeSource()
          .type("db")
          .alias("simple_entity_type1")
          .target("source1_target")
          .join(new EntityTypeSourceJoin()
            .type("LEFT JOIN")
            .joinTo("source2")
            .condition(":this.field1 = :that.field3")
          )
          .flattened(true),
        new EntityTypeSource()
          .type("db")
          .alias("simple_entity_type2")
          .target("source1_target")
          .join(new EntityTypeSourceJoin()
            .type("LEFT JOIN")
            .joinTo("source2")
            .condition(":this.field1 = :that.field4")
          )
          .flattened(true)
      ));

    when(entityTypeRepository.getEntityTypeDefinition(SIMPLE_ENTITY_TYPE_ID)).thenReturn(Optional.of(SIMPLE_ENTITY_TYPE));
    when(entityTypeRepository.getEntityTypeDefinition(COMPLEX_ENTITY_TYPE_ID)).thenReturn(Optional.of(COMPLEX_ENTITY_TYPE));
    when(entityTypeRepository.getEntityTypeDefinition(TRIPLE_NESTED_ENTITY_TYPE_ID)).thenReturn(Optional.of(TRIPLE_NESTED_ENTITY_TYPE));

    EntityType actualEntityType = entityTypeFlatteningService.getFlattenedEntityType(TRIPLE_NESTED_ENTITY_TYPE_ID, true)
      .orElseThrow(() -> new EntityTypeNotFoundException(TRIPLE_NESTED_ENTITY_TYPE_ID));
    assertEquals(expectedEntityType, actualEntityType);
  }

  @Test
  void shouldGetJoinClause() {
    String expectedJoinClause = "source2_target \"source2\" LEFT JOIN source3_target \"source3\" ON \"source3\".field5 = \"source2\".field3 LEFT JOIN source1_target \"simple_entity_type1\" ON \"simple_entity_type1\".field1 = \"source2\".field3 LEFT JOIN source1_target \"simple_entity_type2\" ON \"simple_entity_type2\".field1 = \"source2\".field4";


    when(entityTypeRepository.getEntityTypeDefinition(SIMPLE_ENTITY_TYPE_ID)).thenReturn(Optional.of(SIMPLE_ENTITY_TYPE));
    when(entityTypeRepository.getEntityTypeDefinition(COMPLEX_ENTITY_TYPE_ID)).thenReturn(Optional.of(COMPLEX_ENTITY_TYPE));

    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(COMPLEX_ENTITY_TYPE_ID, true)
      .orElseThrow(() -> new EntityTypeNotFoundException(COMPLEX_ENTITY_TYPE_ID));
    String actualJoinClause = entityTypeFlatteningService.getJoinClause(entityType);
    assertEquals(expectedJoinClause, actualJoinClause);

  }

  @Test
  void shouldReorderSourcesToMakeValidJoinClause() {
    String expectedJoinClause = "source1_target \"source1\" JOIN source2_target \"source2\" ON \"source2\".field = \"source1\".field JOIN source3_target \"source3\" ON \"source3\".field = \"source2\".field JOIN source7_target \"source7\" ON \"source7\".field = \"source3\".field JOIN source4_target \"source4\" ON \"source4\".field = \"source3\".field JOIN source6_target \"source6\" ON \"source6\".field = \"source1\".field JOIN source5_target \"source5\" ON \"source5\".field = \"source4\".field";


    when(entityTypeRepository.getEntityTypeDefinition(UNORDERED_ENTITY_TYPE_ID)).thenReturn(Optional.of(UNORDERED_ENTITY_TYPE));

    EntityType entityType = entityTypeFlatteningService.getFlattenedEntityType(UNORDERED_ENTITY_TYPE_ID, true)
      .orElseThrow(() -> new EntityTypeNotFoundException(COMPLEX_ENTITY_TYPE_ID));
    String actualJoinClause = entityTypeFlatteningService.getJoinClause(entityType);
    assertEquals(expectedJoinClause, actualJoinClause);
  }
}
