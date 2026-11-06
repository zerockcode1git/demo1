# 상품 기능

상품 등록, 조회, 수정, 삭제 API와 상품 이미지 업로드(Supabase Storage)를 정리한 문서다.

## 목차

1. [패키지 구조](#패키지-구조)
2. [API](#api)
3. [이미지 처리](#이미지-처리)
4. [오류 응답](#오류-응답)
5. [설정](#설정)
6. [DB](#db)
7. [테스트](#테스트)
8. [알려진 제한](#알려진-제한)

## 패키지 구조

```
com.example.demo1
├── product
│   ├── controller   ProductController, ProductControllerAdvice
│   ├── service      ProductCommandService, ProductService
│   ├── repository   ProductRepository
│   ├── entity       Product, ProductImage(@Embeddable)
│   ├── dto          요청/응답 DTO
│   └── exception    ProductNotFoundException, InvalidProductImageException, InvalidStockException, InsufficientStockException
└── storage
    ├── config       SupabaseStorageConfig, SupabaseStorageProperties
    ├── controller   FileStorageControllerAdvice (모든 컨트롤러 공통)
    ├── service      FileStorageService
    ├── dto          FileUploadResponse, FileErrorResponse
    └── exception    InvalidFileException, FileStorageException
```

### 서비스를 둘로 나눈 이유

| 서비스 | 트랜잭션 | 역할 |
|---|---|---|
| `ProductCommandService` | 없음 | 이미지 검사, 업로드, 실패 시 업로드한 파일 정리, 수정으로 빠진 이미지 삭제 |
| `ProductService` | 있음 (기본 읽기 전용) | DB 조회, 저장, 수정, 삭제 표시 |

- 업로드는 외부 네트워크 호출이다. 트랜잭션 안에서 하면 업로드를 기다리는 동안 DB 커넥션을 붙잡는다. 로컬 커넥션 풀은 2개뿐이다.
- 같은 클래스 안에서 `this.create()`처럼 호출하면 `@Transactional`이 적용되지 않는다. 그래서 트랜잭션 없는 쪽을 별도 빈으로 분리했다.
- 컨트롤러는 등록과 수정은 `ProductCommandService`, 조회와 삭제는 `ProductService`를 호출한다.
- 서비스는 엔티티나 `Page`를 그대로 반환하지 않고 항상 DTO로 반환한다.

## API

기본 경로는 `/api/products`이다. Swagger UI(`/swagger-ui/index.html`)의 "상품" 그룹에서 확인할 수 있다.

| 메서드 | 경로 | 설명 | 로그인 | 성공 응답 |
|---|---|---|---|---|
| POST | `/api/products` | 상품 등록 (multipart) | 필요 | 201 `ProductResponse` |
| GET | `/api/products/{id}` | 상품 조회 | 불필요 | 200 `ProductResponse` |
| GET | `/api/products` | 상품 목록 조회, 검색 | 불필요 | 200 `ProductPageResponse` |
| PUT | `/api/products/{id}` | 상품 수정 (multipart) | 필요 | 200 `ProductResponse` |
| DELETE | `/api/products/{id}` | 상품 삭제 (삭제 표시) | 필요 | 204 |

- **로그인 필요:** `Authorization: Bearer {accessToken}` 헤더를 붙여야 한다. 없으면 401 `TOKEN_MISSING`이다.
- **조회와 목록:** 화이트 리스트(`GET /api/products/**`)에 있어 토큰 없이 호출할 수 있다.
- 자세한 내용은 [docs/auth.md](auth.md)를 본다.

### 등록과 수정 요청 형식

등록과 수정은 `multipart/form-data`로 보낸다.

| 파트 | 필수 | 내용 |
|---|---|---|
| `product` | O | 상품 정보 JSON. **이 파트의 Content-Type은 `application/json`이어야 한다.** 없으면 415가 난다. |
| `files` | X | 이미지 파일. 여러 개 보낼 수 있다. |

Swagger UI는 OpenAPI `encoding` 설정에 따라 `product` 파트를 JSON으로 보낸다.

### 상품 등록 `POST /api/products`

`product` 필드:

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `name` | string | O | 공백 불가 |
| `price` | int | O | 0 이상 |
| `stock` | int | O | 0 이상, 처음 재고 |
| `imageUrls` | string[] | X | **무시한다.** 이미지는 `files`로만 받는다. |

- 이미지는 `files` 순서대로 0번부터 번호가 매겨진다.
- `files`를 생략하면 이미지 없이 등록한다.

```bash
curl -F 'product={"name":"티셔츠","price":10000,"stock":50};type=application/json' \
     -F files=@front.png -F files=@back.png \
     http://localhost:8080/api/products
```

### 상품 조회 `GET /api/products/{id}`

`ProductResponse`:

```json
{
  "id": 3,
  "name": "기본 반팔 티셔츠",
  "price": 15900,
  "stock": 120,
  "images": [
    { "imageUrl": "https://{project}.supabase.co/storage/v1/object/public/images/2026/11/06/{uuid}.png", "imageIndex": 0 },
    { "imageUrl": "https://.../{uuid}.png", "imageIndex": 1 }
  ],
  "createdAt": "2026-11-06T23:41:10.123456",
  "updatedAt": "2026-11-06T23:41:10.123456"
}
```

- 이미지는 `imageIndex` 순서로 정렬된다.
- 삭제된 상품은 404를 응답한다.

### 상품 목록 조회 `GET /api/products`

| 파라미터 | 기본값 | 규칙 |
|---|---|---|
| `page` | 1 | **1부터 시작**, 1 이상 |
| `size` | 10 | 1 ~ 100 |
| `keyword` | (없음) | 100자 이하. 상품명 부분 일치이며 영문 대소문자를 구분하지 않는다. |

- **정렬:** 최근 등록순(id 내림차순)이다.
- **검색:** `keyword`를 생략하거나 공백만 보내면 전체를 조회한다. 앞뒤 공백은 제거한다.
- **와일드카드:** 검색어의 `%`, `_`, `\`는 이스케이프해서 문자 그대로 찾는다.
- **삭제된 상품:** 목록과 개수에서 빠진다.

`ProductPageResponse`:

```json
{
  "content": [
    { "id": 12, "name": "캔버스 스니커즈", "price": 55000, "stock": 40, "imageUrl": "https://.../{uuid}.png" }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 12,
  "totalPages": 2,
  "hasNext": true
}
```

- `content[].imageUrl`은 0번 이미지이며, 이미지가 없으면 `null`이다.
- 응답의 `page`도 1부터 시작한다.

### 상품 수정 `PUT /api/products/{id}`

`product` 필드:

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `name` | string | O | 공백 불가 |
| `price` | int | O | 0 이상 |
| `stock` | int | O | 0 이상 |
| `imageUrls` | string[] | O | **남겨 둘 기존 이미지 URL**. 빈 목록 `[]`이면 기존 이미지를 모두 지운다. |

- 결과 이미지 순서는 `imageUrls`(기존 이미지)가 먼저, 그 뒤에 `files`(새 이미지)다. 번호는 0부터 다시 매긴다.
- `imageUrls`에는 그 상품의 기존 이미지 URL만 넣을 수 있다. 다른 URL이나 중복 URL을 넣으면 400이다.
- `imageUrls`에서 빠진 기존 이미지는 DB 커밋 후 Storage에서도 지운다.

```bash
# 기존 이미지 a.png만 남기고 new.png를 추가
curl -X PUT \
     -F 'product={"name":"티셔츠","price":12000,"stock":30,"imageUrls":["https://.../a.png"]};type=application/json' \
     -F files=@new.png \
     http://localhost:8080/api/products/3
```

### 상품 삭제 `DELETE /api/products/{id}`

- 행을 지우지 않고 `deleted = true`로 표시만 한다.
- 이미지 행과 Storage의 파일은 남는다.
- 삭제된 상품은 이후 조회, 목록, 수정, 삭제 요청에서 모두 없는 상품(404)으로 처리된다.

## 이미지 처리

### 업로드 규칙

| 항목 | 규칙 | 위반 시 |
|---|---|---|
| 형식 | Content-Type이 `image/*` (예: `image/png`, `image/jpeg`) | 400, 하나라도 위반하면 아무것도 올리지 않는다 |
| 파일 하나 크기 | `supabase.max-file-size` (현재 4MB) | 413 |
| 요청 전체 크기 | `supabase.max-request-size` (현재 30MB) | 413 |
| 빈 파일 | 허용하지 않음 | 400 |

- **형식 검사:** 클라이언트가 보낸 Content-Type만 검사하고 파일 내용은 검사하지 않는다.
- **저장 경로:** `{bucket}/yyyy/MM/dd/{uuid}.{확장자}`이다. 원본 파일명은 쓰지 않는다.
- **공개 URL:** `{supabase.url}/storage/v1/object/public/{bucket}/{경로}`이다.

### 등록 흐름

```
ProductCommandService.register
  1. 모든 파일 형식 검사          ── 실패 → 400 (업로드 없음)
  2. 파일을 순서대로 업로드
  3. ProductService.create (트랜잭션)
     └ 2~3에서 실패 → 이번에 올린 파일 삭제 후 예외 전달
```

### 수정 흐름

```
ProductCommandService.update
  1. 새 파일 형식 검사
  2. 새 파일 업로드
  3. ProductService.update (트랜잭션, 비관적 쓰기 잠금)
     - imageUrls가 기존 이미지인지, 중복이 없는지 검사
     - 이름, 가격, 재고 변경
     - 이미지 = 남길 기존 이미지 + 새 이미지
     - 빠진 기존 이미지 URL을 결과(ProductUpdateResult)로 반환
     └ 2~3에서 실패 → 이번에 올린 파일 삭제 후 예외 전달
  4. (커밋 완료 후) 빠진 기존 이미지 파일을 Storage에서 삭제
```

- 파일 정리(삭제)에 실패해도 요청 결과는 바꾸지 않고 경고 로그만 남긴다.
- `FileStorageService.deleteByUrl`은 이 버킷의 공개 URL이 아니면 아무것도 하지 않는다. 예전 데이터나 외부 URL이 여기에 해당한다.

## 오류 응답

### 상품 API 오류: `ProductErrorResponse`

`ProductControllerAdvice`가 처리하며 `ProductController`에만 적용된다.

```json
{ "status": 400, "message": "입력값을 확인해 주세요.", "fieldErrors": { "name": "상품명을 입력해 주세요." } }
```

| 상황 | 상태 | message |
|---|---|---|
| 입력값 검증 실패 | 400 | 입력값을 확인해 주세요. (`fieldErrors`에 필드별 메시지) |
| JSON 형식 오류, `/api/products/abc` | 400 | 요청 값의 형식이 올바르지 않습니다. |
| `product` 파트 누락 | 400 | 'product' 항목이 필요합니다. |
| 수정 시 상품에 없는 이미지 URL, 중복 URL | 400 | 상품에 없는 이미지입니다. url=… / 같은 이미지를 두 번 넣을 수 없습니다. |
| 재고 값 오류 | 400 | 재고는 0 이상이어야 합니다. … |
| 없는 상품, 삭제된 상품 | 404 | 상품을 찾을 수 없습니다. id=… |

### 파일 오류: `FileErrorResponse`

`FileStorageControllerAdvice`가 처리하며 모든 컨트롤러에 적용된다. 크기 초과 예외는 요청이 어느 컨트롤러로 갈지 정해지기 전에 발생하므로, 범위를 좁힌 advice로는 잡을 수 없다.

```json
{ "status": 413, "message": "파일은 하나당 4MB, 요청 전체는 30MB 이하여야 합니다." }
```

| 상황 | 상태 | message |
|---|---|---|
| 이미지가 아닌 파일, 빈 파일 | 400 | 이미지 파일만 업로드할 수 있습니다. 파일=… / 업로드할 파일을 선택해 주세요. |
| 크기 초과 | 413 | 파일은 하나당 …MB, 요청 전체는 …MB 이하여야 합니다. |
| Supabase 호출 실패 | 502 | 파일 업로드에 실패했습니다. (상세 내용은 서버 로그에만 남긴다) |

### 스프링 기본 오류

`product` 파트에 Content-Type이 없으면 스프링 기본 형식의 415 응답이 간다.

## 설정

모든 설정은 `application-local.yaml`에 둔다. 이 파일은 git에 올리지 않는다. `application.yaml`의 `spring.profiles.default: local`이 이 파일을 읽게 한다.

```yaml
spring:
  servlet:
    multipart:
      max-file-size: ${supabase.max-file-size}
      max-request-size: ${supabase.max-request-size}

supabase:
  url: https://{project-ref}.supabase.co
  service-role-key: {secret 키}       # sb_secret_... 형식
  bucket: images
  max-file-size: 4MB
  max-request-size: 30MB               # max-file-size 이상이어야 앱이 시작된다
```

- **키 헤더:** `sb_secret_...` 형식의 키는 JWT가 아니므로 `apikey` 헤더로만 보낸다. 예전 JWT 형식 키는 `Authorization: Bearer`로도 보낸다.
- **버킷:** Supabase `images` 버킷은 Public이다. 버킷 자체 제한은 파일당 4MB, `image/*`이며 앱 설정과 맞춰 두었다.
- **다른 환경:** `local`이 아닌 프로필로 실행하면 `supabase.*` 값이 없어서 앱이 시작되지 않는다. 다른 환경에는 같은 키를 따로 넣어야 한다.
- **RestClient:** Spring Boot 4는 `spring-boot-starter-restclient`가 없으면 `RestClient.Builder` 빈을 만들지 않는다. 그래서 `SupabaseStorageConfig`에서 `RestClient.builder()`로 직접 만든다.

## DB

테이블은 JPA `ddl-auto: update`로 만든다.

### product

| 컬럼 | 설명 |
|---|---|
| `id` | PK, IDENTITY |
| `name`, `price`, `stock` | NOT NULL |
| `deleted` | 삭제 표시. NOT NULL, DB 기본값 `false` |
| `created_at`, `updated_at` | Hibernate가 자동으로 기록 |

엔티티에 `@SQLRestriction("deleted = false")`가 있어서 삭제된 상품은 모든 엔티티 조회와 JPQL에서 빠진다.

### product_image

| 컬럼 | 설명 |
|---|---|
| `product_id` | 상품 id |
| `image_url` | 공개 URL |
| `image_index` | 0부터 시작하는 이미지 번호 |

- `(product_id, image_index)`에 유니크 제약이 있다.
- 상품의 `@ElementCollection`이라 상품과 함께 저장되고 교체된다.

### 상품명 검색 인덱스 (pg_trgm)

`ddl-auto`로는 만들 수 없어서 [`src/main/resources/db/product-name-trgm.sql`](../src/main/resources/db/product-name-trgm.sql)을 직접 실행한다. 여러 번 실행해도 안전하다.

```sql
create extension if not exists pg_trgm with schema extensions;
create index if not exists idx_product_name_trgm
    on public.product using gin (lower(name) extensions.gin_trgm_ops);
```

- 검색 쿼리 조건(`lower(p.name) like ...`)과 같은 식으로 만들어야 인덱스를 쓴다.
- 3글자 이상 검색어에서 효과가 크다. 1~2글자 검색어는 후보를 거의 줄이지 못한다.
- 새 DB를 만들면 이 SQL을 다시 실행해야 한다. 자동으로 적용되지 않는다.

## 테스트

```bash
./gradlew test
```

| 테스트 | 대상 | 방식 |
|---|---|---|
| `ProductTest` | 엔티티 (재고 증감, 검증, 이미지) | 단위 테스트 |
| `ProductRepositoryTest` | 저장, 이미지 교체, 조회 쿼리, 잠금 조회 | `@DataJpaTest`, 실제 DB, 롤백 |
| `ProductServiceTest` | 등록, 조회, 목록, 수정, 삭제, 검색 | `@DataJpaTest`, 실제 DB, 롤백 |
| `ProductCommandServiceTest` | 이미지 검사, 업로드, 실패 시 파일 정리 | Mockito 단위 테스트, DB와 Supabase 접속 없음 |
| `ProductControllerTest` | 요청 형식, 검증, 오류 응답 | `@WebMvcTest`, 서비스는 가짜 객체, 보안 필터 끔(`addFilters = false`) |

- **실제 DB 사용:** `@DataJpaTest` 테스트는 `application-local.yaml`의 실제 Supabase DB에 접속한다. 각 테스트는 끝나면 롤백된다.
- **검색 테스트:** 실제 DB에 다른 상품이 있어도 결과가 섞이지 않도록 상품명에 고유 문자열을 붙인다.

### `ProductServiceTest`가 확인하는 수정, 검색, 삭제

- **수정**
  - 재고가 바뀐다.
  - 남길 기존 이미지는 요청 순서대로, 새 이미지는 그 뒤에 붙는다.
  - 빠진 이미지 URL을 결과로 돌려준다.
  - 상품에 없는 URL과 중복 URL은 거부하고, 이때 데이터는 바뀌지 않는다.
- **검색**
  - 영문 대소문자를 구분하지 않는다.
  - 검색어 앞뒤 공백을 무시한다.
  - 검색어의 `%`와 `_`는 문자 그대로 찾는다.
- **삭제**
  - 행과 이미지는 남고 `deleted`만 `true`가 된다. `@SQLRestriction`을 거치지 않도록 SQL로 직접 확인한다.
  - 삭제된 상품은 목록과 개수에서 빠지고, 수정이나 재삭제 요청에는 404를 돌려준다.

### `ProductCommandServiceTest`가 확인하는 흐름

- **형식 검사**
  - 이미지가 아닌 파일이 하나라도 있으면 아무것도 올리지 않는다.
  - Content-Type이 없거나, 와일드카드(`image/*`)이거나, 잘못된 형식이면 거부한다.
- **등록 실패 시 정리**
  - DB 저장이 실패하면 올린 파일을 모두 지운다.
  - 중간 업로드가 실패하면 이미 올린 파일을 지운다.
  - 정리에 실패해도 원래 예외를 그대로 전달한다.
- **수정 시 정리**
  - 성공하면 빠진 기존 이미지만 지운다.
  - 실패하면 이번에 올린 새 파일만 지운다.
  - 빠진 이미지 삭제에 실패해도 수정은 성공한다.

### 테스트로 확인하지 않는 것

- **크기 초과(413):** `MaxUploadSizeExceededException`은 실제 서버의 multipart 처리에서 발생하므로 MockMvc로는 재현하지 않는다. 앱을 실행해서 확인했다.
- **실제 Supabase 업로드와 삭제:** `FileStorageService`를 실제 Storage에 연결해서 확인하는 테스트는 없다. 앱을 실행해서 등록과 공개 URL 접근만 확인했다.
- **동시 수정 시 잠금 대기:** 실제 잠금 대기는 검증하지 않는다.

## 알려진 제한

- **파일 내용 미검사:** 이미지 형식은 Content-Type만 보고 파일 내용(매직 바이트)은 검사하지 않는다.
- **삭제 후 파일:** 삭제된 상품의 이미지 파일은 Storage에 남는다. 정리 기능은 없다.
- **정리 실패 시 고아 파일:** 업로드, 수정 중 파일 정리에 실패하면 쓰이지 않는 파일이 Storage에 남을 수 있다. 이 경우 경고 로그가 남는다.
- **415 응답 형식:** `product` 파트의 Content-Type이 없을 때의 415 응답은 프로젝트 오류 형식이 아니다.
- **수동 인덱스:** pg_trgm 인덱스는 마이그레이션 도구 없이 수동으로 적용한다.
