package com.motorplatforms.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Serves the customer page for an SMS link. Deliberately does nothing else: SMS apps fetch links to
 * build previews, so "opened" is recorded only when the page's script calls the session API.
 */
@Component
class CustomerPageController {

  private static final Resource PAGE = new ClassPathResource("static/inspection.html");

  ServerResponse page(ServerRequest request) {
    return ServerResponse.ok().contentType(MediaType.TEXT_HTML).body(PAGE);
  }
}
