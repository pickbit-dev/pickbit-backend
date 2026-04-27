package com.pickbit.productservice.exception;

public class DuplicateCategoryException extends RuntimeException {

    public DuplicateCategoryException(String name) {
        super("이미 존재하는 카테고리명입니다. name=" + name);
    }
}
