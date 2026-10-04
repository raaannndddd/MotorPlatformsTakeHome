package com.motorplatforms;

import static org.awaitility.Awaitility.await;

import com.motorplatforms.infra.sms.SmsSender;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test double that records every SMS instead of logging it. */
public class RecordingSmsSender implements SmsSender {

  public record Sms(String to, String body) {}

  private final List<Sms> sent = new CopyOnWriteArrayList<>();

  @Override
  public void send(String toE164, String body) {
    sent.add(new Sms(toE164, body));
  }

  public List<Sms> sent() {
    return List.copyOf(sent);
  }

  /** Waits for the queue to deliver {@code count} messages in total and returns the latest. */
  public Sms awaitCount(int count) {
    await().atMost(Duration.ofSeconds(5)).until(() -> sent.size() >= count);
    return sent.get(count - 1);
  }

  public void clear() {
    sent.clear();
  }
}
