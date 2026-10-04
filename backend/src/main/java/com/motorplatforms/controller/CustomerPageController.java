package com.motorplatforms.inspections;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the customer page for an SMS link. Deliberately does nothing else: SMS apps fetch links to
 * build previews, so "opened" is recorded only when the page's script calls the session API.
 */
@Controller
class CustomerPageController {

  @GetMapping("/i/{token}")
  String page() {
    return "forward:/inspection.html";
  }
}
