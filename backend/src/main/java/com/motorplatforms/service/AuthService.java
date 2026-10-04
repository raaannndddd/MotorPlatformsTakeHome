package com.motorplatforms.service;

import com.motorplatforms.common.ApiException;
import com.motorplatforms.model.User;
import com.motorplatforms.repository.UserRepository;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  // Checked when the email is unknown, so both failure paths take the same time.
  private final String dummyHash;

  public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.dummyHash = passwordEncoder.encode("not-a-real-password");
  }

  public User login(String email, String password) {
    Optional<User> user = users.findByEmail(UserService.normaliseEmail(email));
    String hash = user.map(User::getPasswordHash).orElse(dummyHash);
    if (!passwordEncoder.matches(password, hash) || user.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
    }
    return user.get();
  }
}
