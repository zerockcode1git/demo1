# Todo API curl 호출 문서

Todo를 등록, 조회, 수정, 삭제하는 REST API의 curl 호출 방법을 정리한 문서입니다.

## API 목록

| 기능 | 메서드 | URL | 성공 상태 코드 |
|---|---|---|---|
| [등록](#등록) | POST | `/api/todos` | 201 |
| [단건 조회](#단건-조회) | GET | `/api/todos/{id}` | 200 |
| [목록 조회](#목록-조회) | GET | `/api/todos` | 200 |
| [수정](#수정) | PUT | `/api/todos/{id}` | 200 |
| [삭제](#삭제) | DELETE | `/api/todos/{id}` | 204 |

## 시작하기

DB 설정이 `local` 프로필에 있으므로 프로필을 지정해 서버를 실행합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

기본 주소는 `http://localhost:8080`입니다. 이 문서의 예시는 아래 변수를 사용하므로 먼저 설정해 주세요.

```bash
API=http://localhost:8080/api/todos
```

아래 예시를 실행하면 `local` 프로필에 설정된 실제 DB의 데이터가 바뀝니다. 응답의 `id`와 시각은 실행할 때마다 달라지며, 응답 예시는 읽기 쉽게 줄을 나눈 것입니다. 상태 코드까지 확인하려면 curl에 `-i` 옵션을 추가하세요.

## 등록

새 Todo를 등록합니다. `completed`는 `false`로 시작합니다.

| 본문 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `content` | 문자열 | 예 | 빈 값이나 공백만 입력할 수 없음 |

```bash
curl -X POST "$API" \
  -H 'Content-Type: application/json' \
  -d '{"content":"장보기"}'
```

응답: `201 Created`

```json
{
  "id": 111,
  "content": "장보기",
  "completed": false,
  "createdAt": "2026-11-05T18:31:28.711175",
  "updatedAt": "2026-11-05T18:31:28.711207"
}
```

## 단건 조회

id로 Todo 하나를 조회합니다.

```bash
curl "$API/111"
```

응답: `200 OK`

```json
{
  "id": 111,
  "content": "장보기",
  "completed": false,
  "createdAt": "2026-11-05T18:31:28.711175",
  "updatedAt": "2026-11-05T18:31:28.711207"
}
```

## 목록 조회

Todo 목록을 최신순(id 역순)으로 조회합니다. 정렬 순서는 바꿀 수 없습니다.

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|
| `page` | 정수 | 아니요 | 0 | 0 이상. 첫 페이지가 0 |
| `size` | 정수 | 아니요 | 10 | 1 이상 100 이하 |

```bash
# 기본값: 첫 페이지, 10개씩
curl "$API"

# 두 번째 페이지, 5개씩
curl "$API?page=1&size=5"
```

응답: `200 OK`

```json
{
  "content": [
    {
      "id": 111,
      "content": "장보기",
      "completed": false,
      "createdAt": "2026-11-05T18:31:28.711175",
      "updatedAt": "2026-11-05T18:31:28.711207"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

| 응답 필드 | 설명 |
|---|---|
| `content` | 현재 페이지의 Todo 목록 |
| `page` | 현재 페이지 번호 (0부터 시작) |
| `size` | 페이지 크기 |
| `totalElements` | 전체 Todo 수 |
| `totalPages` | 전체 페이지 수 |
| `hasNext` | 다음 페이지가 있으면 `true` |

## 수정

Todo의 내용과 완료 여부를 수정합니다. 두 필드 모두 보내야 합니다.

| 본문 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `content` | 문자열 | 예 | 빈 값이나 공백만 입력할 수 없음 |
| `completed` | 불리언 | 예 | `true` 또는 `false` |

```bash
curl -X PUT "$API/111" \
  -H 'Content-Type: application/json' \
  -d '{"content":"장보기 수정","completed":true}'
```

응답: `200 OK`

```json
{
  "id": 111,
  "content": "장보기 수정",
  "completed": true,
  "createdAt": "2026-11-05T18:31:28.711175",
  "updatedAt": "2026-11-05T18:31:28.910066"
}
```

## 삭제

id로 Todo를 삭제합니다.

```bash
curl -i -X DELETE "$API/111"
```

응답: `204 No Content` (본문 없음)

## 오류 응답

오류가 나면 아래 형식의 JSON을 반환합니다.

| 응답 필드 | 설명 |
|---|---|
| `status` | HTTP 상태 코드 |
| `message` | 오류 설명 |
| `fieldErrors` | 필드별 오류 메시지. 없으면 빈 객체 |

| 상황 | 상태 코드 | `fieldErrors` |
|---|---|---|
| 입력값이 규칙에 맞지 않음 | 400 | 있음 |
| `page`, `size`가 숫자가 아님 | 400 | 있음 |
| JSON 형식이 깨짐 | 400 | 없음 |
| 경로의 `id`가 숫자가 아님 | 400 | 없음 |
| 해당 id의 Todo가 없음 | 404 | 없음 |

### 입력값이 규칙에 맞지 않을 때

```bash
curl -X POST "$API" \
  -H 'Content-Type: application/json' \
  -d '{"content":"   "}'
```

응답: `400 Bad Request`

```json
{
  "status": 400,
  "message": "입력값을 확인해 주세요.",
  "fieldErrors": {
    "content": "내용을 입력해 주세요."
  }
}
```

쿼리 파라미터도 같은 형식으로 응답합니다.

```bash
curl "$API?size=101"
```

```json
{
  "status": 400,
  "message": "입력값을 확인해 주세요.",
  "fieldErrors": {
    "size": "페이지 크기는 100 이하여야 합니다."
  }
}
```

### 요청 형식이 잘못되었을 때

```bash
curl "$API/abc"
```

응답: `400 Bad Request`

```json
{
  "status": 400,
  "message": "요청 값의 형식이 올바르지 않습니다.",
  "fieldErrors": {}
}
```

### Todo가 없을 때

단건 조회, 수정, 삭제에서 없는 id를 요청하면 발생합니다.

```bash
curl "$API/999999"
```

응답: `404 Not Found`

```json
{
  "status": 404,
  "message": "Todo를 찾을 수 없습니다. id=999999",
  "fieldErrors": {}
}
```

## 전체 테스트 스크립트

[`scripts/test-todo-api.sh`](../scripts/test-todo-api.sh)는 위 API의 정상 요청과 오류 요청을 차례로 호출하고 상태 코드를 확인합니다. 테스트용 Todo를 만들고 끝날 때 삭제합니다.

```bash
./scripts/test-todo-api.sh

# 서버 주소가 다를 때
BASE_URL=http://localhost:9090 ./scripts/test-todo-api.sh
```

## Swagger UI

서버를 실행한 뒤 브라우저에서 API를 확인하고 호출할 수 있습니다.

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI 문서(JSON): <http://localhost:8080/v3/api-docs>
