package com.example.thesisrepo.publication;

import com.example.thesisrepo.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "download_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "published_item_id")
  private PublishedItem publishedItem;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false)
  private Instant downloadedAt;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "user_agent", columnDefinition = "text")
  private String userAgent;
}
