package com.example.thesisrepo.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void studentLoginSucceeds() throws Exception {
    performLogin("student1@example.com", "Student123!")
      .andExpect(jsonPath("$.role").value("STUDENT"));
  }

  @Test
  void lecturerLoginSucceeds() throws Exception {
    performLogin("lecturer1@example.com", "Lecturer123!")
      .andExpect(jsonPath("$.role").value("LECTURER"));
  }

  @Test
  void adminLoginSucceeds() throws Exception {
    performLogin("admin@example.com", "Admin123!")
      .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  private ResultActions performLogin(String username, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .accept(MediaType.APPLICATION_JSON)
          .param("username", username)
          .param("password", password)
      )
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }
}
