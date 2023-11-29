package org.folio.fqm.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class StreamHelperTest {

  @Test
  void shouldChunkStream_sizeLessThanElementsCount() {
    Stream<Integer> originalStream = IntStream.rangeClosed(0, 11).boxed();
    Stream<List<Integer>> resultStream = StreamHelper.chunk(originalStream, 5);
    List<List<Integer>> resultList = resultStream.toList();
    assertEquals(3, resultList.size());
    assertEquals(List.of(0, 1, 2, 3, 4), resultList.get(0));
    assertEquals(List.of(5, 6, 7, 8, 9), resultList.get(1));
    assertEquals(List.of(10, 11), resultList.get(2));
  }

  @Test
  void shouldChunkStream_sizeMoreThanElementsCount() {
    Stream<Integer> originalStream = IntStream.range(0, 5).boxed();
    Stream<List<Integer>> resultStream = StreamHelper.chunk(
      originalStream,
      100
    );
    List<List<Integer>> resultList = resultStream.toList();
    assertEquals(1, resultList.size());
    assertEquals(List.of(0, 1, 2, 3, 4), resultList.get(0));
  }

  @Test
  void shouldCloseOriginalStreamWhenResultStreamClosed()
    throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Boolean> originalStreamClosed = new CompletableFuture<>();
    Stream<Integer> originalStream = IntStream
      .rangeClosed(0, 11)
      .boxed()
      .onClose(() -> originalStreamClosed.complete(true));
    Stream<List<Integer>> resultStream = StreamHelper.chunk(originalStream, 5);
    resultStream.close();
    assertEquals(true, originalStreamClosed.get(10, TimeUnit.SECONDS));
  }

  @Test
  void shouldThrowExceptionForInvalidSize() {
    var intStream1 = IntStream.rangeClosed(0, 11).boxed();
    assertThrows(
      IllegalArgumentException.class,
      () -> {
        StreamHelper.chunk(intStream1, 1);
      }
    );
    var intStream0 = IntStream.rangeClosed(0, 11).boxed();
    assertThrows(
      IllegalArgumentException.class,
      () -> {
        StreamHelper.chunk(intStream0, 0);
      }
    );
    var intStreamMinus1 = IntStream.rangeClosed(0, 11).boxed();
    assertThrows(
      IllegalArgumentException.class,
      () -> {
        StreamHelper.chunk(intStreamMinus1, -1);
      }
    );
  }

  @Test
  void shouldNotThrowExceptionForValidSize() {
    assertDoesNotThrow(() -> {
      StreamHelper.chunk(IntStream.rangeClosed(0, 11).boxed(), 2);
    });
    assertDoesNotThrow(() -> {
      StreamHelper.chunk(IntStream.rangeClosed(0, 11).boxed(), 100);
    });
    assertDoesNotThrow(() -> {
      StreamHelper.chunk(IntStream.rangeClosed(0, 11).boxed(), 1000);
    });
  }
}
