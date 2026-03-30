package com.pickbit.productservice.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long productId) {
        super("상품을 찾을 수 없습니다. id=" + productId);
    }
}
