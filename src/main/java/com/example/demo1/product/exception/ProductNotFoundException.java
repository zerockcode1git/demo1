package com.example.demo1.product.exception;

/**
 * 요청한 id의 상품이 없을 때 발생한다.
 * RuntimeException이므로 @Transactional 메서드 밖으로 던져지면 트랜잭션이 롤백된다.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("상품을 찾을 수 없습니다. id=" + id);
    }
}
