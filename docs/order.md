# 주문 기능

상품 주문, 주문 조회, 주문 취소 API를 정리한 문서다. 재고 차감과 복원, 동시 주문 처리, 주문 목록의 페이징과 N+1 방지를 다룬다.

## 목차

1. [요구사항](#요구사항)
2. [패키지 구조](#패키지-구조)
3. [엔티티와 연관관계](#엔티티와-연관관계)
4. [API](#api)
5. [주문 목록 조회: 쿼리 3번](#주문-목록-조회-쿼리-3번)
6. [재고와 동시성](#재고와-동시성)
7. [오류 응답](#오류-응답)
8. [DB](#db)
9. [테스트](#테스트)
10. [알려진 제한](#알려진-제한)

## 요구사항

| 요구사항 | 구현 |
|---|---|
| 주문 하나는 여러 주문 상품(상품 + 수량)으로 이루어진다. | `Order`와 `OrderItem` |
| 주문하면 재고를 확인하고 차감한다. | 상품을 잠그고 확인한 뒤 차감 |
| 상태는 주문중, 주문 완료, 주문 취소다. | `OrderStatus`: `PENDING`, `COMPLETED`, `CANCELED` |
| 주문 상품 중 하나라도 재고가 부족하면 주문은 실패한다. | 모든 상품을 먼저 확인하고, 부족하면 아무것도 차감하지 않음 |
| 모든 주문 기능은 로그인한 사용자만 쓴다. | 화이트 리스트 밖 + `@PreAuthorize` |
| 주문은 수정하지 않는다. 취소하고 다시 주문한다. | 수정 API 없음. 취소하면 재고 복원 |

요구사항에 없어서 정한 것:

| 항목 | 결정 |
|---|---|
| 주문중 → 주문 완료 시점 | 결제가 없으므로 재고 차감에 성공하면 같은 트랜잭션에서 바로 완료한다. 저장된 주문은 `COMPLETED`나 `CANCELED`다. |
| 취소할 수 있는 상태 | 주문중, 주문 완료. 이미 취소된 주문은 409 |
| 조회와 취소 권한 | 본인과 ADMIN. 다른 사람의 주문은 404 |
| 같은 상품을 두 번 넣은 경우 | 수량을 합친다. |
| 개수 제한 | 한 주문에 상품 50개, 상품 하나에 수량 999개까지 |

## 패키지 구조

```
com.example.demo1.order
├── controller   OrderController, OrderControllerAdvice
├── service      OrderService
├── repository   OrderRepository, OrderItemRepository
├── entity       Order, OrderItem, OrderStatus
├── dto          OrderCreateRequest, OrderLineRequest, OrderPageRequest,
│                OrderResponse, OrderItemResponse, OrderPageResponse,
│                OrderErrorResponse, StockShortageResponse
└── exception    OrderNotFoundException, OrderProductNotFoundException, OutOfStockException,
                 InvalidOrderException, InvalidOrderStatusException
```

상품 쪽에는 여러 상품을 한 번에 잠그는 `ProductRepository.findAllByIdInForUpdate`를 추가했다.

## 엔티티와 연관관계

```
orders                        order_item
┌───────────────────┐         ┌──────────────────────────┐
│ id                │◀────────│ order_id (FK)            │  @ManyToOne(LAZY) 단방향
│ orderer_email_key │         │ product_id               │  상품과는 연관관계 없음
│ status            │         │ product_name             │  (ID·이름·가격을 복사)
│ total_price       │         │ unit_price               │
│ item_count        │         │ quantity                 │
│ canceled_at       │         └──────────────────────────┘
│ created_at, ...   │
└───────────────────┘
```

### 단방향 `@ManyToOne`

연관관계는 주문 상품 → 주문 단방향이다. `Order`는 주문 상품 목록을 갖지 않는다.

| 고려한 방식 | 결과 |
|---|---|
| 양방향 (`mappedBy`) | 쓰지 않는다. 연관관계는 단방향으로만 둔다. |
| `Order → OrderItem` (`@OneToMany` + `@JoinColumn`) | `Order`를 바꿨는데 SQL은 `order_item`에 나가 예측하기 어렵다. 외래 키가 nullable이면 INSERT 뒤 UPDATE가 더 나간다. 컬렉션을 fetch join하면서 페이징하면 전체를 메모리에 올린다. |
| **`OrderItem → Order` (`@ManyToOne`)** | 외래 키가 있는 쪽이 관계를 관리한다. 주문을 조회해도 주문 상품을 읽지 않아 페이징이 단순하다. **선택** |

대신 주문 상품은 따로 저장하고 따로 조회한다. 주문은 수정이 없어 저장은 주문할 때 한 번뿐이다.

### 규칙은 엔티티가 지킨다

`Order`가 주문 상품 목록을 갖지 않아도 규칙은 `Order` 안에 둔다.

| 메서드 | 규칙 |
|---|---|
| `new Order(emailKey)` | 주문중(`PENDING`)으로 시작 |
| `addItem(상품 ID, 이름, 가격, 수량)` | 주문중일 때만. 새 `OrderItem`을 만들어 **돌려주고**, 총액(`long`, 넘치면 예외)과 상품 수를 갱신한다. 수량은 1 이상 |
| `complete()` | 주문중이고 상품이 1개 이상이어야 완료 |
| `cancel()` | 이미 취소된 주문이면 예외. 취소 시각 기록 |
| `isOrderedBy(emailKey)` | 본인 주문인지 확인 |

서비스는 `addItem()`이 돌려준 주문 상품을 모아, 주문을 저장한 뒤 `saveAll`로 저장한다.

```java
Order order = new Order(emailKey);
items.add(order.addItem(product.getId(), product.getName(), product.getPrice(), quantity));
order.complete();
orderRepository.save(order);          // id가 생긴다
orderItemRepository.saveAll(items);   // 그 id를 외래 키로 쓴다
```

### 주문자와 상품은 값으로 저장

- **주문자:** `User`와 연관관계를 두지 않고 `orderer_email_key`(소문자 이메일)만 저장한다. 사용자 삭제는 행을 실제로 지우므로, 연관관계를 두면 주문이 있는 사용자를 삭제할 수 없다.
- **상품:** 상품 ID, 상품명, **주문 당시 가격**을 복사한다.
  - 주문한 뒤 가격이나 이름이 바뀌어도 주문 내역은 그대로여야 한다.
  - 상품은 삭제 표시만 하고 `@SQLRestriction("deleted = false")`로 조회에서 뺀다. 연관관계로 두면 삭제된 상품이 든 주문을 읽을 때 상품을 찾지 못해 오류가 난다.
- **이미지는 복사하지 않는다:** 상품을 수정하면서 이미지를 빼면 Storage에서 파일을 지운다. URL을 복사해 두면 링크가 깨진다. 이미지는 조회할 때 상품의 지금 0번 이미지를 조인한다.

## API

모든 API는 로그인이 필요하다. `/api/orders/**`는 화이트 리스트에 없어 JWT가 없으면 `AuthCheckFilter`가 401로 막는다. 메서드마다 `@PreAuthorize`도 붙였다.

| 메서드 | 경로 | 권한 | 성공 |
|---|---|---|---|
| POST | `/api/orders` | 로그인 | 201 |
| GET | `/api/orders?page=1&size=10` | 로그인 (내 주문) | 200 |
| GET | `/api/orders/all?page=1&size=10` | ADMIN | 200 |
| GET | `/api/orders/{id}` | 본인 또는 ADMIN | 200 |
| POST | `/api/orders/{id}/cancel` | 본인 또는 ADMIN | 200 |

- **취소는 `POST /{id}/cancel`:** 주문을 지우지 않고 취소 상태로 남기므로 `DELETE`를 쓰지 않는다.
- **다른 사람의 주문은 404:** 403으로 거부하면 그 번호의 주문이 있다는 사실이 드러난다. 없는 주문과 똑같이 처리한다.
- **페이지:** 1부터 시작, 크기 1~100(기본 10), 최근 주문순(id 내림차순)

### 주문 `POST /api/orders`

```json
// 요청
{ "items": [
    { "productId": 126, "quantity": 2 },
    { "productId": 127, "quantity": 1 },
    { "productId": 126, "quantity": 1 }
] }

// 201 응답 (126은 수량 3으로 합쳐짐)
{
  "id": 1,
  "status": "COMPLETED",
  "statusDescription": "주문 완료",
  "totalPrice": 5500,
  "itemCount": 2,
  "createdAt": "2026-11-07T20:28:30.123456",
  "canceledAt": null,
  "items": [
    { "productId": 126, "productName": "주문테스트-A", "unitPrice": 1000, "quantity": 3,
      "linePrice": 3000, "imageUrl": "https://.../products/...png" },
    { "productId": 127, "productName": "주문테스트-B", "unitPrice": 2500, "quantity": 1,
      "linePrice": 2500, "imageUrl": null }
  ]
}
```

- 주문 상품은 요청 순서대로 나온다(같은 상품은 처음 나온 자리).
- `imageUrl`은 상품의 **지금** 0번 이미지다. 이미지가 없거나 상품이 삭제됐으면 `null`이다.

### 처리 순서 (`OrderService.create`)

1. 같은 상품의 수량을 합친다(요청 순서 유지).
2. 상품을 **id 순서로 한 번에 잠근다.** 없거나 삭제된 상품이 있으면 404
3. **모든 상품의 재고를 먼저 확인한다.** 하나라도 부족하면 아무것도 차감하지 않고 409와 부족한 상품 목록을 응답한다.
4. 재고를 차감하고 주문 상품을 만든다. 주문을 완료한다.
5. 주문, 주문 상품 순서로 저장한다.

차감하면서 확인해도 예외가 나면 롤백으로 재고는 돌아온다. 하지만 그러면 처음 부족한 상품 하나만 알 수 있어서 먼저 모두 확인한다.

### 취소 `POST /api/orders/{id}/cancel`

1. 주문을 비관적 쓰기 잠금으로 조회한다.
2. 본인 주문이거나 ADMIN인지 확인한다. 아니면 404
3. 상태를 취소로 바꾼다. 이미 취소됐으면 409
4. 주문 상품의 상품을 id 순서로 잠그고 재고를 되돌린다. **삭제된 상품은 되돌리지 않는다.**

## 주문 목록 조회: 쿼리 3번

페이지 크기와 관계없이 쿼리는 3번이다. 실제 실행한 SQL이다(`size=2`).

```sql
-- 1. 이번 페이지의 주문
select ... from orders o1_0 where o1_0.orderer_email_key=? order by o1_0.id desc fetch first ? rows only

-- 2. 전체 주문 수 (Page의 totalElements, totalPages)
select count(o1_0.id) from orders o1_0 where o1_0.orderer_email_key=?

-- 3. 1번 주문들의 주문 상품과 상품의 0번 이미지
select oi1_0.order_id, oi1_0.product_id, oi1_0.product_name, oi1_0.unit_price, oi1_0.quantity, i1_0.image_url
from order_item oi1_0
left join product p1_0 on (p1_0.deleted = false) and p1_0.id = oi1_0.product_id
left join product_image i1_0 on p1_0.id = i1_0.product_id and i1_0.image_index = 0
where oi1_0.order_id in (?, ?)
order by oi1_0.order_id desc, oi1_0.id
```

서비스는 3번 결과를 `orderId`로 묶어 각 주문에 붙인다.

### 페이징과 N+1을 피한 방법

- **페이징:** `Order`에 주문 상품 컬렉션이 없어 컬렉션 fetch join이 생기지 않는다. DB가 `limit/offset`으로 페이징한다.
- **N+1:** 주문 상품을 주문마다 조회하지 않고 `in (주문 ID들)`로 한 번에 읽는다. `@BatchSize`와 같은 원리다.
- **2번 count:** `Page`를 돌려주기 때문에 Spring Data가 실행한다. 다른 목록 API와 응답 형식(`totalElements`, `totalPages`)을 맞추려고 `Page`를 쓴다. 무한 스크롤처럼 전체 수가 필요 없으면 `Slice`로 바꿔 쿼리를 2번으로 줄일 수 있다.
- **3번은 DTO로 바로 받는다:** `select new OrderItemResponse(...)`. 엔티티를 거치지 않아 지연 로딩이 일어나지 않는다. `oi.order.id`는 `order_id` 컬럼을 바로 비교해 `orders`와 조인하지 않는다.
- **상품 연관관계 없이 조인:** `left join Product p on p.id = oi.productId`처럼 엔티티끼리 조건으로 조인한다. 0번 이미지는 상품마다 하나라 행이 늘지 않는다.
- **삭제된 상품:** `@SQLRestriction`이 조인 조건(`on (p1_0.deleted = false) and ...`)에 붙는다. `left join`이라 주문 상품은 나오고 이미지만 `null`이 된다.
- **인덱스:** 1·2번은 `orders (orderer_email_key, id)`, 3번은 `order_item (order_id)`를 탄다.

### 고려했지만 쓰지 않은 방식: 한 번에 조인

```sql
select oi from OrderItem oi join fetch oi.order o where o.ordererEmailKey = :email order by o.id desc  -- limit 10
```

`limit 10`이 주문 10건이 아니라 **주문 상품 10행**을 자른다. 한 페이지의 주문 수가 들쭉날쭉하고, 주문 하나가 두 페이지로 쪼개진다. 주문을 서브쿼리로 먼저 자르면 쿼리를 2번으로 줄일 수 있지만, `Pageable`을 쓸 수 없고 주문 정보가 주문 상품 수만큼 중복된다. 쿼리 3번은 페이지 크기와 관계없이 고정이라 N+1이 아니므로 이 방식을 유지했다.

## 재고와 동시성

| 상황 | 처리 |
|---|---|
| 같은 상품을 동시에 주문 | 상품 행을 잠가(`FOR NO KEY UPDATE`) 확인과 차감 사이에 다른 주문이 끼어들지 못한다. 재고 1개인 상품을 동시에 두 번 주문하면 하나만 201이다. |
| 교착 상태 | 주문과 취소 모두 상품을 **항상 id 순서로** 잠근다. 두 주문이 같은 상품들을 반대 순서로 잠그면 서로를 기다리게 된다. |
| 같은 주문을 동시에 두 번 취소 | 주문 행을 먼저 잠근다. 두 번째 요청은 이미 취소됨(409)이라 재고는 한 번만 돌아온다. |
| 잠금 대기 | `connection-init-sql: SET lock_timeout = '3s'`로 3초 넘게 기다리지 않는다. |

상품 잠금은 쿼리 한 번이다.

```sql
select ... from product p1_0 where (p1_0.deleted = false) and p1_0.id in (?, ?) order by p1_0.id for no key update of p1_0
```

PostgreSQL에서 Hibernate는 `PESSIMISTIC_WRITE`를 `FOR UPDATE`보다 약한 `FOR NO KEY UPDATE`로 보낸다. 재고를 바꾸려는 다른 트랜잭션은 똑같이 기다린다. 이 행을 참조하는 외래 키 삽입은 막지 않는다.

## 오류 응답

`OrderErrorResponse`. 다른 기능의 오류 형식에 재고 부족 정보(`shortages`)를 더했다.

```json
{ "status": 409, "message": "재고가 부족한 상품이 있습니다.", "fieldErrors": {},
  "shortages": [ { "productId": 128, "productName": "주문테스트-C", "stock": 1, "requested": 5 } ] }
```

| 상황 | 상태 | message |
|---|---|---|
| 재고 부족 | 409 | 재고가 부족한 상품이 있습니다. (`shortages` 포함) |
| 이미 취소된 주문을 취소 | 409 | 주문 취소 상태의 주문은 취소할 수 없습니다. |
| 없는 주문, 다른 사람의 주문 | 404 | 주문을 찾을 수 없습니다. id=... |
| 없거나 삭제된 상품 | 404 | 주문할 수 없는 상품입니다. 상품 ID=[...] |
| 상품 없음, 수량 0, 페이지 크기 초과 등 | 400 | 입력값을 확인해 주세요. (`fieldErrors` 포함) |
| 잘못된 JSON, `/api/orders/abc` | 400 | 요청 값의 형식이 올바르지 않습니다. |
| JWT 없음 | 401 | `TOKEN_MISSING` (필터 응답) |
| USER가 `/all` 호출 | 403 | `FORBIDDEN` (필터 응답) |

## DB

테이블은 JPA `ddl-auto: update`로 만든다. `order`는 SQL 예약어라 테이블 이름을 `orders`로 했다.

### orders

| 컬럼 | 설명 |
|---|---|
| `id` | PK, identity |
| `orderer_email_key` | 주문자의 `users.email_key`. 외래 키 없음 |
| `status` | `PENDING`, `COMPLETED`, `CANCELED`. Hibernate가 check 제약을 만든다. |
| `total_price` | 주문 상품 금액의 합 (`bigint`) |
| `item_count` | 주문 상품 수 |
| `canceled_at` | 취소 시각. 취소하지 않았으면 null |
| `created_at`, `updated_at` | Hibernate가 자동으로 기록 |

인덱스: `idx_orders_orderer_email_key_id (orderer_email_key, id)`

### order_item

| 컬럼 | 설명 |
|---|---|
| `id` | PK, identity |
| `order_id` | FK → `orders`. 바꿀 수 없음(`updatable = false`) |
| `product_id` | 상품 ID. 외래 키 없음 |
| `product_name` | 주문 당시 상품명 |
| `unit_price` | 주문 당시 가격 |
| `quantity` | 수량 |

인덱스: `idx_order_item_order_id (order_id)`

## 테스트

| 테스트 | 방식 | 개수 | 확인하는 것 |
|---|---|---|---|
| `OrderTest` | 엔티티만, DB 없음 | 14 | 총액·상품 수 계산, `int` 넘침, 수량 검증, 상태 전이(완료, 취소, 두 번 취소) |
| `OrderServiceTest` | `@DataJpaTest`, 실제 DB, 끝나면 롤백 | 25 | 재고 차감·복원, 재고 부족 시 전체 실패, 수량 합치기, 주문 당시 가격, 삭제된 상품, 권한, **목록 쿼리 수** |
| `OrderControllerTest` | `@WebMvcTest`, 서비스는 가짜 객체 | 16 | 상태 코드, 검증 메시지, `shortages` 응답, `/all`과 `/{id}` 구분, `orderId` 비노출 |

- **쿼리 수 검증:** `hibernate.generate_statistics`를 켜고 실행된 SQL 수(`getPrepareStatementCount`)를 센다. 페이지 크기 2와 5 모두 3번이다. 주문이 없으면 1번이다(Spring Data가 count를 생략하고, 주문 상품 조회도 건너뜀).
- **주문자 분리:** 실제 DB에 다른 주문이 있으므로 테스트마다 주문자 이메일을 UUID로 만든다.
- **`@AuthenticationPrincipal`:** `@WebMvcTest`에는 이 파라미터를 처리하는 리졸버가 없어, Spring MVC가 `TokenPayload`를 요청 파라미터로 보고 만들다 NPE가 난다. `support/AuthenticationPrincipalTestConfig`로 리졸버를 등록한다. `TodoControllerTest`도 같은 이유로 실패하고 있어 함께 적용했다.
- **자동 테스트로 확인하지 않는 것:** 테스트가 트랜잭션 하나로 실행되어 동시 주문, 교착 상태 방지, 동시 취소의 잠금 대기는 재현되지 않는다. 컨트롤러 테스트는 보안 필터를 꺼서 401·403과 `@PreAuthorize`도 검증하지 않는다. 이 부분은 아래처럼 직접 호출해 확인했다.

### 실제 호출로 확인한 것

앱을 실행해 `user1@example.com`, `admin@example.com`으로 직접 호출하고 SQL 로그를 확인했다. 테스트용 상품을 새로 만들어 쓰고 끝나면 삭제했다.

| 확인 항목 | 결과 |
|---|---|
| `[A×2, B×1, A×1]` 주문 | 201, A 수량 3으로 합쳐짐, 총액 5,500, A에만 이미지 URL, 재고 A 10→7, B 5→4 |
| `[B×1, C×5]` (C 재고 1) | 409, `shortages`에 C, **B 재고 그대로** |
| 없는 상품, 빈 목록, 수량 0, 형식 오류 | 404, 400, 400, 400 |
| 내 목록 (`size=2`, 전체 3건) | page 1/2, `hasNext` true, **쿼리 3개** |
| 상세, 없는 주문, `/api/orders/abc` | 200, 404, 400 |
| 취소, 다시 취소 | 200(재고 A +3, B +1), 409 |
| 재고 1개 상품을 동시에 2번 주문 | 201 하나, 409 하나, 재고 0 |
| 상품 삭제 후 그 상품이 든 주문 목록 | 주문 상품은 그대로, 이미지만 `null`. 취소하면 삭제된 상품은 복원 안 함 |
| 토큰 없이, USER가 `/all` | 401 `TOKEN_MISSING`, 403 `FORBIDDEN` |
| ADMIN이 `/all`, user1 주문 상세·취소 | 200, 200, 200(재고 복원) |
| ADMIN의 내 목록 | admin 본인 주문만 |
| USER가 admin 주문 상세·취소 | 404, 404(재고 그대로) |
| USER의 내 목록 | admin 주문 없음 |

## 알려진 제한

- **동시성 자동 테스트 없음:** 동시 주문, 교착 상태 방지, 동시 취소는 직접 호출로만 확인했다.
- **결제 없음:** 재고 차감에 성공하면 바로 완료다. 결제를 붙이면 결제 전까지 `PENDING`으로 두고, 결제하지 않은 주문의 재고를 되돌리는 처리가 필요하다.
- **잠금 대기 응답:** 잠금을 3초 안에 얻지 못하면 나는 예외를 따로 처리하지 않는다. 스프링 기본 오류(500)로 응답한다.
- **삭제된 상품 재고:** 취소할 때 삭제된 상품의 재고는 되돌리지 않는다. 나중에 상품을 복구하는 기능이 생기면 재고가 맞지 않는다.
- **주문 삭제 없음:** 테스트로 만든 주문도 DB에 남는다.
- **사용자 삭제:** 주문자와 외래 키가 없어서, 사용자를 삭제해도 주문은 남는다. 그 주문은 ADMIN만 볼 수 있다.
