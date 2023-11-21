package org.folio.fqm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.fql.model.AndCondition;
import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.Fql;
import org.folio.fqm.repository.EntityTypeRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeDefaultSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DerivedTableIdentificationServiceTest {

  private static final String TENANT_ID = "tenant-01";
  private static final String DERIVED_TABLE_NAME = "derived-table-01";

  private EntityTypeRepository entityTypeRepository;
  private DerivedTableIdentificationService derivedTableIdentifier;

  @BeforeEach
  public void setup() {
    entityTypeRepository = mock(EntityTypeRepository.class);
    derivedTableIdentifier =
      new DerivedTableIdentificationService(entityTypeRepository);
  }

  @Test
  @DisplayName(
    "Main entity's table should be returned when sub entity types do not exist"
  )
  void testWhenSubEntityTypesDoNotExist() {
    EntityType entityWithoutSubTypes = TestDataFixture.getEntityDefinition();
    when(
      entityTypeRepository.getDerivedTableName(
        TENANT_ID,
        UUID.fromString(entityWithoutSubTypes.getId())
      )
    )
      .thenReturn(Optional.of(DERIVED_TABLE_NAME));

    String actual = derivedTableIdentifier.getDerivedTable(
      TENANT_ID,
      entityWithoutSubTypes,
      new Fql(new EqualsCondition("field", 1)),
      false
    );

    assertEquals(DERIVED_TABLE_NAME, actual);
  }

  @Test
  @DisplayName(
    "Sub entity's table should be returned when it satisfies the query"
  )
  void testWhenSubEntitySatisfiesQuery() {
    EntityType sub = TestDataFixture.getEntityTypeDefinition(
      "main",
      List.of(TestDataFixture.column1(), TestDataFixture.column2()),
      List.of(),
      List.of()
    );
    EntityType main = TestDataFixture.getEntityTypeDefinition(
      "main",
      List.of(
        TestDataFixture.column1(),
        TestDataFixture.column2(),
        TestDataFixture.column3()
      ),
      List.of(),
      List.of(UUID.fromString(sub.getId()))
    );

    when(
      entityTypeRepository.getEntityTypeDefinition(
        TENANT_ID,
        UUID.fromString(sub.getId())
      )
    )
      .thenReturn(Optional.of(sub));
    when(
      entityTypeRepository.getDerivedTableName(
        TENANT_ID,
        UUID.fromString(sub.getId())
      )
    )
      .thenReturn(Optional.of(DERIVED_TABLE_NAME));

    Fql fql = new Fql(
      new AndCondition(
        List.of(
          new EqualsCondition(TestDataFixture.column1().getName(), 1),
          new EqualsCondition(TestDataFixture.column2().getName(), "foo")
        )
      )
    );
    String actual = derivedTableIdentifier.getDerivedTable(
      TENANT_ID,
      main,
      fql,
      false
    );
    assertEquals(DERIVED_TABLE_NAME, actual);
  }

  @Test
  @DisplayName(
    "Main entity's table should be returned when sub entity do not satisfies the query"
  )
  void testWhenSubEntityDoNotSatisfyQuery() {
    EntityType sub = TestDataFixture.getEntityTypeDefinition(
      "main",
      List.of(TestDataFixture.column1(), TestDataFixture.column2()),
      List.of(),
      List.of()
    );
    EntityType main = TestDataFixture.getEntityTypeDefinition(
      "main",
      List.of(
        TestDataFixture.column1(),
        TestDataFixture.column2(),
        TestDataFixture.column3()
      ),
      List.of(),
      List.of(UUID.fromString(sub.getId()))
    );

    when(
      entityTypeRepository.getEntityTypeDefinition(
        TENANT_ID,
        UUID.fromString(sub.getId())
      )
    )
      .thenReturn(Optional.of(sub));
    when(
      entityTypeRepository.getDerivedTableName(
        TENANT_ID,
        UUID.fromString(main.getId())
      )
    )
      .thenReturn(Optional.of(DERIVED_TABLE_NAME));

    Fql fql = new Fql(
      new AndCondition(
        List.of(
          new EqualsCondition(TestDataFixture.column1().getName(), 1),
          new EqualsCondition(TestDataFixture.column3().getName(), "foo")
        )
      )
    );
    String actual = derivedTableIdentifier.getDerivedTable(
      TENANT_ID,
      main,
      fql,
      false
    );
    assertEquals(DERIVED_TABLE_NAME, actual);
  }

  @Test
  @DisplayName(
    "Main entity's table should be returned when sub entity do not include the sort fields"
  )
  void testWhenSortFieldsNotPresentInSubEntityType() {
    EntityTypeDefaultSort col3Sort = new EntityTypeDefaultSort()
      .columnName(TestDataFixture.column3().getName())
      .direction(EntityTypeDefaultSort.DirectionEnum.ASC);
    EntityType sub = TestDataFixture.getEntityTypeDefinition(
      "main",
      List.of(TestDataFixture.column1(), TestDataFixture.column2()),
      List.of(),
      List.of()
    );
    EntityType main = TestDataFixture.getEntityTypeDefinition(
      "main",
      List.of(
        TestDataFixture.column1(),
        TestDataFixture.column2(),
        TestDataFixture.column3()
      ),
      List.of(col3Sort),
      List.of(UUID.fromString(sub.getId()))
    );

    when(
      entityTypeRepository.getEntityTypeDefinition(
        TENANT_ID,
        UUID.fromString(sub.getId())
      )
    )
      .thenReturn(Optional.of(sub));
    when(
      entityTypeRepository.getDerivedTableName(
        TENANT_ID,
        UUID.fromString(main.getId())
      )
    )
      .thenReturn(Optional.of(DERIVED_TABLE_NAME));

    Fql fql = new Fql(
      new EqualsCondition(TestDataFixture.column1().getName(), 1)
    );
    String actual = derivedTableIdentifier.getDerivedTable(
      TENANT_ID,
      main,
      fql,
      true
    );
    assertEquals(DERIVED_TABLE_NAME, actual);
  }

  @Test
  @DisplayName(
    "Sub entity's table should be returned when sub entity do not include the sort fields, however 'shouldSort' param is false"
  )
  void testWhenSortParamIsFalseInRequest() {
    EntityTypeDefaultSort col3Sort = new EntityTypeDefaultSort()
      .columnName(TestDataFixture.column3().getName())
      .direction(EntityTypeDefaultSort.DirectionEnum.ASC);
    EntityType sub = TestDataFixture.getEntityTypeDefinition(
      "main",
      List.of(TestDataFixture.column1(), TestDataFixture.column2()),
      List.of(),
      List.of()
    );
    EntityType main = TestDataFixture.getEntityTypeDefinition(
      "main",
      List.of(
        TestDataFixture.column1(),
        TestDataFixture.column2(),
        TestDataFixture.column3()
      ),
      List.of(col3Sort),
      List.of(UUID.fromString(sub.getId()))
    );

    when(
      entityTypeRepository.getEntityTypeDefinition(
        TENANT_ID,
        UUID.fromString(sub.getId())
      )
    )
      .thenReturn(Optional.of(sub));
    when(
      entityTypeRepository.getDerivedTableName(
        TENANT_ID,
        UUID.fromString(sub.getId())
      )
    )
      .thenReturn(Optional.of(DERIVED_TABLE_NAME));

    Fql fql = new Fql(
      new EqualsCondition(TestDataFixture.column1().getName(), 1)
    );
    String actual = derivedTableIdentifier.getDerivedTable(
      TENANT_ID,
      main,
      fql,
      false
    );
    assertEquals(DERIVED_TABLE_NAME, actual);
  }
}
