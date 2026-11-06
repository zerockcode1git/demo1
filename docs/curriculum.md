# Spring Boot 입문 6일 커리큘럼

이 저장소(demo1)를 만든 과정을 따라가며 Spring Boot로 REST API 서버를 만드는 과정이다.

## 과정 개요

| 항목 | 내용 |
|---|---|
| 대상 | 대학교 1학년. Spring은 처음이고, Java 문법(클래스, 인터페이스, 컬렉션)과 SQL 기초(SELECT, INSERT, JOIN)는 알고 있다. |
| 기간 | 6일, 하루 7시간 (오전 3시간 + 오후 4시간) |
| 완성 결과물 | 할 일(Todo) API, 이미지 업로드가 되는 상품 API, JWT 로그인과 권한 처리, 테스트, API 문서(Swagger) |
| 기술 스택 | Java 21, Spring Boot 4, Gradle, Spring Data JPA, PostgreSQL(Supabase), Supabase Storage, springdoc(Swagger), Spring Security, JWT(jjwt), JUnit 5, Mockito |

### 진행 원칙

- **한 단계씩 만들고 확인한다.** 엔티티 → 리포지토리 → 서비스 → 컨트롤러 순서로 하나씩 만든다. 각 단계마다 실행하거나 테스트해서 확인한 뒤 다음으로 넘어간다.
- **Todo로 익히고 상품에 적용한다.** 1~2일 차에 Todo로 기본 구조를 익힌다. 3~5일 차에는 같은 구조를 상품에 적용하면서 기능을 하나씩 늘린다. 6일 차에는 지금까지 만든 API에 로그인과 권한을 입힌다.
- **실제로 겪은 문제를 교재로 쓴다.** 각 일차의 "실전에서 만난 문제"는 이 프로젝트를 만들면서 실제로 생긴 문제다. 답을 바로 보여 주지 말고, 학생이 먼저 오류 메시지를 읽게 한다.

### 일정 한눈에 보기

| 일차 | 주제 | 결과물 |
|---|---|---|
| 1일 | Spring Boot 시작, 계층 구조, DB 연결 | 실행되는 프로젝트, Todo 엔티티와 테이블 |
| 2일 | REST API와 CRUD | Todo CRUD API, 검증, 예외 처리, 페이징, Swagger |
| 3일 | 도메인 모델링과 JPA 심화 | 상품 엔티티와 이미지, 재고 규칙, 조회 쿼리, 리포지토리 테스트 |
| 4일 | 파일 업로드 | Supabase Storage 연동, 상품 등록 API(이미지 포함) |
| 5일 | 기능 완성과 테스트 | 검색, 수정, 삭제 표시, 계층별 테스트, Git 정리 |
| 6일 | 사용자와 인증·보안 | 사용자 모델, JWT 로그인, 인증 필터, 권한, CORS |

## 1일 차: Spring Boot 시작과 DB 연결

### 학습 목표

- Spring Boot 프로젝트를 만들고 실행할 수 있다.
- 컨트롤러, 서비스, 리포지토리, 엔티티가 각각 무슨 일을 하는지 설명할 수 있다.
- JPA 엔티티를 만들고, 테이블이 자동으로 생성되는 것을 확인할 수 있다.
- 설정 파일과 프로필을 나누고, 비밀 정보를 git에서 뺄 수 있다.

### 시간표

| 시간 | 내용 |
|---|---|
| 오전 1 | Spring과 Spring Boot가 무엇인지, IoC와 DI를 "객체를 대신 만들어 주는 것"으로 설명 |
| 오전 2 | Spring Initializr로 프로젝트 생성 (Web MVC, Data JPA, Validation, PostgreSQL, Lombok), Gradle 구조 살펴보기 |
| 오전 3 | 계층 구조: 요청이 Controller → Service → Repository → DB로 흐르는 그림 그리기 |
| 오후 1 | Supabase 프로젝트 생성, PostgreSQL 접속 정보 확인 |
| 오후 2 | `application.yaml`과 `application-local.yaml` 나누기, `.gitignore`에 local 파일 추가 |
| 오후 3 | `Todo` 엔티티 작성 (`@Entity`, `@Id`, `@GeneratedValue`, `@Column`, `@CreationTimestamp`) |
| 오후 4 | `ddl-auto: update`로 테이블이 생기는 것 확인, 정리와 질의응답 |

