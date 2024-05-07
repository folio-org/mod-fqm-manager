package org.folio.fqm.annotation;

import org.folio.fqm.aspect.EntityTypePermissionsAspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;

/**
 * Annotation used to enforce permissions for methods that interact with entity types.
 * <p>
 * The default behavior with no parameters is to expect the annotated method's first {@link UUID} parameter to be an
 * entity type ID.
 * <p>
 * If the entity type or its ID isn't directly available as a method parameter, then the value can be
 * set to the appropriate class, and a handler will need to be added to {@link EntityTypePermissionsAspect}
 * that can use the parameter to retrieve the entity type.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityTypePermissionsRequired {

  /**
   * The expected type of the parameter containing the entity type.
   * Default is {@link UUID}.
   */
  Class<?> value() default UUID.class;

  /**
   * The name of the method parameter that contains the entity type.
   * If not specified, the first parameter of the method will be used.
   */
  String parameterName() default "";

  /**
   * The type of the ID, for use when the parameter is a {@link UUID}.
   * Default is {@link IdType#ENTITY_TYPE}.
   */
  IdType idType() default IdType.ENTITY_TYPE;

  enum IdType {
    /**
     * The ID is for an FQL query.
     */
    QUERY,
    /**
     * The ID is for an entity type.
     */
    ENTITY_TYPE,
  }
}
