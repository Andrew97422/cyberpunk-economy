package ru.andrew.newsservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class UpdateNewsRequest {
    private String title;
    private String summary;
    private String body;
    private String category;
    private String coverImageUrl;
    private List<String> galleryUrls;
    private Instant publishAt;
}