### 핵심 개념

- **빈과 의존성 주입:** `@Service`, `@Repository`가 붙은 클래스는 Spring이 직접 만든다. 생성자로 필요한 객체를 받는다. 롬복 `@RequiredArgsConstructor`로 생성자를 줄인다.
- **엔티티:** 테이블 한 줄에 해당하는 클래스다. 기본 생성자는 `protected`로 막는다. 값은 `changeContent()`처럼 이름이 있는 메서드로만 바꾼다. 아무 값이나 바꾸는 setter는 두지 않는다.
- **프로필:** `spring.profiles.default: local`을 쓰면 아무 프로필도 지정하지 않았을 때 `application-local.yaml`을 읽는다.
- **비밀 정보 관리:** DB 비밀번호와 API 키는 `application-local.yaml`에만 둔다. 이 파일은 git에 올리지 않는다.

### 실습

1. 프로젝트를 만들고 `Demo1Application`을 실행한다.
2. `application-local.yaml`에 Supabase DB 접속 정보를 넣는다. 커넥션 풀은 무료 요금제에 맞게 2개로 둔다.
3. `Todo` 엔티티를 만든다(`content`, `completed`, `createdAt`, `updatedAt`).
4. 앱을 실행하고 Supabase 대시보드에서 `todo` 테이블이 생겼는지 확인한다.

### 체크포인트

- [ ] 앱이 오류 없이 시작된다.
- [ ] `todo` 테이블이 생겼다.
- [ ] `git status`에 `application-local.yaml`이 보이지 않는다.

### 과제

- 계층 구조 그림을 직접 그리고, 각 계층이 하면 안 되는 일을 하나씩 적어 온다. 예: 컨트롤러가 리포지토리를 직접 호출하지 않는다.

## 2일 차: REST API와 CRUD

### 학습 목표

- REST 방식으로 URL과 HTTP 메서드를 설계할 수 있다.
- 요청과 응답에 DTO를 쓰는 이유를 설명할 수 있다.
- 입력값을 검증하고, 오류를 일정한 형식으로 응답할 수 있다.
- 페이징 조회를 만들고 Swagger로 API를 확인할 수 있다.

### 시간표

| 시간 | 내용 |
|---|---|
| 오전 1 | REST 설계: `POST /api/todos`, `GET /api/todos/{id}`, `GET /api/todos`, `PUT`, `DELETE`와 상태 코드(201, 200, 204, 400, 404) |
| 오전 2 | `TodoRepository` (`JpaRepository` 상속만으로 CRUD 가능) |
| 오전 3 | `TodoService`와 `@Transactional(readOnly = true)` 기본값, 변경 메서드만 `@Transactional` |
| 오후 1 | 요청·응답 DTO를 record로 작성, 엔티티를 그대로 반환하지 않는 이유 |
| 오후 2 | `TodoController` 작성, Postman이나 curl로 호출 |
| 오후 3 | `@Valid`와 검증 어노테이션, `@RestControllerAdvice`로 400·404 오류 응답 통일 |
| 오후 4 | 페이징(`Pageable`, 페이지 응답 DTO), springdoc으로 Swagger UI 확인 |

### 핵심 개념

- **DTO 반환 원칙:** 서비스와 컨트롤러는 항상 직접 만든 DTO를 반환한다. 엔티티는 물론 `Page<T>` 같은 프레임워크 타입도 그대로 내보내지 않는다. `TodoPageResponse`처럼 페이지 응답도 DTO로 감싼다.
- **트랜잭션 기본값:** 클래스에는 `readOnly = true`를 걸고, 데이터를 바꾸는 메서드만 `@Transactional`로 바꾼다. 변경 감지(dirty checking) 덕분에 `save()`를 다시 부르지 않아도 수정 내용이 저장된다.
- **오류 응답 형식:** `{ status, message, fieldErrors }`로 통일한다. `?page=abc` 같은 변환 오류는 내부 타입 정보가 들어 있으므로 노출하지 않고 일반 메시지로 바꾼다.
- **페이지 번호 기준:** Spring Data는 0부터 센다. 화면과 사용자는 1부터 세는 것이 자연스러우므로 요청은 `page - 1`, 응답은 `getNumber() + 1`로 바꾼다.

