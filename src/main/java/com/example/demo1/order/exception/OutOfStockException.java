package com.example.demo1.order.exception;

import com.example.demo1.order.dto.StockShortageResponse;
import java.util.List;

/**
 * 주문 상품 중 하나라도 재고가 부족할 때 발생한다. 부족한 상품을 모두 담아 알려 준다.
 * 이 예외로 트랜잭션이 롤백되어 어떤 상품의 재고도 차감되지 않는다.
 */
public class OutOfStockException extends RuntimeException {

    private final List<StockShortageResponse> shortages;

    public OutOfStockException(List<StockShortageResponse> shortages) {
        super("재고가 부족한 상품이 있습니다.");
        this.shortages = List.copyOf(shortages);
    }

    public List<StockShortageResponse> getShortages() {
        return shortages;
    }
}
