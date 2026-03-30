package com.pickbit.library.dto;

import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * {@link Slice} 조회 결과를 API 응답으로 변환하기 위한 제네릭 페이징 DTO.
 * <p>
 * {@link PageResponse}와 달리 전체 건수({@code totalElements})와
 * 전체 페이지 수({@code totalPages})를 포함하지 않으며,
 * 대신 다음 페이지 존재 여부({@code hasNext})를 제공합니다.
 * count 쿼리가 불필요한 무한 스크롤 등의 시나리오에 적합합니다.
 *
 * @param content 현재 페이지의 데이터 목록
 * @param page    현재 페이지 번호 (0-based)
 * @param size    페이지 크기
 * @param first   첫 번째 페이지 여부
 * @param last    마지막 페이지 여부
 * @param hasNext 다음 페이지 존재 여부
 * @param <T>     데이터 요소 타입
 */
public record SliceResponse<T>(
        List<T> content,
        int page,
        int size,
        boolean first,
        boolean last,
        boolean hasNext
) {
    /**
     * Spring Data {@link Slice} 객체를 {@link SliceResponse}로 변환하는 팩토리 메서드.
     *
     * @param slice 변환할 {@link Slice} 객체
     * @param <T>   데이터 요소 타입
     * @return 변환된 {@link SliceResponse}
     */
    public static <T> SliceResponse<T> from(Slice<T> slice) {
        return new SliceResponse<>(
                slice.getContent(),
                slice.getNumber(),
                slice.getSize(),
                slice.isFirst(),
                slice.isLast(),
                slice.hasNext()
        );
    }
}
