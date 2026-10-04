package com.motorplatforms.clients;

import com.motorplatforms.infra.crypto.EncryptedString;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A dealership customer. name, phone and rego are PII and stored encrypted. */
@Entity
@Table(name = "clients")
public class Client {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Convert(converter = EncryptedString.class)
  private String name;

  @Column(name = "car_model")
  private String carModel;

  /** E.164, for example +61412345678. */
  @Convert(converter = EncryptedString.class)
  private String phone;

  @Convert(converter = EncryptedString.class)
  private String rego;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  protected Client() {}

  public Client(String name, String carModel, String phone, String rego) {
    update(name, carModel, phone, rego);
  }

  public final void update(String name, String carModel, String phone, String rego) {
    this.name = name;
    this.carModel = carModel;
    this.phone = phone;
    this.rego = rego;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getCarModel() {
    return carModel;
  }

  public String getPhone() {
    return phone;
  }

  public String getRego() {
    return rego;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
