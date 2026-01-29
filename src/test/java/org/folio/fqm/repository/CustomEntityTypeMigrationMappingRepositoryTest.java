package org.folio.fqm.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.exception.UncheckedException;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertSetMoreStep;
import org.jooq.InsertSetStep;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.SelectSelectStep;
import org.jooq.Table;
import org.jooq.UpdateSetFirstStep;
import org.jooq.UpdateSetMoreStep;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class CustomEntityTypeMigrationMappingRepositoryTest {

  @Mock
  DSLContext jooqContext;

  CustomEntityTypeMigrationMappingRepository customEntityTypeMigrationMappingRepository;

  @BeforeEach
  void setup() {
    customEntityTypeMigrationMappingRepository =
      new CustomEntityTypeMigrationMappingRepository(jooqContext, jooqContext, new ObjectMapper());
  }

  @Test
  void testGetMappingsWhenAlreadyPresent() {
    mockFetchCount(1);
    mockFetch("{\"b50630f8-eb40-5f67-8b6f-b7612314e67a\": {\"something\": \"d290f1ee-6c54-4b01-90e6-d701748f0851\"}}");

    assertThat(
      customEntityTypeMigrationMappingRepository.getMappings(),
      is(
        Map.of(
          UUID.fromString("b50630f8-eb40-5f67-8b6f-b7612314e67a"),
          Map.of("something", UUID.fromString("d290f1ee-6c54-4b01-90e6-d701748f0851"))
        )
      )
    );

    verify(jooqContext, never()).insertInto(any(Table.class));
  }

  @Test
  void testGetMappingsInvalidFallback() {
    mockFetchCount(1);
    mockFetch("invalid");

    assertThat(customEntityTypeMigrationMappingRepository.getMappings(), is(Map.of()));

    verify(jooqContext, never()).insertInto(any(Table.class));
  }

  @Test
  void testGetMappingsInitializeEmpty() {
    mockFetchCount(0);
    mockInsert();
    mockFetch("{\"6dbb66a6-078c-5983-aa4d-a450a126c025\":{}}");

    assertThat(
      customEntityTypeMigrationMappingRepository.getMappings(),
      is(Map.of(UUID.fromString("6dbb66a6-078c-5983-aa4d-a450a126c025"), Map.of()))
    );
  }

  @Test
  void testUpdate() {
    mockFetchCount(1);
    mockUpdate(null);

    assertDoesNotThrow(() ->
      customEntityTypeMigrationMappingRepository.saveMappings(
        Map.of(
          UUID.fromString("b50630f8-eb40-5f67-8b6f-b7612314e67a"),
          Map.of("something", UUID.fromString("d290f1ee-6c54-4b01-90e6-d701748f0851"))
        )
      )
    );
  }

  @Test
  void testUpdateExceptional() {
    mockFetchCount(1);
    mockUpdate(new DataAccessException("nope"));

    assertThrows(
      UncheckedException.class,
      () ->
        customEntityTypeMigrationMappingRepository.saveMappings(
          Map.of(
            UUID.fromString("b50630f8-eb40-5f67-8b6f-b7612314e67a"),
            Map.of("something", UUID.fromString("d290f1ee-6c54-4b01-90e6-d701748f0851"))
          )
        )
    );
  }

  private void mockFetchCount(int count) {
    when(jooqContext.fetchCount(any(Table.class))).thenReturn(count);
  }

  private void mockFetch(String result) {
    SelectSelectStep<Record1<String>> selection = mock(SelectSelectStep.class);
    when(jooqContext.select(any(Field.class))).thenReturn(selection);
    SelectJoinStep<Record1<String>> selection2 = mock(SelectJoinStep.class);
    when(selection.from(any(Table.class))).thenReturn(selection2);
    when(selection2.fetchOne(any(Field.class))).thenReturn(result);
  }

  private void mockInsert() {
    InsertSetStep<?> insertStep = mock(InsertSetStep.class);
    when(jooqContext.insertInto(any(Table.class))).thenReturn(insertStep);
    InsertSetMoreStep<?> insertStep2 = mock(InsertSetMoreStep.class);
    when(insertStep.set(any(Field.class), any())).thenReturn(insertStep2);
    when(insertStep2.execute()).thenReturn(1);
  }

  private void mockUpdate(Exception toThrow) {
    UpdateSetFirstStep<?> updateStep = mock(UpdateSetFirstStep.class);
    when(jooqContext.update(any(Table.class))).thenReturn(updateStep);
    UpdateSetMoreStep<?> updateStep2 = mock(UpdateSetMoreStep.class);
    when(updateStep.set(any(Field.class), any())).thenReturn(updateStep2);
    if (toThrow == null) {
      when(updateStep2.execute()).thenReturn(1);
    } else {
      when(updateStep2.execute()).thenThrow(toThrow);
    }
  }
}