### 실습

1. Todo CRUD API 5개를 만든다.
2. 빈 `content`는 400으로, 없는 id는 404로 응답하게 한다.
3. `GET /api/todos?page=1&size=10`을 만들고 최근 등록순으로 정렬한다.
4. `/swagger-ui/index.html`에서 모든 API를 호출해 본다.

### 실전에서 만난 문제

- **"page=1로 조회했는데 2개만 나와요":** 처음에는 페이지가 0부터 시작했다. 12개 중 `page=1, size=10`은 두 번째 페이지라 2개가 맞았다. 시작 번호를 API 규칙으로 정하고 문서에 적어야 한다는 사례다. 이후 1부터 시작하도록 바꿨다.

### 체크포인트

- [ ] 5개 API가 Swagger에서 모두 동작한다.
- [ ] 검증 오류 응답에 필드별 메시지가 나온다.
- [ ] 컨트롤러와 서비스의 반환 타입이 모두 DTO다.

### 과제

- `PUT`과 `PATCH`의 차이를 조사하고, Todo 수정에 어느 것이 맞는지 이유와 함께 적어 온다.

## 3일 차: 도메인 모델링과 JPA 심화

### 학습 목표

- 규칙을 가진 엔티티를 설계할 수 있다. 예: 재고는 0 미만이 될 수 없다.
- 값 객체 목록(`@ElementCollection`)을 매핑할 수 있다.
- 필요한 데이터만 가져오는 조회 쿼리를 만들 수 있다.
- 실제 DB로 리포지토리를 테스트할 수 있다.

### 시간표

| 시간 | 내용 |
|---|---|
| 오전 1 | 상품 요구사항 분석: 이름, 가격, 재고, 이미지 여러 장(순서 있음) |
| 오전 2 | `Product` 엔티티와 재고 메서드(`changeStock`, `increaseStock`, `decreaseStock`), 도메인 예외 |
| 오전 3 | `ProductImage`를 `@Embeddable` record로, `@ElementCollection`, `@OrderBy`, 유니크 제약 |
| 오후 1 | 지연 로딩과 N+1 문제, `@EntityGraph`로 상품과 이미지를 한 번에 조회 |
| 오후 2 | 목록 조회는 DTO로 바로 받기: JPQL 생성자 표현식과 `left join ... on i.imageIndex = 0` |
| 오후 3 | (심화) 비관적 쓰기 잠금 `@Lock(PESSIMISTIC_WRITE)`: 두 사람이 동시에 재고를 바꾸면 생기는 일 |
| 오후 4 | `@DataJpaTest`로 리포지토리 테스트, 테스트 롤백, `flush`와 `clear`를 쓰는 이유 |

### 핵심 개념

- **도메인 규칙은 엔티티에:** 재고 검증은 엔티티 메서드 안에서 한다. 어느 서비스에서 호출하든 규칙이 지켜진다.
- **목록 순서 관리:** 이미지 번호(`imageIndex`)는 엔티티가 직접 매긴다. 이미지 목록은 읽기 전용으로 내보내서, 엔티티 메서드를 거치지 않고는 바꿀 수 없게 한다.
- **조회 성능:** 상세 조회는 이미지 전체를, 목록 조회는 대표 이미지(0번) 하나만 가져온다.
- **테스트 격리:** `@DataJpaTest`는 테스트마다 롤백한다. 실제 DB를 써도 데이터가 남지 않는다. 다만 1차 캐시 때문에 DB를 다시 읽지 않을 수 있으므로 `flush()`와 `clear()` 뒤에 조회한다.

### 실습

1. `Product`, `ProductImage`, 재고 관련 예외를 만든다.
2. `ProductRepository`에 상세 조회, 잠금 조회, 대표 이미지 목록 조회를 만든다.
3. 엔티티 단위 테스트(`ProductTest`)와 리포지토리 테스트를 작성하고 실행한다.

