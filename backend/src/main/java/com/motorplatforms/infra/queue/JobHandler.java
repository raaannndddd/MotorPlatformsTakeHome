package com.motorplatforms.infra.queue;

/** Returning normally acks the job. Throwing, or not returning in time, is a failure. */
@FunctionalInterface
public interface JobHandler {

  void handle(Job job) throws Exception;
}
