package com.grytonems.backend.global.common;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 방식 목록 응답 (API 명세서 공통 규칙).
 *
 * <pre>
 * { "items": [...], "nextCursor": "102", "hasNext": true }
 * </pre>
 *
 * 쓰는 법: 화면에 보여줄 개수(size)보다 1개 더(size + 1) 조회한 목록을 넘기면,
 * 다음 페이지가 있는지 판단하고 size개로 잘라 줍니다.
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    /** size 쿼리 값을 1~50 사이로 맞춥니다 (없으면 20) */
    public static int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(size, MAX_SIZE));
    }

    /**
     * @param fetched   size + 1개까지 조회한 결과
     * @param size      한 페이지 크기
     * @param cursorOf  항목에서 커서 값을 꺼내는 함수 (보통 id)
     * @param mapper    엔티티 → 응답 DTO
     */
    public static <E, T> CursorPage<T> of(List<E> fetched, int size, Function<E, Object> cursorOf,
            Function<E, T> mapper) {
        boolean hasNext = fetched.size() > size;
        List<E> page = hasNext ? fetched.subList(0, size) : fetched;
        String nextCursor = hasNext ? String.valueOf(cursorOf.apply(page.get(page.size() - 1))) : null;
        return new CursorPage<>(page.stream().map(mapper).toList(), nextCursor, hasNext);
    }
}
