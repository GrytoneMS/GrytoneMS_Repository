package com.grytonems.backend.domain.post;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.sqm.TemporalUnit;

import com.grytonems.backend.domain.user.User;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * 모집글 목록 조건 블록 (LIST-01~04)
 * 서비스에서 필요한 블록만 and()로 이어 붙여 씁니다.
 * - and로 이은 블록끼리는 AND
 * - 블록 하나 안의 in(여러 값)은 OR
 */
public final class PostSpecs {

    private PostSpecs() {
    }

    // ===== 기본 =====

    /** 숨김·삭제되지 않은 글 (항상 적용) */
    public static Specification<Post> visible() {
        return (root, query, cb) -> cb.and(
                cb.isFalse(root.get("hidden")),
                cb.isNull(root.get("deletedAt")));
    }

    // ===== 커서 (LIST-01·02) =====

    /** 최신순 다음 페이지: id가 커서보다 작은 글 */
    public static Specification<Post> idLessThan(Long cursorId) {
        return (root, query, cb) -> cb.lessThan(root.get("id"), cursorId);
    }

    /** 마감 임박순: 마감일이 오늘 이후인 글만 */
    public static Specification<Post> deadlineOnOrAfter(LocalDate today) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("deadline"), today);
    }

    /** 마감 임박순 다음 페이지: (마감일, id)가 커서보다 뒤인 글 */
    public static Specification<Post> afterDeadlineCursor(LocalDate cursorDeadline, Long cursorId) {
        return (root, query, cb) -> cb.or(
                cb.greaterThan(root.get("deadline"), cursorDeadline),
                cb.and(cb.equal(root.get("deadline"), cursorDeadline),
                        cb.greaterThan(root.get("id"), cursorId)));
    }

    // ===== 필터 (LIST-03) =====

    /** 모집중만 보기 */
    public static Specification<Post> openOnly() {
        return (root, query, cb) -> cb.equal(root.get("status"), RecruitStatus.OPEN);
    }

    /** 모집 유형 (여러 개면 OR) */
    public static Specification<Post> categoryIn(List<PostCategory> categories) {
        return (root, query, cb) -> root.get("category").in(categories);
    }

    /** 진행 방식 (여러 개면 OR) */
    public static Specification<Post> meetingTypeIn(List<MeetingType> meetingTypes) {
        return (root, query, cb) -> root.get("meetingType").in(meetingTypes);
    }

    /** 모임 장소 (여러 개면 OR) */
    public static Specification<Post> locationCodeIn(List<LocationCode> locationCodes) {
        return (root, query, cb) -> root.get("locationCode").in(locationCodes);
    }

    /** 역할: 고른 역할 중 하나라도 있는 글 (post_roles에서 찾기) */
    public static Specification<Post> roleCodeIn(List<RoleCode> roleCodes) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<PostRole> role = sub.from(PostRole.class);
            sub.select(role.<Long>get("id"))
                    .where(cb.equal(role.get("post"), root),
                            role.get("roleCode").in(roleCodes));
            return cb.exists(sub);
        };
    }

    /** 팀장 학교: 팀장이 고른 학교 중 하나에 다니는 글 (users에서 찾기) */
    public static Specification<Post> leaderSchoolIn(List<Long> schoolIds) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<User> user = sub.from(User.class);
            sub.select(user.<Long>get("id"))
                    .where(user.get("school").get("id").in(schoolIds));
            return root.get("leaderId").in(sub);
        };
    }

    /**
     * 활동 기간: (종료일 - 시작일) 일수가 범위 안인 글
     * DB 함수를 직접 부르지 않고 Hibernate가 DB에 맞는 SQL을 만들게 합니다 (MySQL·H2 모두 동작)
     */
    public static Specification<Post> periodIn(PostPeriod period) {
        return (root, query, cb) -> {
            HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
            Expression<Long> days = hcb.durationByUnit(TemporalUnit.DAY,
                    hcb.durationBetween(root.<LocalDate>get("periodEnd"), root.<LocalDate>get("periodStart")));
            return cb.between(days, (long) period.minDays(), (long) period.maxDays());
        };
    }

    // ===== 검색 (LIST-04) =====

    /** 키워드: 제목·소개·행사명 중 하나에 포함된 글 */
    public static Specification<Post> keywordContains(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword + "%";

            Subquery<Long> eventSub = query.subquery(Long.class);
            Root<Event> event = eventSub.from(Event.class);
            eventSub.select(event.<Long>get("id"))
                    .where(cb.equal(event.get("post"), root),
                            cb.like(event.get("name"), pattern));

            return cb.or(
                    cb.like(root.get("title"), pattern),
                    cb.like(root.get("description"), pattern),
                    cb.exists(eventSub));
        };
    }
}