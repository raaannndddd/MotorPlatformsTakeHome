package com.motorplatforms.infra.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wraps a handler so a redelivered job (same id) is skipped once it has succeeded. With
 * at-least-once delivery this is what stops an SMS going out twice.
 */
@Component
public class IdempotentConsumer {

  private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

  private final ProcessedJobRepository processed;

  IdempotentConsumer(ProcessedJobRepository processed) {
    this.processed = processed;
  }

  public JobHandler once(JobHandler handler) {
    return job -> {
      if (processed.existsById(job.id())) {
        log.info("Skipping job {} ({}): already processed", job.id(), job.type());
        return;
      }
      handler.handle(job);
      processed.save(new ProcessedJob(job.id(), job.type()));
    };
  }
}
