package com.motorplatforms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DashboardTest extends InspectionTestSupport {

  @Autowired EntityManagerFactory emf;

  @Test
  void listsInspectionsWithTheirClientInOneQuery() throws Exception {
    for (int i = 0; i < 5; i++) {
      String otherClient = createClient(admin); // a distinct client per row exposes N+1
      mvc.perform(jsonPost("/api/inspections", Map.of("clientId", otherClient)).cookie(admin));
    }
    // Let the background SMS jobs finish so their queries are not counted.
    await()
        .until(
            () -> jdbc.queryForObject("SELECT count(*) FROM processed_jobs", Integer.class) == 5);
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();

    mvc.perform(get("/api/inspections").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].clientName").value("Jane Citizen"));

    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  void filtersByStatus() throws Exception {
    submittedInspection();
    sendLink();

    mvc.perform(get("/api/inspections?status=SUBMITTED").cookie(admin))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    mvc.perform(get("/api/inspections").cookie(admin)).andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void showsTheSubmittedForm() throws Exception {
    String id = submittedInspection();

    mvc.perform(get("/api/inspections/" + id).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inspection.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.mileage").value(84_000))
        .andExpect(jsonPath("$.conditionNotes").value("Scratch"));
  }
}