### 체크포인트

- [ ] 재고를 음수로 바꾸면 예외가 난다.
- [ ] 이미지가 없는 상품도 목록에 나온다(`left join`).
- [ ] 리포지토리 테스트가 통과하고, DB에 테스트 데이터가 남지 않는다.

### 과제

- SQL 로그(`spring.jpa.show-sql`)를 켜고 상세 조회를 `@EntityGraph`가 있을 때와 없을 때로 나눠 실행해 본다. 쿼리 수를 비교해 적어 온다.

## 4일 차: 파일 업로드

### 학습 목표

- 설정 값을 타입이 있는 객체로 받아 검증할 수 있다(`@ConfigurationProperties`).
- 외부 REST API를 호출할 수 있다(`RestClient`).
- multipart 요청으로 JSON과 파일을 함께 받을 수 있다.
- 외부 호출을 DB 트랜잭션 밖에서 하는 이유를 설명할 수 있다.

### 시간표

| 시간 | 내용 |
|---|---|
| 오전 1 | Supabase Storage 버킷 만들기(Public), 버킷 제한(크기, `image/*`) 확인 |
| 오전 2 | `SupabaseStorageProperties` record와 `@Validated`, 설정이 비면 앱이 시작되지 않게 하기 |
| 오전 3 | `RestClient`로 업로드와 삭제, UUID 파일명과 날짜 폴더, 공개 URL 만들기 |
| 오후 1 | multipart 컨트롤러: `@RequestPart("product")` JSON + `@RequestPart("files")` 파일 |
| 오후 2 | 업로드 크기 제한(`spring.servlet.multipart`), 이미지 형식 검사, 413·400·502 응답 |
| 오후 3 | 서비스 분리: 업로드(트랜잭션 없음) → 저장(트랜잭션), 실패하면 올린 파일 지우기 |
| 오후 4 | Swagger에서 multipart 테스트(`@Encoding`), curl로 실제 업로드 확인 |

### 핵심 개념

- **트랜잭션 밖 업로드:** 업로드는 네트워크를 기다리는 작업이다. 트랜잭션 안에서 하면 그동안 DB 커넥션을 붙잡는다. 커넥션 풀이 2개면 요청 두 개로 DB가 막힌다.
- **같은 클래스 안 호출 문제:** `@Transactional`은 프록시로 동작한다. 같은 클래스 안에서 `this.create()`로 부르면 프록시를 거치지 않아 트랜잭션이 걸리지 않는다. 그래서 업로드를 맡는 서비스를 따로 만든다.
- **실패 시 정리:** DB 저장이 실패하면 이미 올린 파일을 지운다. 정리 작업이 실패해도 원래 오류를 가리지 않도록 로그만 남긴다.
- **설정 일치:** 앱의 크기 제한과 버킷의 크기 제한이 다르면, 앱에서 통과한 파일을 Supabase가 거부한다. 두 값을 맞춘다.

### 실습

1. `FileStorageService`(업로드, 삭제)와 설정 클래스를 만든다.
2. `POST /api/products`를 multipart로 받아 이미지와 함께 등록한다.
3. 정상 등록, 이미지 없이 등록, txt 파일, 5MB 파일, 검증 실패, `product` 파트 누락, Content-Type 누락을 각각 호출해 본다. 7가지 경우의 응답 코드를 표로 정리한다.
4. 응답의 이미지 URL을 브라우저에서 열어 본다.

### 실전에서 만난 문제

