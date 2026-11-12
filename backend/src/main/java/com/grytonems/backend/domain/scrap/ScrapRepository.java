package com.grytonems.backend.domain.scrap;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    /** SCRP-01 해제. 없으면 0 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Scrap s where s.user.id = :userId and s.post.id = :postId")
    int deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    /** 목록·상세의 북마크 표시용: 이 글들 중 내가 스크랩한 글 id */
    @Query("select s.post.id from Scrap s where s.user.id = :userId and s.post.id in :postIds")
    List<Long> findScrappedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    /** SCRP-02 내 스크랩: 최신 스크랩순, 커서는 직전 페이지 마지막 스크랩 id. 삭제·숨김 글은 자동 제외 */
    @Query("""
            select s from Scrap s
            join fetch s.post p
            where s.user.id = :userId and p.deletedAt is null and p.hidden = false
              and (:cursor is null or s.id < :cursor)
            order by s.id desc
            """)
    List<Scrap> findMine(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);

    /** 스크랩 글 마감 D-1 알림 대상 */
    @Query("select s.user.id from Scrap s where s.post.id = :postId")
    List<Long> findUserIdsByPostId(@Param("postId") Long postId);
}
