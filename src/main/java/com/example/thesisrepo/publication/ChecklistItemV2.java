package com.example.thesisrepo.publication;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "checklist_item_v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistItemV2 {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "template_id")
  private ChecklistTemplate template;

  @Column(nullable = false)
  private Integer orderIndex;

  private String section;

  @Column(nullable = false, columnDefinition = "text")
  private String itemText;

  @Column(columnDefinition = "text")
  private String guidanceText;

  @Column(nullable = false)
  private boolean isRequired;
}