- **앱이 시작되지 않음 — `RestClient.Builder` 빈이 없다:** 컴파일은 됐지만 실행하자마자 실패했다. Spring Boot 4는 `spring-boot-starter-restclient`가 없으면 이 빈을 만들지 않는다. `RestClient.builder()`로 직접 만들어 해결했다. 컴파일 성공이 곧 동작 확인은 아니라는 사례다.
- **설정 키 이름 불일치:** 설정 파일의 키를 `service-key`에서 `service-role-key`로 바꾸자 코드와 이름이 맞지 않았다. `@Validated` 덕분에 앱 시작 단계에서 바로 드러난다.
- **새 형식 API 키:** Supabase의 `sb_secret_...` 키는 JWT가 아니다. `Authorization: Bearer` 헤더에 넣으면 거부되므로 `apikey` 헤더로만 보낸다.
- **크기 제한 불일치:** 앱은 5MB, 버킷은 4MB라 4~5MB 파일이 400이 아닌 502로 실패했다. 버킷 정보를 API로 조회해 차이를 찾았다.
- **Swagger에서 415:** Swagger UI는 기본적으로 `product` 파트를 Content-Type 없이 보낸다. `@Encoding(name = "product", contentType = "application/json")`으로 해결했다.

### 체크포인트

- [ ] 이미지 2장과 함께 상품이 등록되고, 공개 URL이 열린다.
- [ ] 이미지가 아닌 파일은 아무것도 업로드되지 않고 400으로 거부된다.
- [ ] 키와 비밀번호가 git에 올라가지 않았다.

### 과제

- 서비스를 나누지 않고 컨트롤러에서 업로드한 뒤 서비스를 호출하는 방식과, 지금 방식의 장단점을 비교해 적어 온다.

## 5일 차: 기능 완성과 테스트

### 학습 목표

- 검색 조건이 있는 목록 조회를 만들 수 있다.
- 실제로 지우지 않고 삭제 표시만 하는 삭제(soft delete)를 구현할 수 있다.
- 계층마다 알맞은 테스트 방식을 고를 수 있다.
- 작업을 브랜치에 커밋하고 병합할 수 있다.

### 시간표

| 시간 | 내용 |
|---|---|
| 오전 1 | 상품명 검색(`keyword`): 대소문자 무시, LIKE 와일드카드(`%`, `_`) 이스케이프 |
| 오전 2 | (심화) PostgreSQL `pg_trgm` 인덱스와 실행 계획(`EXPLAIN`) 확인 |
| 오전 3 | 상품 수정: 남길 기존 이미지 + 새 이미지, 상품에 없는 URL 거부, 빠진 이미지는 커밋 후 삭제 |
| 오후 1 | 삭제 표시: `deleted` 컬럼, `@ColumnDefault`, `@SQLRestriction`으로 모든 조회에서 제외 |
| 오후 2 | 테스트 종류: 엔티티 단위 테스트, Mockito 단위 테스트, `@WebMvcTest`, `@DataJpaTest` |
| 오후 3 | 테스트 작성과 실행, 실패 원인 읽기 |
| 오후 4 | 기능 문서 작성, Git 브랜치 → 커밋 → 푸시 → `main` 병합, 회고 |

### 핵심 개념

- **사용자 입력은 문자 그대로:** 검색어에 `%`를 넣으면 SQL 와일드카드로 동작해 전체가 조회된다. 사용자 입력은 이스케이프해서 문자 그대로 찾게 한다.
- **인덱스와 식 일치:** 쿼리 조건이 `lower(name) like ...`이면 인덱스도 `lower(name)`으로 만들어야 그 인덱스를 쓴다. trigram 인덱스는 3글자 이상 검색어에서 효과가 크다.
- **삭제 표시:** 행을 지우지 않으므로 기록이 남는다. 대신 모든 조회에서 빠뜨려야 한다. `@SQLRestriction`을 엔티티에 붙이면 한 곳에서 처리된다. 기존 행이 있는 테이블에 NOT NULL 컬럼을 추가하려면 DB 기본값이 필요하다.
- **테스트 피라미드:**

| 테스트 | 무엇을 확인하나 | 속도 | 예 |
|---|---|---|---|
| 단위 테스트 | 엔티티 규칙, 흐름 분기 | 빠름 | `ProductTest`, `ProductCommandServiceTest` |
| 웹 계층 테스트 | 요청 형식, 검증, 오류 응답 | 보통 | `ProductControllerTest` |
| DB 테스트 | 쿼리, 트랜잭션, 매핑 | 느림 | `ProductRepositoryTest`, `ProductServiceTest` |

### 실습

