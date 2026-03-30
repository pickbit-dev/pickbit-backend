package com.pickbit.library.dto;

import org.springframework.data.domain.Sort;

/**
 * 단일 정렬 조건을 나타내는 DTO.
 *
 * <p>필드명과 정렬 방향({@link Direction})의 쌍으로 구성되며,
 * {@code "field,desc"} 형식의 문자열로부터 파싱할 수 있다.
 *
 * @param field     정렬 대상 필드명
 * @param direction 정렬 방향 ({@link Direction#ASC} 또는 {@link Direction#DESC})
 */
public record SortField(String field, Direction direction) {

    /**
     * 정렬 방향을 나타내는 열거형.
     */
    public enum Direction {
        /** 오름차순. */
        ASC,
        /** 내림차순. */
        DESC;

        /**
         * Spring Data {@link Sort.Direction}으로 변환한다.
         *
         * @return 대응하는 {@link Sort.Direction}
         */
        public Sort.Direction toSpring() {
            return this == ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        }
    }

    /**
     * {@code "field,desc"} 형식의 문자열을 파싱하여 {@link SortField}를 생성한다.
     *
     * <p>방향이 생략되면 기본값은 {@link Direction#ASC}이다.
     * 입력이 {@code null}이거나 공백이면 {@code null}을 반환한다.
     *
     * @param sortStr 파싱할 정렬 문자열 (예: {@code "name,asc"}, {@code "createdAt,desc"})
     * @return 파싱된 {@link SortField}, 또는 유효하지 않은 입력이면 {@code null}
     */
    public static SortField parse(String sortStr) {
        if (sortStr == null || sortStr.isBlank()) return null;
        String[] parts = sortStr.split(",", 2);
        String field = parts[0].trim();
        if (field.isBlank()) return null;
        Direction direction = parts.length > 1
                && "desc".equalsIgnoreCase(parts[1].trim())
                ? Direction.DESC : Direction.ASC;
        return new SortField(field, direction);
    }

    /**
     * Spring Data {@link Sort.Order}로 변환한다.
     *
     * @return 이 정렬 조건에 대응하는 {@link Sort.Order}
     */
    public Sort.Order toSortOrder() {
        return new Sort.Order(direction.toSpring(), field);
    }
}
