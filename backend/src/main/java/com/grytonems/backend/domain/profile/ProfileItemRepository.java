package com.grytonems.backend.domain.profile;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileItemRepository extends JpaRepository<ProfileItem, Long> {

    List<ProfileItem> findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(Long userId);

    Optional<ProfileItem> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    long countByUserIdAndItemTypeAndDeletedAtIsNull(Long userId, ProfileItemType itemType);

    /** AUTH-09 탈퇴: 이력 항목을 모두 지웁니다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProfileItem i where i.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