1. `GET /api/products?keyword=셔츠`를 만들고 `%`, `_`가 들어간 검색어로 확인한다.
2. `PUT /api/products/{id}`(multipart)와 `DELETE /api/products/{id}`를 만든다.
3. 삭제한 상품이 조회, 목록, 수정에서 모두 404인지 확인한다. DB에는 행이 남아 있는지도 SQL로 확인한다.
4. 테스트를 작성하고 `./gradlew test`로 전체를 실행한다.
5. `feature/product` 브랜치에 커밋하고 푸시한 뒤 `main`에 병합한다.

### 실전에서 만난 문제

- **관계없는 테스트 11개가 한꺼번에 실패:** 파일 오류를 처리하는 advice를 모든 컨트롤러에 적용했다. 그러자 Todo 컨트롤러 테스트에도 이 advice가 올라왔다. `@WebMvcTest`에는 Supabase 설정 빈이 없어 테스트 환경이 뜨지 못했다. `ObjectProvider`로 받아 해결했다. 전역 설정이 다른 테스트에 영향을 준다는 사례다.
- **규칙이 바뀌면 테스트도 바뀐다:** "수정해도 재고는 유지된다" 테스트는 재고를 수정할 수 있게 바뀐 뒤 의미가 없어졌다. 테스트는 통과했지만 이름과 내용이 맞지 않았다. 통과 여부만이 아니라 테스트가 무엇을 확인하는지도 봐야 한다.
- **실제 DB에서의 검색 테스트:** 실제 DB에 다른 상품이 있어서 검색 결과가 섞였다. 테스트마다 고유 문자열을 상품명에 붙여 격리했다.

### 체크포인트

- [ ] 전체 테스트가 통과한다(이 저장소 기준 90개).
- [ ] 문서(`docs/product.md`)에 API, 오류 응답, 설정, 테스트 범위가 정리되어 있다.
- [ ] `main` 브랜치에 커밋이 반영되었고, 비밀 정보가 없다.

### 과제

- 삭제된 상품의 이미지 파일은 Storage에 그대로 남는다. 이 파일들을 정리하는 방법을 설계해 본다. 예: 일정 기간이 지난 삭제 상품의 파일을 지우는 배치 작업. 언제, 무엇을, 어떤 순서로 지울지 적는다.

## 6일 차: 사용자와 인증·보안

### 학습 목표

- 인증(누구인가)과 인가(무엇을 할 수 있는가)를 구분해 설명할 수 있다.
- 비밀번호를 BCrypt로 암호화해 저장할 수 있다.
- JWT의 구조를 설명하고, 로그인하면 Access Token과 Refresh Token을 발급할 수 있다.
- Spring Security 필터로 요청마다 토큰을 검증하고 SecurityContext에 사용자를 넣을 수 있다.
- 화이트 리스트와 `@PreAuthorize`로 기능별 권한을 지정할 수 있다.
- CORS가 무엇이고 왜 필요한지 설명할 수 있다.

### 시간표

| 시간 | 내용 |
|---|---|
| 오전 1 | 인증과 인가, 세션 방식과 토큰 방식 비교, JWT 구조(header.payload.signature)를 직접 디코딩해 보기 |
| 오전 2 | 사용자 엔티티: 이메일 기본 키, 권한 목록(`@ElementCollection` + enum), BCrypt 비밀번호 |
| 오전 3 | `JwtProvider`: 토큰 발급과 검증, 서명 키 설정, 토큰 종류(`type`) 구분 |
| 오후 1 | 로그인 API(`POST /api/auth/login`), 로그인 실패 메시지를 하나로 통일하는 이유 |
| 오후 2 | Spring Security 필터 체인, `JWTCheckFilter`(없으면 GUEST)와 `AuthCheckFilter`(검증 후 SecurityContext 설정) |
| 오후 3 | 화이트 리스트, `@PreAuthorize`, 401과 403의 차이, 필터에서 JSON 오류 응답 쓰기 |
| 오후 4 | CORS와 사전 요청(OPTIONS), 세션을 쓰지 않는 설정(STATELESS), curl로 전체 흐름 확인 |

### 핵심 개념

