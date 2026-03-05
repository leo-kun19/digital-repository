package com.example.thesisrepo.web.dto;

import java.util.List;

public record LecturerStudentGroupDto(
  Long studentUserId,
  String studentEmail,
  String studentName,
  String studentIdNumber,
  String faculty,
  String program,
  List<LecturerCaseWorkItemDto> cases
) {
}
