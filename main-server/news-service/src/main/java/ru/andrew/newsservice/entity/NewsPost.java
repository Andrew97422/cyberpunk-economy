package ru.andrew.newsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "news_posts")
@Getter
@Setter
public class NewsPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "body", columnDefinition = "text", nullable = false)
    private String body;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    /** URLs of gallery images, stored as a JSON array (jsonb). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gallery_urls", columnDefinition = "jsonb")
    private List<String> galleryUrls = new ArrayList<>();

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NewsStatus status;

    /** Optional scheduled go-live time; players see the post only once this has passed. */
    @Column(name = "publish_at")
    private Instant publishAt;

    /** When the post was first moved to PUBLISHED. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "author_account_id")
    private Long authorAccountId;

    @Column(name = "author_public_name", length = 150)
    private String authorPublicName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = NewsStatus.DRAFT;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