- **비밀번호는 해시로만 저장한다:** BCrypt는 같은 비밀번호라도 매번 다른 결과를 만든다. 그래서 비교는 `matches()`로 한다.
- **이메일 비교와 표시는 다르다:** 비교용 키(`email_key`, 소문자)와 표시용 이메일(입력한 그대로)을 나눠 저장한다. 대소문자가 달라도 같은 사용자로 보면서, 사용자가 입력한 모습은 그대로 보여 준다.
- **JWT는 서명될 뿐 암호화되지 않는다:** 누구나 페이로드를 읽을 수 있다. 이메일, 닉네임, 권한만 넣고 비밀번호는 넣지 않는다. 서명 키가 새면 누구나 ADMIN 토큰을 만들 수 있다.
- **필터의 역할 분담:**

| 필터 | 실행 범위 | 하는 일 |
|---|---|---|
| `JWTCheckFilter` | 모든 요청 | JWT가 없으면 GUEST로 표시하고 통과 |
| `AuthCheckFilter` | 화이트 리스트 밖 | JWT가 없거나 잘못됐으면 401, 맞으면 SecurityContext에 사용자 설정 |

- **권한 지정 방법:** URL 규칙은 "화이트 리스트 허용, 나머지 로그인 필요"만 둔다. 기능별 권한은 컨트롤러 메서드에 `@PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")`로 단다.
- **401과 403:** 401은 "누군지 모름"(토큰 없음, 만료, 위조)이고, 403은 "누군지는 알지만 권한이 없음"이다. 클라이언트는 401 중 `TOKEN_EXPIRED`일 때 토큰 재발급을 시도한다.
- **CORS는 브라우저 규칙이다:** 다른 출처의 프런트엔드가 호출하려면 서버가 허용 출처를 알려 줘야 한다. curl이나 Postman은 CORS 검사를 하지 않는다.

### 실습

1. `User`, `UserRole`, `UserRepository`, `UserService`를 만든다.
2. `JwtProvider`를 만들고, 서명 키는 `application-local.yaml`에 둔다.
3. 테스트 계정 세 개를 만든다(ADMIN, MANAGER, USER).
4. 로그인 API를 만들고, 받은 Access Token을 jwt.io 같은 디코더로 열어 본다.
5. 두 필터와 `SecurityConfig`를 만든다. 할 일 목록은 GUEST도, 등록·수정·삭제는 로그인 사용자만 할 수 있게 한다.
6. 아래 경우를 curl로 호출해 응답 코드를 표로 정리한다.
   - 토큰 없이 할 일 조회와 등록
   - Refresh Token으로 등록
   - 위조한 토큰으로 등록
   - USER 토큰으로 등록
   - CORS 사전 요청

### 실전에서 만난 문제

- **"내 이메일을 마음대로 바꿨다":** 처음에는 이메일을 소문자로 바꿔 저장했다. 사용자가 입력한 대소문자가 사라진다는 지적을 받고, 비교용 키와 표시용 이메일을 나눴다. 기술적으로 맞는 설계와 사용자가 받아들이는 설계가 다를 수 있다는 사례다.
- **같은 이메일로 가입하면 기존 사용자를 덮어쓴다:** 기본 키를 직접 넣는 엔티티는 Spring Data의 `save()`가 새 데이터인지 구분하지 못한다. 그래서 insert 대신 merge(덮어쓰기)를 한다. `Persistable`을 구현해 해결했다.
- **서명 키를 git에 올릴 뻔했다:** 처음에는 서명 키를 `application.yaml`에 두었다. 커밋하기 전에 `application-local.yaml`로 옮겼다.
- **숫자로만 된 키:** `1234...`처럼 숫자로만 된 값은 따옴표가 없으면 YAML이 숫자로 읽는다. 문자열로 감쌌다.
- **앱이 시작되지 않음 — 생성자가 두 개:** `JwtProvider`에 테스트용 생성자를 하나 더 만들었더니 Spring이 어느 것을 쓸지 고르지 못했다. 컴파일은 됐지만 실행에서 실패했다. 쓸 생성자에 `@Autowired`를 붙여 해결했다.
- **보안을 넣자 기존 테스트가 모두 401:** 보안 스타터가 들어가면 `@WebMvcTest`에 기본 보안 규칙이 적용된다. 컨트롤러 테스트에 `@AutoConfigureMockMvc(addFilters = false)`를 붙였다. 대신 이 테스트로는 권한을 검증할 수 없다.
- **필터를 `@Component`로 만들면 두 번 실행된다:** 일반 서블릿 필터로도 등록되기 때문이다. `SecurityConfig`에서 직접 만들어 추가했다.
- **GUEST가 조회를 못 함:** `@PreAuthorize`를 조회 메서드에 걸지 않아도, 화이트 리스트에 없으면 `AuthCheckFilter`가 먼저 401로 막는다. 필터와 권한 검사의 순서를 이해해야 하는 사례다.
- **`/error`를 빼면 모든 오류가 401로 보인다:** 404나 500이 나면 스프링이 `/error`로 다시 보내는데, 이 경로가 막히면 401이 된다. 화이트 리스트에 넣었다.

