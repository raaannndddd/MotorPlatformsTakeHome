package com.motorplatforms.service;

import com.motorplatforms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Creates the first admin. Runs only under the "seed" profile ({@code ./gradlew seedAdmin}). */
@Component
@Profile("seed")
public class SeedAdminRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(SeedAdminRunner.class);

  private final UserService userService;
  private final UserRepository users;
  private final String email;
  private final String password;

  SeedAdminRunner(
      UserService userService,
      UserRepository users,
      @Value("${seed.admin-email}") String email,
      @Value("${seed.admin-password}") String password) {
    this.userService = userService;
    this.users = users;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(String... args) {
    if (users.existsByEmail(UserService.normaliseEmail(email))) {
      log.info("Admin already exists, nothing to seed.");
      return;
    }
    if (password.length() < 12) {
      throw new IllegalStateException("SEED_ADMIN_PASSWORD must be at least 12 characters.");
    }
    userService.create(email, password);
    log.info("Seeded the first admin.");
  }
}
