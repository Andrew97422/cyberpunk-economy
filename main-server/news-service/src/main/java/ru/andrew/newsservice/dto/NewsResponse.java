package ru.andrew.newsservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class NewsResponse {
    private Long id;
    private String title;
    private String summary;
    private String body;
    private String category;
    private String coverImageUrl;
    private List<String> galleryUrls;
    private boolean pinned;
    private String status;
    private Instant publishAt;
    private Instant publishedAt;
    private Long authorAccountId;
    private String authorPublicName;
    private Instant createdAt;
    private Instant updatedAt;
}