### 체크포인트

- [ ] 세 계정으로 로그인하면 토큰이 발급되고, 페이로드에 이메일·닉네임·권한이 있다.
- [ ] 토큰 없이 할 일 목록은 200, 등록은 401이다.
- [ ] Refresh Token으로 API를 호출하면 401 `TOKEN_INVALID`이다.
- [ ] 응답에 `Set-Cookie`가 없다(세션 미사용).
- [ ] 서명 키와 비밀번호가 git에 없다.

### 최종 과제

- **토큰 재발급 API 설계:** Refresh Token으로 새 Access Token을 받는 API(`POST /api/auth/refresh`)를 설계한다. 로그아웃하면 Refresh Token을 어떻게 무효로 만들지, 권한이 바뀐 사용자에게는 어떤 권한을 담을지도 적는다.
- **할 일 작성자 설계:** 지금은 로그인만 하면 다른 사람의 할 일도 수정하고 삭제할 수 있다. 작성자를 저장하고, 본인과 ADMIN만 수정·삭제할 수 있게 하는 방법을 설계한다.

## 부록

### 사전 준비

| 항목 | 내용 |
|---|---|
| 개발 도구 | JDK 21, IntelliJ IDEA 또는 VS Code, Git |
| 계정 | GitHub, Supabase(무료 요금제) |
| API 호출 도구 | curl 또는 Postman |
| 사전 학습 | Java 클래스·인터페이스·컬렉션, SQL SELECT·INSERT·JOIN, HTTP 요청과 응답의 기본 구조 |

### 평가 기준 (예시)

| 항목 | 비중 | 기준 |
|---|---|---|
| 일일 체크포인트 | 40% | 각 일차 체크포인트 달성 여부 |
| 최종 결과물 | 30% | 상품 API 5개 동작, 로그인과 권한 동작, 오류 응답 형식 통일, 비밀 정보 미포함 |
| 테스트 | 20% | 계층별 테스트 작성, 전체 통과 |
| 과제와 회고 | 10% | 설계 비교 과제, 최종 과제 |

### 강의 팁

- **오류 메시지를 먼저 읽게 한다:** "실전에서 만난 문제"는 일부러 같은 오류를 재현해 보여 준다. 학생이 먼저 오류 메시지를 읽고 원인을 추측하게 한다.
- **심화 내용은 선택이다:** 비관적 잠금, pg_trgm, 로그인 응답 시간 맞추기는 시간이 부족하면 개념만 설명하고 넘어가도 된다.
- **6일 차는 양이 많다:** 내용이 가장 많은 날이다. 사용자 엔티티(오전 2)를 미리 만들어 두고 시작하거나, CORS(오후 4)를 과제로 돌려도 된다.
- **실제 DB를 쓰는 테스트에 주의한다:** 학생마다 Supabase 프로젝트를 따로 쓰게 한다. 무료 요금제의 커넥션 수가 적으므로 커넥션 풀 크기를 작게 둔다.
- **참고 코드:** 이 저장소(demo1)의 `main` 브랜치와 [docs/product.md](product.md), [docs/auth.md](auth.md)를 보면 된다.
