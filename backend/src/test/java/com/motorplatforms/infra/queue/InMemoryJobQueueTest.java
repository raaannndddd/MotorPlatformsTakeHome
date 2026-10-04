package com.motorplatforms.infra.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InMemoryJobQueueTest {

  private final InMemoryJobQueue queue =
      new InMemoryJobQueue(3, Duration.ofMillis(10), Duration.ofMillis(200));
  private final AtomicInteger attempts = new AtomicInteger();
  private final Job job = Job.of("test", Map.of());

  @AfterEach
  void stop() {
    queue.shutdown();
  }

  @Test
  void deliversOnceWhenTheHandlerSucceeds() {
    queue.subscribe("test", j -> attempts.incrementAndGet());

    queue.publish(job);

    await().until(() -> attempts.get() == 1);
    assertThat(queue.deadLetters()).isEmpty();
  }

  @Test
  void retriesAFailingHandlerUntilItSucceeds() {
    queue.subscribe(
        "test",
        j -> {
          if (attempts.incrementAndGet() < 3) {
            throw new IllegalStateException("provider down");
          }
        });

    queue.publish(job);

    await().until(() -> attempts.get() == 3);
    await().during(Duration.ofMillis(200)).until(() -> attempts.get() == 3);
    assertThat(queue.deadLetters()).isEmpty();
  }

  @Test
  void movesToTheDlqAfterThreeRetries() {
    queue.subscribe(
        "test",
        j -> {
          attempts.incrementAndGet();
          throw new IllegalStateException("provider down");
        });

    queue.publish(job);

    await().until(() -> queue.deadLetters().contains(job));
    assertThat(attempts.get()).isEqualTo(4); // first try + 3 retries
  }

  @Test
  void treatsAMissingAckAsAFailure() {
    queue.subscribe(
        "test",
        j -> {
          attempts.incrementAndGet();
          Thread.sleep(Duration.ofSeconds(10)); // never acks within the timeout
        });

    queue.publish(job);

    await().atMost(Duration.ofSeconds(5)).until(() -> queue.deadLetters().contains(job));
    assertThat(attempts.get()).isEqualTo(4);
  }

  @Test
  void deadLettersAJobWithNoHandler() {
    queue.publish(job);

    assertThat(queue.deadLetters()).containsExactly(job);
  }
}
