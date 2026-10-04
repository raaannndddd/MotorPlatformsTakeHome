package com.motorplatforms.infra.queue;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MOCK. In-process queue with retry, exponential backoff and a dead-letter list.
 *
 * <p>A handler that throws, or does not return within the ack timeout, has failed. A failed job is
 * retried up to {@code maxRetries} times, then moved to the DLQ and logged.
 *
 * <p>Production: RabbitMQ or SQS (visibility timeout + redrive policy to a real DLQ). Jobs here are
 * lost if the process stops.
 */
@Component
public class InMemoryJobQueue implements JobQueue {

  private static final Logger log = LoggerFactory.getLogger(InMemoryJobQueue.class);

  private final Map<String, JobHandler> handlers = new ConcurrentHashMap<>();
  private final List<Job> deadLetters = new CopyOnWriteArrayList<>();
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final int maxRetries;
  private final Duration baseBackoff;
  private final Duration ackTimeout;

  public InMemoryJobQueue() {
    this(3, Duration.ofMillis(500), Duration.ofSeconds(30));
  }

  public InMemoryJobQueue(int maxRetries, Duration baseBackoff, Duration ackTimeout) {
    this.maxRetries = maxRetries;
    this.baseBackoff = baseBackoff;
    this.ackTimeout = ackTimeout;
  }

  @Override
  public void publish(Job job) {
    deliver(job, 1);
  }

  @Override
  public void subscribe(String type, JobHandler handler) {
    if (handlers.putIfAbsent(type, handler) != null) {
      throw new IllegalStateException("A handler is already subscribed to " + type);
    }
  }

  /** Jobs that exhausted their retries. Production: a DLQ that is monitored and alerted on. */
  public List<Job> deadLetters() {
    return List.copyOf(deadLetters);
  }

  private void deliver(Job job, int attempt) {
    JobHandler handler = handlers.get(job.type());
    if (handler == null) {
      deadLetter(job, attempt, new IllegalStateException("No handler for " + job.type()));
      return;
    }
    CompletableFuture.runAsync(() -> run(handler, job), workers)
        .orTimeout(ackTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .whenComplete((ok, error) -> onComplete(job, attempt, error));
  }

  private void onComplete(Job job, int attempt, Throwable error) {
    if (error == null) {
      return;
    }
    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
    if (attempt > maxRetries) {
      deadLetter(job, attempt, cause);
      return;
    }
    long delay = baseBackoff.toMillis() << (attempt - 1);
    // Log ids only: payloads can contain links and codes.
    log.warn(
        "Job {} ({}) failed on attempt {}, retrying in {}ms: {}",
        job.id(),
        job.type(),
        attempt,
        delay,
        cause.toString());
    scheduler.schedule(() -> deliver(job, attempt + 1), delay, TimeUnit.MILLISECONDS);
  }

  private void deadLetter(Job job, int attempts, Throwable cause) {
    deadLetters.add(job);
    log.error(
        "Job {} ({}) moved to DLQ after {} attempt(s): {}",
        job.id(),
        job.type(),
        attempts,
        cause.toString());
  }

  private static void run(JobHandler handler, Job job) {
    try {
      handler.handle(job);
    } catch (Exception e) {
      throw new CompletionException(e);
    }
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
    workers.shutdownNow();
  }
}
