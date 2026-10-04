package com.motorplatforms.infra.queue;

/** A message queue with at-least-once delivery. */
public interface JobQueue {

  void publish(Job job);

  /** One handler per job type. */
  void subscribe(String type, JobHandler handler);
}
