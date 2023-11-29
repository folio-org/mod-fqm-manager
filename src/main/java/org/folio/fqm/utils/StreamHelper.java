package org.folio.fqm.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StreamHelper {

  public static <T> Stream<List<T>> chunk(Stream<T> stream, int size) {
    if (size <= 1) {
      throw new IllegalArgumentException("Invalid size parameter: " + size);
    }

    Iterator<T> iterator = stream.iterator();
    Iterator<List<T>> listIterator = new Iterator<>() {
      public boolean hasNext() {
        return iterator.hasNext();
      }

      public List<T> next() {
        List<T> result = new ArrayList<>(size);
        for (int i = 0; i < size && iterator.hasNext(); i++) {
          result.add(iterator.next());
        }
        return result;
      }
    };
    return StreamSupport.stream(((Iterable<List<T>>) () -> listIterator).spliterator(), false)
      .onClose(stream::close);
  }
}
