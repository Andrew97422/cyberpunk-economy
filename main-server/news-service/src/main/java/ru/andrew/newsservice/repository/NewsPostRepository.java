package ru.andrew.newsservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.andrew.newsservice.entity.NewsPost;
import ru.andrew.newsservice.entity.NewsStatus;

import java.time.Instant;

public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {

    /**
     * Operator view — all posts with optional filters. Empty-string sentinels mean "no filter"
     * so a null never reaches {@code lower(...)} (PostgreSQL would otherwise fail with
     * "function lower(bytea) does not exist"). Sorted pinned-first, then newest.
     */
    @Query("""
            select n from NewsPost n
            where (:status is null or n.status = :status)
              and (:category = '' or lower(n.category) = lower(:category))
              and (:search = ''
                   or lower(n.title) like lower(concat('%', :search, '%'))
                   or lower(n.body) like lower(concat('%', :search, '%')))
            order by n.pinned desc, coalesce(n.publishedAt, n.publishAt, n.createdAt) desc
            """)
    Page<NewsPost> searchAll(@Param("status") NewsStatus status,
                             @Param("category") String category,
                             @Param("search") String search,
                             Pageable pageable);

    /** Player view — only live posts (PUBLISHED and the scheduled time, if any, has passed). */
    @Query("""
            select n from NewsPost n
            where n.status = ru.andrew.newsservice.entity.NewsStatus.PUBLISHED
              and (n.publishAt is null or n.publishAt <= :now)
              and (:category = '' or lower(n.category) = lower(:category))
              and (:search = ''
                   or lower(n.title) like lower(concat('%', :search, '%'))
                   or lower(n.body) like lower(concat('%', :search, '%')))
            order by n.pinned desc, coalesce(n.publishedAt, n.publishAt, n.createdAt) desc
            """)
    Page<NewsPost> searchLive(@Param("now") Instant now,
                              @Param("category") String category,
                              @Param("search") String search,
                              Pageable pageable);
}
