package com.motorplatforms.notifications;

import com.motorplatforms.clients.ClientService;
import com.motorplatforms.infra.queue.IdempotentConsumer;
import com.motorplatforms.infra.queue.Job;
import com.motorplatforms.infra.queue.JobQueue;
import com.motorplatforms.infra.sms.SmsSender;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Consumes sms.send jobs. Each job id is sent at most once. */
@Component
class SmsJobHandler {

  private final ClientService clients;
  private final SmsSender sms;

  SmsJobHandler(
      JobQueue queue, IdempotentConsumer idempotent, ClientService clients, SmsSender sms) {
    this.clients = clients;
    this.sms = sms;
    queue.subscribe(SmsNotifier.JOB_TYPE, idempotent.once(this::handle));
  }

  private void handle(Job job) {
    String phone = clients.get(UUID.fromString(job.payload().get("clientId"))).getPhone();
    sms.send(phone, job.payload().get("body"));
  }
}
