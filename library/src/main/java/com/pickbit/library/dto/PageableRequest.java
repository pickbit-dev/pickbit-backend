package com.pickbit.library.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Objects;

/**
 * 페이징 및 정렬 조건을 담는 요청 DTO.
 *
 * <p>컨트롤러 메서드의 쿼리 파라미터로 바인딩되어
 * {@code page}, {@code size}, {@code sort} 값을 전달받는다.
 *
 * @param page 0-based 페이지 번호 ({@code null}이면 0으로 처리)
 * @param size 페이지 크기 ({@code null}이면 기본값 사용, 최대 {@value #MAX_SIZE})
 * @param sort 정렬 문자열 목록 ({@code "field,asc"} 또는 {@code "field,desc"} 형식)
 */
public record PageableRequest(
        Integer page,
        Integer size,
        List<String> sort
) {
    /** 허용되는 최대 페이지 크기. */
    private static final int MAX_SIZE = 100;

    /**
     * {@code sort} 문자열 목록을 {@link SortField} 리스트로 파싱한다.
     *
     * <p>파싱할 수 없는 항목은 필터링되어 결과에 포함되지 않는다.
     *
     * @return 파싱된 정렬 필드 목록 (비어 있을 수 있음)
     */
    public List<SortField> sortFields() {
        if (sort == null || sort.isEmpty()) return List.of();
        return sort.stream()
                .map(SortField::parse)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Spring Data {@link Pageable} 객체로 변환한다.
     *
     * <p>페이지 번호는 0 이상으로, 크기는 1 이상 {@value #MAX_SIZE} 이하로 보정된다.
     * {@code size}가 {@code null}이면 {@code defaultSize}를 사용한다.
     *
     * @param defaultSize {@code size}가 {@code null}일 때 적용할 기본 페이지 크기
     * @return 보정된 페이징·정렬 조건이 적용된 {@link Pageable}
     */
    public Pageable toPageable(int defaultSize) {
        int p = page != null ? Math.max(page, 0) : 0;
        int s = size != null ? Math.min(Math.max(size, 1), MAX_SIZE) : defaultSize;

        List<SortField> fields = sortFields();
        if (!fields.isEmpty()) {
            Sort springSort = Sort.by(
                    fields.stream().map(SortField::toSortOrder).toList()
            );
            return PageRequest.of(p, s, springSort);
        }
        return PageRequest.of(p, s);
    }
}
