# 사용자, 인증, 보안

사용자, 로그인과 JWT 발급, 토큰 갱신과 Refresh Token 저장(Redis), 요청 인증(필터), 권한, CORS를 정리한 문서다.

## 목차

1. [전체 흐름](#전체-흐름)
2. [패키지 구조](#패키지-구조)
3. [사용자](#사용자)
4. [로그인 API](#로그인-api)
5. [토큰 갱신 API](#토큰-갱신-api)
6. [Refresh Token 저장 (Redis)](#refresh-token-저장-redis)
7. [JWT](#jwt)
8. [요청 인증: 필터 두 개](#요청-인증-필터-두-개)
9. [권한](#권한)
10. [CORS와 세션](#cors와-세션)
11. [설정](#설정)
12. [테스트 계정](#테스트-계정)
13. [테스트](#테스트)
14. [남은 작업과 제한](#남은-작업과-제한)

## 전체 흐름

```
1. 로그인       POST /api/auth/login {email, password}
                → 비밀번호 확인(BCrypt) → Access Token(10분) + Refresh Token(60분)
                → Refresh Token을 Redis에 저장 (TTL 60분)

2. API 호출     Authorization: Bearer {accessToken}
                → CorsFilter → JWTCheckFilter → AuthCheckFilter → 권한 검사(URL 규칙, @PreAuthorize) → 컨트롤러

3. 토큰 갱신    Access Token 만료(401 TOKEN_EXPIRED) → POST /api/auth/refresh {refreshToken}
                → Redis에 저장된 토큰과 비교 → 새 Access Token + 새 Refresh Token (이전 Refresh Token은 못 씀)
                → 실패하면 401, 다시 로그인
```

## 패키지 구조

```
com.example.demo1
├── user
│   ├── entity       User, UserRole
│   ├── repository   UserRepository
│   ├── service      UserService (컨트롤러는 아직 없음)
│   ├── dto          UserCreateRequest, UserUpdateRequest, UserResponse, UserPageRequest, UserPageResponse
│   ├── exception    UserNotFoundException, DuplicateEmailException, InvalidUserRoleException
│   └── config       PasswordEncoderConfig (BCrypt)
├── auth
│   ├── controller   AuthController, AuthControllerAdvice
│   ├── service      AuthService
│   ├── repository   RefreshTokenRepository (Redis)
│   ├── dto          LoginRequest, RefreshRequest, AuthErrorResponse
│   └── exception    LoginFailedException, TokenRefreshFailedException
├── security
│   ├── config       SecurityConfig, SecurityWhitelistProperties, CorsProperties
│   ├── filter       JWTCheckFilter, AuthCheckFilter, BearerTokens, SecurityErrorWriter
│   ├── dto          SecurityErrorResponse
│   └── jwt
│       ├── config     JwtProperties
│       ├── provider   JwtProvider, TokenType
│       ├── dto        TokenPayload, TokenPairResponse
│       └── exception  ExpiredTokenException, InvalidTokenException
└── config           OpenApiConfig (Swagger의 Bearer 토큰 입력), RedisConfig (RedisTemplate)
```

## 사용자

### 테이블

`user`는 PostgreSQL 예약어라 테이블 이름을 `users`로 했다. 테이블은 JPA `ddl-auto: update`로 만든다.

| 테이블 | 컬럼 | 설명 |
|---|---|---|
| `users` | `email_key` (PK) | 이메일을 소문자로 바꾼 값. 중복 확인과 조회에 쓴다. |
| | `email` | 사용자가 입력한 대소문자 그대로의 이메일. 응답과 화면에 쓴다. |
| | `password` | BCrypt 해시 (`$2a$10$...`). 원문은 저장하지 않는다. |
| | `nickname` | 비어 있으면 이메일을 쓴다. |
| | `created_at`, `updated_at` | Hibernate가 자동으로 기록한다. |
| `user_roles` | `user_email_key`, `role` | 사용자별 권한. 사용자 하나에 하나 이상 |

### 이메일 대소문자 처리

| 용도 | 값 | 예 |
|---|---|---|
| 비교(아이디) | `email_key` = 앞뒤 공백 제거 + 소문자 | `hong.gildong@example.com` |
| 표시 | `email` = 입력한 그대로 | `Hong.GilDong@Example.com` |

- 어떤 대소문자로 조회하거나 로그인해도 같은 사용자를 찾는다.
- 대소문자만 다른 이메일로 다시 가입하면 기본 키가 겹쳐서 거부된다. 별도 인덱스 없이 DB가 보장한다.
- 비교 키를 만드는 규칙은 `User.toEmailKey()` 한 곳에 있다. 엔티티 생성자가 이 규칙으로 키를 만든다.

### 권한

`UserRole`: `ROLE_USER`, `ROLE_MANAGER`, `ROLE_ADMIN`

- DB에는 이름 그대로 문자열로 저장한다.
- `ROLE_` 접두사는 Spring Security 규칙과 같다. 그래서 `hasRole('ADMIN')`으로 바로 검사할 수 있다.
- 비로그인 사용자는 GUEST(`ROLE_GUEST`)로 다룬다. GUEST는 DB에 저장하지 않는 권한이다.

### UserService

| 메서드 | 설명 |
|---|---|
| `create` | 등록. 권한을 생략하면 `ROLE_USER`. 이메일 중복이면 `DuplicateEmailException` |
| `getOne` | 이메일로 조회. 권한까지 한 번의 쿼리로 가져온다. |
| `getAll` | 목록. 최근 가입순, 페이지 1부터. 권한은 `@BatchSize`로 한 번에 읽는다. |
| `update` | 닉네임과 권한을 교체한다. 비밀번호는 값이 있을 때만 바꾼다. |
| `delete` | 사용자와 권한 행을 실제로 삭제한다. |

### 설계상 주의한 점

- **덮어쓰기 방지:** 기본 키를 직접 넣는 엔티티는 Spring Data의 `save()`가 같은 키를 덮어쓴다. `User`가 `Persistable`을 구현해, 작성일이 없는(저장 전) 엔티티만 새 엔티티로 본다.
- **동시 가입:** 중복 확인과 저장 사이에 같은 이메일이 먼저 저장돼도 `DuplicateEmailException`으로 처리한다.
- **목록 조회:** 권한 컬렉션을 fetch join하면서 페이징하면 Hibernate가 전체를 메모리에 올린다. 그래서 목록에서는 fetch join 대신 `@BatchSize`를 쓴다.
- **비밀번호 규칙:** 가입과 수정 요청은 8~64자다.

## 로그인 API

`POST /api/auth/login`은 화이트 리스트에 있어 JWT 없이 호출할 수 있다.

```json
// 요청
{ "email": "user1@example.com", "password": "..." }

// 200 응답
{ "accessToken": "eyJ...", "refreshToken": "eyJ...",
  "accessTokenExpiresIn": 600, "refreshTokenExpiresIn": 3600 }
```

| 상황 | 상태 | message |
|---|---|---|
| 이메일이나 비밀번호 누락 | 400 | 입력값을 확인해 주세요. (`fieldErrors` 포함) |
| JSON 형식 오류 | 400 | 요청 값의 형식이 올바르지 않습니다. |
| 없는 이메일, 틀린 비밀번호 | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. |

- **같은 메시지:** 없는 이메일과 틀린 비밀번호에 같은 메시지를 쓴다. 다르게 알려 주면 가입된 이메일을 알아낼 수 있다.
- **같은 응답 시간:** 없는 이메일이어도 가짜 해시와 비밀번호를 한 번 비교한다. 비교를 건너뛰면 응답이 빨라져서, 응답 시간으로 가입 여부를 알 수 있다.
- **비밀번호 길이:** 로그인할 때는 가입 규칙(8~64자)을 검사하지 않는다.
- **Refresh Token 저장:** 발급한 Refresh Token을 Redis에 저장한다. 같은 사용자가 다시 로그인하면 이전 Refresh Token을 덮어쓴다.

## 토큰 갱신 API

`POST /api/auth/refresh`는 `/api/auth/**` 화이트 리스트에 있어 Access Token 없이 호출할 수 있다.
Access Token이 만료되면(401 `TOKEN_EXPIRED`) Refresh Token을 보내 두 토큰을 모두 새로 받는다(Refresh Token Rotation).

```json
// 요청
{ "refreshToken": "eyJ..." }

// 200 응답 (로그인과 같은 형식)
{ "accessToken": "eyJ...", "refreshToken": "eyJ...",
  "accessTokenExpiresIn": 600, "refreshTokenExpiresIn": 3600 }
```

클라이언트는 응답의 새 Refresh Token으로 바꿔 저장해야 한다. 보낸 Refresh Token은 더 이상 쓸 수 없다.

### 처리 순서 (`AuthService.refresh`)

1. Refresh Token의 서명, 만료, 종류(`type: refresh`)를 검증하고 이메일을 꺼낸다.
2. 사용자를 DB에서 다시 읽는다. 바뀐 닉네임과 권한이 새 토큰에 들어간다.
3. 새 토큰 쌍을 만든다.
4. Redis에 저장된 토큰이 보낸 토큰과 **같을 때만** 새 Refresh Token으로 바꾼다. 비교와 교체는 Redis 명령 하나로 처리한다([Refresh Token 저장](#refresh-token-저장-redis)).
5. 다르면 이미 한 번 쓰인 토큰이다. 탈취됐을 수 있으므로 저장된 토큰을 지운다. 이 사용자는 다시 로그인해야 한다.

### 오류 응답

실패하면 모두 401이다. 클라이언트는 이 API에서 401을 받으면 로그인 화면으로 보낸다.

| 상황 | 상태 | message |
|---|---|---|
| `refreshToken` 누락 | 400 | 입력값을 확인해 주세요. (`fieldErrors` 포함) |
| Refresh Token 만료 | 401 | Refresh Token이 만료되었습니다. 다시 로그인해 주세요. |
| 서명 불일치, 형식 오류, Access Token을 보냄 | 401 | 유효하지 않은 Refresh Token입니다. 다시 로그인해 주세요. |
| Redis에 토큰 없음 (TTL 지남, 불일치로 삭제됨) | 401 | 로그인 정보가 없습니다. 다시 로그인해 주세요. |
| 저장된 토큰과 다름 (이미 쓴 토큰) → **저장된 토큰 삭제** | 401 | 사용할 수 없는 Refresh Token입니다. 다시 로그인해 주세요. |
| 토큰 발급 뒤 탈퇴한 사용자 → 저장된 토큰 삭제 | 401 | 사용자를 찾을 수 없습니다. 다시 로그인해 주세요. |

- **재사용 탐지:** 공격자가 훔친 토큰을 쓰든 정상 사용자가 이전 토큰을 쓰든, 이전 토큰이 한 번이라도 오면 저장된 토큰을 지운다. 그러면 공격자와 정상 사용자 모두 갱신할 수 없어, 정상 사용자가 다시 로그인할 때까지 공격자도 더 이상 토큰을 받지 못한다.
- **오류 형식:** 로그인 API와 같은 `AuthErrorResponse`(`status`, `message`, `fieldErrors`)다. 필터 오류와 달리 `code`는 없다.

## Refresh Token 저장 (Redis)

Redis Cloud(Redis 8.6.2)를 쓴다. 클라이언트는 Lettuce이고, 연결은 `spring.data.redis.*` 설정으로 Spring Boot가 자동 구성한다.

| 항목 | 값 |
|---|---|
| 키 | `refresh-token:{소문자 이메일}` (예: `refresh-token:user1@example.com`) |
| 값 | Refresh Token 문자열 |
| TTL | Refresh Token 유효 시간(60분). 만료되면 Redis가 지운다. |

- **사용자당 하나:** 키가 이메일이라 사용자 한 명에 토큰이 하나다. 다른 기기에서 로그인하면 먼저 로그인한 기기의 Refresh Token은 쓸 수 없게 된다.
- **이메일 대소문자:** 키는 `User.toEmailKey()`로 만든다. `users.email_key`와 같은 규칙이다.
- **`RedisConfig`:** 키와 값을 `StringRedisSerializer`로 저장한다. 기본 JDK 직렬화를 쓰면 redis-cli에서 `\xac\xed...`처럼 보인다. 빈 이름이 `redisTemplate`이라 Spring Boot의 기본 `RedisTemplate` 대신 쓰인다.
- **`@Qualifier`:** Spring Boot가 만드는 `StringRedisTemplate`도 `RedisTemplate<String, String>` 타입이라 후보가 둘이다. `RefreshTokenRepository`는 `@Qualifier("redisTemplate")`로 지정한다.

### RefreshTokenRepository

| 메서드 | Redis 명령 | 쓰는 곳 |
|---|---|---|
| `save(email, token, ttl)` | `SET key token PX ttl` | 로그인 |
| `replaceIfEquals(email, expected, newToken, ttl)` | `SET key newToken IFEQ expected GET PX ttl` | 토큰 갱신 |
| `deleteIfEquals(email, token)` | `DELEX key IFEQ token` | 토큰 갱신, 불일치일 때 |
| `deleteByEmail(email)` | `DEL key` | 토큰 갱신, 탈퇴한 사용자일 때 |

### 비교와 교체를 한 명령으로

`GET`으로 꺼내 비교한 뒤 `SET`으로 저장하면, 같은 토큰으로 요청 두 개가 거의 동시에 올 때 둘 다 비교를 통과한다. 그래서 `SET`의 `IFEQ`(저장된 값이 같을 때만 저장)와 `GET`(실행 전 값을 돌려줌)을 함께 쓴다.

| `GET`이 돌려준 이전 값 | 의미 | 처리 |
|---|---|---|
| 보낸 토큰과 같음 | 교체됨 | 새 토큰 응답 |
| 없음 | 키가 없어 아무것도 저장되지 않음 | 401 |
| 보낸 토큰과 다름 | 교체되지 않음 (이미 쓴 토큰) | `DELEX ... IFEQ 이전 값`으로 삭제, 401 |

- **동시 요청:** 같은 토큰으로 동시에 갱신하면 하나만 200이고 나머지는 401이다. 그 뒤 저장된 토큰은 지워진다.
- **조건부 삭제:** 불일치를 확인한 뒤 지우기 전에 사용자가 다시 로그인했다면, 새로 저장된 토큰은 값이 달라 지워지지 않는다.
- **Redis 버전:** `SET ... IFEQ`와 `DELEX`는 Redis 8.4부터 있다. 낮은 버전으로 바꾸면 갱신할 때 명령 오류가 난다. Spring Data Redis 4.1의 `setGet(key, value, spec -> spec.ifEquals().value(...))`, `compareAndDelete(key, value)`로 호출한다.

## JWT

### 페이로드

```json
{
  "sub": "user1@example.com",
  "nickname": "user1@example.com",
  "roles": ["ROLE_USER"],
  "type": "access",
  "jti": "a3caf9ee-1388-4ad9-810f-c8aa06522611",
  "iat": 1793982491,
  "exp": 1793983091
}
```

- **서명:** HMAC-SHA256(HS256)으로 서명한다. 라이브러리는 jjwt 0.12.6이다.
- **`type`:** `access`나 `refresh`다. Refresh Token을 Access Token 자리에 쓰면 `TOKEN_INVALID`로 거부한다.
- **`jti`:** 토큰 ID다. 발급할 때마다 새 UUID를 넣는다. `iat`와 `exp`는 초 단위라, `jti`가 없으면 같은 초에 같은 사용자에게 발급한 토큰이 서명까지 똑같다. 그러면 Refresh Token을 교체해도 값이 같아서 이전 토큰이 계속 통과했다(실제 호출로 확인한 버그).
- **민감 정보 금지:** 페이로드는 서명만 될 뿐 암호화되지 않아 누구나 읽을 수 있다. 비밀번호 같은 정보는 넣지 않는다.
- **유효 시간:** Access Token 10분, Refresh Token 60분

### JwtProvider

| 메서드 | 설명 |
|---|---|
| `createTokenPair(TokenPayload)` | 같은 시각 기준으로 두 토큰을 발급한다. |
| `createAccessToken(TokenPayload)` | Access Token만 발급한다. |
| `parseAccessToken(token)` / `parseRefreshToken(token)` | 서명, 만료, 종류를 검증하고 `TokenPayload`를 돌려준다. |

- 만료된 토큰은 `ExpiredTokenException`, 그 밖의 오류는 `InvalidTokenException`을 던진다.
- 생성자가 둘이다. 하나는 테스트에서 시계를 바꿔 넣는 용도다. 그래서 Spring이 쓸 생성자에 `@Autowired`를 붙였다. 없으면 앱이 시작되지 않는다.

## 요청 인증: 필터 두 개

두 필터는 `@Component`로 등록하지 않고 `SecurityConfig`에서 직접 만든다. `@Component`로 만들면 Spring Boot가 일반 서블릿 필터로도 등록해서 한 번 더 실행된다.

| 필터 | 실행 범위 | 동작 |
|---|---|---|
| `JWTCheckFilter` | 모든 요청 | JWT가 있으면 그대로 통과. 없으면 GUEST(`ROLE_GUEST`, 익명 인증)로 SecurityContext에 넣고 통과 |
| `AuthCheckFilter` | 화이트 리스트 밖, CORS 사전 요청 제외 | JWT가 있어야 통과. 검증에 성공하면 `TokenPayload`와 권한을 SecurityContext에 넣음 |

- **JWT 위치:** `Authorization: Bearer {토큰}` 헤더에서 읽는다. 헤더가 없거나 다른 형식(`Basic` 등)이면 JWT가 없는 것으로 본다.
- **사용자 정보 받기:** 컨트롤러에서 `@AuthenticationPrincipal TokenPayload user`로 받는다. DB를 다시 조회하지 않는다.

### 인증·권한 오류 응답

필터의 예외는 `@RestControllerAdvice`까지 가지 않으므로 `SecurityErrorWriter`가 JSON을 직접 쓴다. 401에는 `WWW-Authenticate: Bearer` 헤더를 붙인다.

```json
{ "status": 401, "code": "TOKEN_EXPIRED", "message": "Access Token이 만료되었습니다." }
```

| `code` | 상황 | 상태 |
|---|---|---|
| `TOKEN_MISSING` | JWT 없음 | 401 |
| `TOKEN_EXPIRED` | 만료됨. 클라이언트는 이 코드로 재발급 여부를 판단한다. | 401 |
| `TOKEN_INVALID` | 서명 불일치, 형식 오류, Refresh Token 사용 | 401 |
| `UNAUTHORIZED` | 필터를 거치지 않고 인증이 필요한 곳에 도달함 (예: 화이트 리스트 경로에 `@PreAuthorize`) | 401 |
| `FORBIDDEN` | 로그인했지만 권한 부족 | 403 |

## 권한

### URL 규칙 (`SecurityConfig`)

```java
.requestMatchers(whitelist).permitAll()
.anyRequest().authenticated()
```

URL 규칙은 이 두 가지만 둔다. 기능별 권한은 컨트롤러 메서드의 `@PreAuthorize`로 지정한다(`@EnableMethodSecurity`).

### 화이트 리스트 (JWT 없이 호출 가능)

| 항목 | 이유 |
|---|---|
| `/error` | 빠지면 404·500 같은 오류 응답도 401로 바뀐다. |
| `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | API 문서 |
| `/api/auth/**` | 로그인, 토큰 갱신 |
| `POST /api/users` | 회원 가입 (예정, 아직 API 없음) |
| `GET /api/products/**` | 상품 조회와 목록 |
| `GET /api/todos/**` | 할 일 조회와 목록 |

### 기능별 권한

| API | GUEST | 로그인 사용자 | 지정 위치 |
|---|---|---|---|
| 할 일 목록, 조회 | O | O | 화이트 리스트 |
| 할 일 등록, 수정, 삭제 | X (401) | O | `TodoController`의 `@PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")` |
| 상품 목록, 조회 | O | O | 화이트 리스트 |
| 상품 등록, 수정, 삭제 | X (401) | O | 화이트 리스트 밖 (`authenticated`) |
| 로그인, 토큰 갱신 | O | O | 화이트 리스트 |

할 일을 등록하면 로그인 사용자의 이메일이 로그에 남는다(`Todo 등록 요청. email=...`). 작성자를 DB에 저장하지는 않는다.

## CORS와 세션

### CORS

| 항목 | 값 |
|---|---|
| 허용 출처 | `security.cors.allowed-origins` (기본 `http://localhost:3000`, `http://localhost:5173`) |
| 허용 메서드 | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| 허용 요청 헤더 | `Authorization`, `Content-Type` |
| 노출 응답 헤더 | `WWW-Authenticate` |
| 쿠키 전송 | 허용 안 함 (토큰을 헤더로 보냄) |
| 사전 요청 캐시 | `security.cors.max-age` (기본 1시간) |

- **처리 위치:** CORS는 보안 필터 체인 맨 앞에서 처리한다. 사전 요청(OPTIONS)은 JWT 필터까지 가기 전에 응답한다.
- **오류 응답에도 적용:** 401·403 응답에도 CORS 헤더가 붙어서, 프런트엔드가 오류 내용을 읽을 수 있다.

### 세션

HttpSession을 쓰지 않는다. 실행해 보면 응답에 `Set-Cookie`가 없다.

| 설정 | 역할 |
|---|---|
| `SessionCreationPolicy.STATELESS` | Spring Security가 세션을 만들지도, 쓰지도 않는다. |
| `RequestAttributeSecurityContextRepository` | 인증 정보를 요청 하나 동안만 두고 세션에 저장하지 않는다. |
| `NullRequestCache` | 요청을 세션에 저장하지 않는다. |
| CSRF, 폼 로그인, HTTP Basic, 로그아웃 끔 | 세션·쿠키 기반 기능이라 필요 없다. |

## 설정

### application.yaml (git에 올라감)

```yaml
jwt:
  access-token-validity: 10m
  refresh-token-validity: 60m
```

### application-local.yaml (git에 올리지 않음)

```yaml
spring:
  data:
    redis:                        # Refresh Token 저장소
      host: ....db.redis.io
      port: 17870
      username: default
      password: ...
      timeout: 3s

jwt:
  secret: "..."                   # 32바이트 이상. 숫자만으로 된 값은 따옴표로 감싼다.

security:
  whitelist:                      # '/경로' 또는 'GET /경로'
    - /error
    - /swagger-ui.html
    - /swagger-ui/**
    - /v3/api-docs/**
    - /api/auth/**
    - POST /api/users
    - GET /api/products/**
    - GET /api/todos/**
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:5173
    max-age: 1h
```

앱을 시작할 때 다음 설정을 검사하고, 맞지 않으면 시작하지 않는다.

| 설정 | 조건 |
|---|---|
| `jwt.secret` | 32바이트 이상 |
| `jwt.refresh-token-validity` | Access Token 유효 시간보다 길어야 함 |
| `security.whitelist` | 각 항목이 `/경로` 또는 `메서드 /경로` 형식 |
| `security.cors.allowed-origins` | 값이 있어야 함 |

### 의존성 (build.gradle)

| 의존성 | 용도 |
|---|---|
| `spring-boot-starter-security` | 필터, SecurityContext, BCrypt |
| `jjwt-api` (구현), `jjwt-impl`·`jjwt-jackson` (실행 시) | JWT. 모두 0.12.6 |
| `spring-boot-starter-data-redis` | Refresh Token 저장. Lettuce 7.5, Spring Data Redis 4.1 |

### Swagger

Swagger UI 오른쪽 위 Authorize 버튼에 Access Token을 넣으면 모든 요청에 `Authorization: Bearer` 헤더가 붙는다.

## 테스트 계정

회원 가입 API가 없어서 Supabase DB에 직접 넣었다. 비밀번호는 저장소에 적지 않으며 따로 공유한다.

| 이메일 | 권한 |
|---|---|
| `admin@example.com` | `ROLE_ADMIN` |
| `manager@example.com` | `ROLE_MANAGER` |
| `user1@example.com` | `ROLE_USER` |

- **비밀번호 규칙과 다름:** 세 계정의 비밀번호는 가입 규칙(8~64자)보다 짧다. 로그인은 문제없지만 가입이나 수정 API로는 같은 비밀번호를 만들 수 없다.
- **권한은 하나씩:** ADMIN 계정은 `ROLE_ADMIN` 하나만 가진다.
- **다른 DB에서는 다시 만들어야 함:** 계정을 만드는 코드가 저장소에 없다.

### 실제 호출로 확인한 것

| 확인 항목 | 결과 |
|---|---|
| 세 계정 로그인 | 200, 페이로드와 유효 시간 정상 |
| 대소문자를 바꾼 이메일로 로그인 | 200 |
| 틀린 비밀번호, 없는 이메일 | 401, 같은 메시지 |
| GUEST로 할 일 목록 조회 | 200 |
| GUEST로 할 일 등록 | 401 `TOKEN_MISSING` |
| Refresh Token으로 등록 | 401 `TOKEN_INVALID` |
| 형식이 틀린 토큰 | 401 `TOKEN_INVALID` |
| USER 토큰으로 할 일 등록 | 201 |
| CORS 사전 요청 (`localhost:5173`) | 200, `Access-Control-Allow-Origin` 있음 |
| 응답의 `Set-Cookie` | 없음 |

### 토큰 갱신을 실제 호출로 확인한 것

`user1@example.com`으로 호출하고, Redis 값은 직접 조회해 확인했다.

| 확인 항목 | 결과 |
|---|---|
| 로그인 | Redis에 Refresh Token 저장, TTL 3600초 |
| 받은 Refresh Token으로 갱신 (두 번 연속) | 200, 매번 다른 Refresh Token으로 교체, TTL 3600초로 다시 걸림 |
| 이미 쓴 Refresh Token으로 갱신 | 401 사용할 수 없는 Refresh Token, Redis 키 삭제 |
| 그 뒤 가장 최근 Refresh Token으로 갱신 | 401 로그인 정보가 없습니다 |
| 형식이 틀린 토큰, Access Token | 401 유효하지 않은 Refresh Token |
| 빈 값 | 400 |
| 만료된 Refresh Token (`exp`를 과거로 해 직접 서명) | 401 Refresh Token이 만료되었습니다 |
| 같은 Refresh Token으로 동시에 두 번 | 하나 200, 하나 401, 그 뒤 키 삭제 |
| 로그인한 같은 초에 갱신하고 이전 토큰 재사용 | 401 (`jti` 넣기 전에는 200이었음) |

## 테스트

- **보안 필터를 끈 컨트롤러 테스트:** 보안 스타터를 추가하면 `@WebMvcTest`에 Spring Boot 기본 보안(모든 요청 로그인 필요)이 적용된다. 그래서 `TodoControllerTest`와 `ProductControllerTest`에 `@AutoConfigureMockMvc(addFilters = false)`를 붙였다.
- **`@PreAuthorize` 미검증:** `@WebMvcTest`는 `SecurityConfig`를 읽지 않아서, 위 컨트롤러 테스트에는 `@PreAuthorize`도 적용되지 않는다. 권한 규칙은 이 테스트들로 검증되지 않는다.
- **테스트가 없는 부분:** 사용자, 로그인, 토큰 갱신, Redis 저장, JWT, 필터, CORS에 대한 자동 테스트는 아직 없다. 위 표는 앱을 실행해서 직접 호출한 결과다.

## 남은 작업과 제한

- **회원 가입·사용자 API 없음:** 사용자 컨트롤러가 없다. `POST /api/users`는 화이트 리스트에만 있다.
- **로그아웃 API 없음:** Redis의 Refresh Token을 지우는 API가 없다. 지금은 TTL이 지나야 사라진다.
- **Access Token은 무효로 만들 수 없음:** Refresh Token을 지워도 이미 발급한 Access Token은 만료(10분)될 때까지 쓸 수 있다.
- **기기 하나만:** Refresh Token을 사용자당 하나만 저장해, 여러 기기에서 동시에 로그인 상태를 유지할 수 없다.
- **권한 변경 반영 시점:** 토큰 갱신 때 DB에서 권한을 다시 읽는다. 그 전까지는 Access Token에 예전 권한이 남는다(최대 10분).
- **Redis 8.4 이상 필요:** `SET ... IFEQ`, `DELEX`를 쓴다.
- **할 일 작성자:** 할 일에 작성자를 저장하지 않는다. 누구나 로그인만 하면 다른 사람의 할 일을 수정하고 삭제할 수 있다.
- **기본 비밀번호 로그:** 앱을 시작하면 Spring Boot가 "Using generated security password" 로그를 남긴다. 이 프로젝트는 그 기본 사용자를 쓰지 않으므로 무시해도 된다.
