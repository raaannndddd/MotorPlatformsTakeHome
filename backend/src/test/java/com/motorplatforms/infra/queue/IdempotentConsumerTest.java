package com.motorplatforms.infra.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.motorplatforms.IntegrationTest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IdempotentConsumerTest extends IntegrationTest {

  @Autowired IdempotentConsumer idempotent;

  @Test
  void skipsAJobItHasAlreadyProcessed() throws Exception {
    var calls = new AtomicInteger();
    JobHandler handler = idempotent.once(j -> calls.incrementAndGet());
    Job job = Job.of("test", Map.of());

    handler.handle(job);
    handler.handle(job); // redelivery of the same job id

    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void doesNotMarkAFailedJobAsProcessed() throws Exception {
    var calls = new AtomicInteger();
    JobHandler handler =
        idempotent.once(
            j -> {
              if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("provider down");
              }
            });
    Job job = Job.of("test", Map.of());

    try {
      handler.handle(job);
    } catch (IllegalStateException expected) {
      // The retry below must still run.
    }
    handler.handle(job);

    assertThat(calls.get()).isEqualTo(2);
  }
}
