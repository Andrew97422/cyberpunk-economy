package ru.andrew.newsservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class CreateNewsRequest {
    private String title;
    private String summary;
    private String body;
    private String category;
    private String coverImageUrl;
    private List<String> galleryUrls;
    private Boolean pinned;
    /** Optional initial status (DRAFT default, or PUBLISHED to publish immediately). */
    private String status;
    /** Optional scheduled go-live time. */
    private Instant publishAt;
}
