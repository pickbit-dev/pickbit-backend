package com.pickbit.library.dto;


import org.springframework.data.domain.Page;

import java.util.List;

/**
 * {@link Page} 조회 결과를 API 응답으로 변환하기 위한 제네릭 페이징 DTO.
 *
 * @param content      현재 페이지의 데이터 목록
 * @param page         현재 페이지 번호 (0-based)
 * @param size         페이지 크기
 * @param totalElements 전체 데이터 건수
 * @param totalPages   전체 페이지 수
 * @param first        첫 번째 페이지 여부
 * @param last         마지막 페이지 여부
 * @param <T>          데이터 요소 타입
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    /**
     * Spring Data {@link Page} 객체를 {@link PageResponse}로 변환하는 팩토리 메서드.
     *
     * @param page 변환할 {@link Page} 객체
     * @param <T>  데이터 요소 타입
     * @return 변환된 {@link PageResponse}
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
