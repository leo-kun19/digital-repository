package com.example.thesisrepo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.supervisors")
public class SupervisorDirectoryProperties {

  private List<SupervisorEntry> entries = new ArrayList<>();

  @Data
  public static class SupervisorEntry {
    private String email;
    private String name;
    private String faculty;
    private String studyProgram;
    private boolean active = true;
  }
}
