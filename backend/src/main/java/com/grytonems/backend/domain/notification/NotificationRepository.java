package com.grytonems.backend.domain.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 알림함 목록: since 이후(30일 보관) 알림을 최신순으로. cursor는 직전 페이지 마지막 알림 id */
    @Query("""
            select n from Notification n
            where n.user.id = :userId
              and n.createdAt >= :since
              and (:cursor is null or n.id < :cursor)
            order by n.id desc
            """)
    List<Notification> findPage(@Param("userId") Long userId, @Param("since") LocalDateTime since,
            @Param("cursor") Long cursor, Pageable pageable);

    /** 안 읽은 알림 개수 (30일 이내) */
    @Query("""
            select count(n) from Notification n
            where n.user.id = :userId and n.read = false and n.createdAt >= :since
            """)
    long countUnread(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /** 내 알림만 찾기 (남의 알림 id를 넣으면 빈 값) */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** 모두 읽음 처리. 바뀐 개수를 돌려줍니다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);
}
