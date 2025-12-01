package org.folio.fqm.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.folio.fqm.domain.SourceViewDefinition.SourceViewDependency;
import org.junit.jupiter.api.Test;

public class SourceViewDefinitionTest {

  private static final SourceViewDependency DEP_A = new SourceViewDependency("", "a");
  private static final SourceViewDependency DEP_B = new SourceViewDependency("", "b");
  private static final SourceViewDependency DEP_C = new SourceViewDependency("", "c");
  private static final SourceViewDependency DEP_D = new SourceViewDependency("", "d");

  @Test
  void testNullDependenciesIsAvailable() {
    assertTrue(new SourceViewDefinition("view", null, "SELECT 1", "source").isAvailable(List.of()));
  }

  @Test
  void testNoDependenciesIsAvailable() {
    assertTrue(new SourceViewDefinition("view", List.of(), "SELECT 1", "source").isAvailable(List.of()));
  }

  @Test
  void testSatisfiedDependenciesIsAvailable() {
    assertTrue(
      new SourceViewDefinition("view", List.of(DEP_C, DEP_B), "SELECT 1", "source")
        .isAvailable(List.of(DEP_A, DEP_B, DEP_C, DEP_D))
    );
  }

  @Test
  void testNotSatisfiedDependenciesIsNotAvailable() {
    assertFalse(
      new SourceViewDefinition("view", List.of(DEP_A, DEP_C), "SELECT 1", "source")
        .isAvailable(List.of(DEP_A, DEP_B, DEP_D))
    );
  }
}
