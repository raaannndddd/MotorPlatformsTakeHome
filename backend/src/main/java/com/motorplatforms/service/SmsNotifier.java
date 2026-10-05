package com.motorplatforms.service;

import com.motorplatforms.infra.queue.Job;
import com.motorplatforms.infra.queue.JobQueue;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Queues an SMS to a client. The job carries the client id, not the phone number, so PII stays out
 * of the queue; the consumer looks the number up.
 */
@Component
public class SmsNotifier {

  static final String JOB_TYPE = "sms.send";

  private final JobQueue queue;

  SmsNotifier(JobQueue queue) {
    this.queue = queue;
  }

  /**
   * Publishes after the current transaction commits, so a rolled-back change never sends an SMS.
   * The gap between commit and publish is what a transactional outbox would close in production.
   */
  public void send(UUID clientId, String body) {
    Job job = Job.of(JOB_TYPE, Map.of("clientId", clientId.toString(), "body", body));
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      queue.publish(job);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            queue.publish(job);
          }
        });
  }
}
