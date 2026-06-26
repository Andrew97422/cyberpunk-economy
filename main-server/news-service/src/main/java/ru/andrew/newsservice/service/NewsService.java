package ru.andrew.newsservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.newsservice.dto.CreateNewsRequest;
import ru.andrew.newsservice.dto.NewsResponse;
import ru.andrew.newsservice.dto.UpdateNewsRequest;
import ru.andrew.newsservice.entity.NewsPost;
import ru.andrew.newsservice.entity.NewsStatus;
import ru.andrew.newsservice.exception.BadRequestException;
import ru.andrew.newsservice.exception.NotFoundException;
import ru.andrew.newsservice.exception.UnauthorizedException;
import ru.andrew.newsservice.repository.NewsPostRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NewsService {

    private static final Set<String> ADMIN_BANKER = Set.of("ADMIN", "BANKER");

    private final NewsPostRepository repository;
    private final NewsEventPublisher eventPublisher;

    @Transactional
    public NewsResponse create(Long actorId, String actorRole, String authorPublicName, CreateNewsRequest request) {
        requireAdminOrBanker(actorRole);
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BadRequestException("title is required");
        }
        if (request.getBody() == null || request.getBody().isBlank()) {
            throw new BadRequestException("body is required");
        }

        NewsPost post = new NewsPost();
        post.setTitle(request.getTitle().trim());
        post.setSummary(blankToNull(request.getSummary()));
        post.setBody(request.getBody());
        post.setCategory(blankToNull(request.getCategory()));
        post.setCoverImageUrl(blankToNull(request.getCoverImageUrl()));
        post.setGalleryUrls(cleanUrls(request.getGalleryUrls()));
        post.setPinned(Boolean.TRUE.equals(request.getPinned()));
        post.setPublishAt(request.getPublishAt());
        post.setAuthorAccountId(actorId);
        post.setAuthorPublicName(authorPublicName);

        NewsStatus status = parseStatusOrDefault(request.getStatus(), NewsStatus.DRAFT);
        post.setStatus(status);
        if (status == NewsStatus.PUBLISHED) {
            post.setPublishedAt(Instant.now());
        }

        NewsPost saved = repository.save(post);
        eventPublisher.publishCreated(saved);
        if (saved.getStatus() == NewsStatus.PUBLISHED) {
            eventPublisher.publishPublished(saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public NewsResponse update(String actorRole, Long postId, UpdateNewsRequest request) {
        requireAdminOrBanker(actorRole);
        NewsPost post = getById(postId);
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            post.setTitle(request.getTitle().trim());
        }
        if (request.getSummary() != null) {
            post.setSummary(blankToNull(request.getSummary()));
        }
        if (request.getBody() != null && !request.getBody().isBlank()) {
            post.setBody(request.getBody());
        }
        if (request.getCategory() != null) {
            post.setCategory(blankToNull(request.getCategory()));
        }
        if (request.getCoverImageUrl() != null) {
            post.setCoverImageUrl(blankToNull(request.getCoverImageUrl()));
        }
        if (request.getGalleryUrls() != null) {
            post.setGalleryUrls(cleanUrls(request.getGalleryUrls()));
        }
        if (request.getPublishAt() != null) {
            post.setPublishAt(request.getPublishAt());
        }
        NewsPost saved = repository.save(post);
        eventPublisher.publishUpdated(saved);
        return toResponse(saved);
    }

    @Transactional
    public NewsResponse changeStatus(String actorRole, Long postId, NewsStatus newStatus) {
        requireAdminOrBanker(actorRole);
        if (newStatus == null) {
            throw new BadRequestException("status is required");
        }
        NewsPost post = getById(postId);
        NewsStatus previous = post.getStatus();
        post.setStatus(newStatus);
        if (newStatus == NewsStatus.PUBLISHED && post.getPublishedAt() == null) {
            post.setPublishedAt(Instant.now());
        }
        NewsPost saved = repository.save(post);

        if (previous != newStatus && newStatus == NewsStatus.PUBLISHED) {
            eventPublisher.publishPublished(saved);
        } else if (previous != newStatus && newStatus == NewsStatus.ARCHIVED) {
            eventPublisher.publishArchived(saved);
        } else {
            eventPublisher.publishUpdated(saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public NewsResponse setPinned(String actorRole, Long postId, boolean pinned) {
        requireAdminOrBanker(actorRole);
        NewsPost post = getById(postId);
        post.setPinned(pinned);
        NewsPost saved = repository.save(post);
        eventPublisher.publishUpdated(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public NewsResponse get(String actorRole, Long postId) {
        NewsPost post = getById(postId);
        if (!isOperator(actorRole) && !isLive(post)) {
            throw new NotFoundException("News post not found: " + postId);
        }
        return toResponse(post);
    }

    @Transactional(readOnly = true)
    public Page<NewsResponse> list(String actorRole, String status, String category, String search, Pageable pageable) {
        // Empty string = "no filter" (avoids null → lower(bytea) on Postgres).
        String categoryFilter = category == null ? "" : category.trim();
        String searchFilter = search == null ? "" : search.trim();
        if (isOperator(actorRole)) {
            return repository.searchAll(parseStatusOrNull(status), categoryFilter, searchFilter, pageable)
                    .map(this::toResponse);
        }
        return repository.searchLive(Instant.now(), categoryFilter, searchFilter, pageable)
                .map(this::toResponse);
    }

    private boolean isLive(NewsPost post) {
        return post.getStatus() == NewsStatus.PUBLISHED
                && (post.getPublishAt() == null || !post.getPublishAt().isAfter(Instant.now()));
    }

    private NewsPost getById(Long id) {
        if (id == null) throw new BadRequestException("newsId is required");
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("News post not found: " + id));
    }

    private NewsResponse toResponse(NewsPost n) {
        return NewsResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .summary(n.getSummary())
                .body(n.getBody())
                .category(n.getCategory())
                .coverImageUrl(n.getCoverImageUrl())
                .galleryUrls(n.getGalleryUrls() == null ? List.of() : n.getGalleryUrls())
                .pinned(n.isPinned())
                .status(n.getStatus().name())
                .publishAt(n.getPublishAt())
                .publishedAt(n.getPublishedAt())
                .authorAccountId(n.getAuthorAccountId())
                .authorPublicName(n.getAuthorPublicName())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }

    private boolean isOperator(String role) {
        return "ADMIN".equals(role) || "BANKER".equals(role) || "DEVELOPER".equals(role);
    }

    private void requireAdminOrBanker(String role) {
        if (!ADMIN_BANKER.contains(role)) {
            throw new UnauthorizedException("Admin or banker role required");
        }
    }

    private NewsStatus parseStatusOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return NewsStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private NewsStatus parseStatusOrDefault(String value, NewsStatus fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return NewsStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid status: " + value);
        }
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Drop null/blank entries; cap to a sane gallery size. */
    private List<String> cleanUrls(List<String> urls) {
        if (urls == null) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String u : urls) {
            if (u != null && !u.isBlank()) out.add(u.trim());
            if (out.size() >= 20) break;
        }
        return out;
    }
}
