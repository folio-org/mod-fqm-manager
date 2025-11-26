package org.folio.fqm.utils;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JSON5ObjectMapperFactory {

  public static ObjectMapper create() {
    // this enables all JSON5 features, except for numeric ones (hex, starting/trailing
    // decimal points, use of NaN, etc), as those are not relevant for our use
    // see: https://stackoverflow.com/questions/68312227/can-the-jackson-parser-be-used-to-parse-json5
    // full list:
    // https://fasterxml.github.io/jackson-core/javadoc/2.14/com/fasterxml/jackson/core/json/JsonReadFeature.html
    return JsonMapper
      .builder()
      // allows use of Java/C++ style comments (both '/'+'*' and '//' varieties) within parsed content.
      .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
      // some SQL statements may be cleaner this way around
      .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
      // left side of { foo: bar }, cleaner/easier to read. JS style
      .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
      // nicer diffs/etc
      .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
      // allows "escaping" newlines in regular JSON, giving proper linebreaks
      .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
      .build();
  }
}
