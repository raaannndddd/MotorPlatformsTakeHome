package com.motorplatforms.service;

import com.motorplatforms.common.ApiException;
import com.motorplatforms.model.User;
import com.motorplatforms.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public User create(String email, String password) {
    String normalised = normaliseEmail(email);
    if (users.existsByEmail(normalised)) {
      throw ApiException.conflict("EMAIL_TAKEN", "A user with that email already exists.");
    }
    return users.save(new User(normalised, passwordEncoder.encode(password)));
  }

  @Transactional(readOnly = true)
  public List<User> list() {
    return users.findAllByOrderByCreatedAtAsc();
  }

  public static String normaliseEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
