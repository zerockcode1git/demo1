# Todo API curl 실행 결과

Todo API의 각 작업을 curl로 호출하고 응답을 그대로 기록한 문서입니다. 호출 방법과 파라미터 규칙은 [todo-docs.md](todo-docs.md)를 참고하세요.

- **실행 일시:** 2026-11-05 18:41
- **실행 환경:** `local` 프로필, `http://localhost:8080`
- **결과:** 17개 중 17개가 기대한 상태 코드를 반환

응답의 `id`와 시각은 실행할 때마다 달라집니다. 테스트용 Todo는 등록 후 마지막에 삭제했습니다.

## 결과 요약

| 번호 | 구분 | 작업 | 기대 | 실제 | 결과 |
|---|---|---|---|---|---|
| 1 | 정상 요청 | 등록 | 201 | 201 | 성공 |
| 2 | 정상 요청 | 단건 조회 | 200 | 200 | 성공 |
| 3 | 정상 요청 | 목록 조회 (기본값) | 200 | 200 | 성공 |
| 4 | 정상 요청 | 목록 조회 (page, size 지정) | 200 | 200 | 성공 |
| 5 | 정상 요청 | 수정 | 200 | 200 | 성공 |
| 6 | 입력값 오류 | 등록: 공백 내용 | 400 | 400 | 성공 |
| 7 | 입력값 오류 | 수정: completed 누락 | 400 | 400 | 성공 |
| 8 | 입력값 오류 | 목록 조회: 음수 page | 400 | 400 | 성공 |
| 9 | 입력값 오류 | 목록 조회: size 0 | 400 | 400 | 성공 |
| 10 | 입력값 오류 | 목록 조회: size 상한 초과 | 400 | 400 | 성공 |
| 11 | 형식 오류 | 등록: 깨진 JSON | 400 | 400 | 성공 |
| 12 | 형식 오류 | 목록 조회: 숫자가 아닌 page | 400 | 400 | 성공 |
| 13 | 형식 오류 | 단건 조회: 숫자가 아닌 id | 400 | 400 | 성공 |
| 14 | 삭제 | 삭제 | 204 | 204 | 성공 |
| 15 | 삭제 | 삭제 후 단건 조회 | 404 | 404 | 성공 |
| 16 | 삭제 | 삭제 후 수정 | 404 | 404 | 성공 |
| 17 | 삭제 | 삭제 후 다시 삭제 | 404 | 404 | 성공 |

## 정상 요청

### 1. 등록

```bash
curl -si -X POST http://localhost:8080/api/todos -H 'Content-Type: application/json' -d '{"content":"장보기"}'
```

```http
HTTP/1.1 201 
Content-Type: application/json
Content-Length: 132
Date: Thu, 05 Nov 2026 09:41:36 GMT

{"id":140,"content":"장보기","completed":false,"createdAt":"2026-11-05T18:41:36.750968","updatedAt":"2026-11-05T18:41:36.750996"}
```

### 2. 단건 조회

```bash
curl -si http://localhost:8080/api/todos/140
```

```http
HTTP/1.1 200 
Content-Type: application/json
Content-Length: 132
Date: Thu, 05 Nov 2026 09:41:36 GMT

{"id":140,"content":"장보기","completed":false,"createdAt":"2026-11-05T18:41:36.750968","updatedAt":"2026-11-05T18:41:36.750996"}
```

### 3. 목록 조회 (기본값)

```bash
curl -si http://localhost:8080/api/todos
```

```http
HTTP/1.1 200 
Content-Type: application/json
Content-Length: 214
Date: Thu, 05 Nov 2026 09:41:36 GMT

{"content":[{"id":140,"content":"장보기","completed":false,"createdAt":"2026-11-05T18:41:36.750968","updatedAt":"2026-11-05T18:41:36.750996"}],"page":0,"size":10,"totalElements":1,"totalPages":1,"hasNext":false}
```

### 4. 목록 조회 (page, size 지정)

```bash
curl -si 'http://localhost:8080/api/todos?page=0&size=5'
```

```http
HTTP/1.1 200 
Content-Type: application/json
Content-Length: 213
Date: Thu, 05 Nov 2026 09:41:36 GMT

{"content":[{"id":140,"content":"장보기","completed":false,"createdAt":"2026-11-05T18:41:36.750968","updatedAt":"2026-11-05T18:41:36.750996"}],"page":0,"size":5,"totalElements":1,"totalPages":1,"hasNext":false}
```

### 5. 수정

```bash
curl -si -X PUT http://localhost:8080/api/todos/140 -H 'Content-Type: application/json' -d '{"content":"장보기 수정","completed":true}'
```

```http
HTTP/1.1 200 
Content-Type: application/json
Content-Length: 138
Date: Thu, 05 Nov 2026 09:41:36 GMT

{"id":140,"content":"장보기 수정","completed":true,"createdAt":"2026-11-05T18:41:36.750968","updatedAt":"2026-11-05T18:41:36.975839"}
```

## 입력값 오류

### 6. 등록: 공백 내용

```bash
curl -si -X POST http://localhost:8080/api/todos -H 'Content-Type: application/json' -d '{"content":"   "}'
```

```http
HTTP/1.1 400 
Content-Type: application/json
Content-Length: 119
Date: Thu, 05 Nov 2026 09:41:37 GMT
Connection: close

{"status":400,"message":"입력값을 확인해 주세요.","fieldErrors":{"content":"내용을 입력해 주세요."}}
```

### 7. 수정: completed 누락

```bash
curl -si -X PUT http://localhost:8080/api/todos/140 -H 'Content-Type: application/json' -d '{"content":"장보기"}'
```

```http
HTTP/1.1 400 
Content-Type: application/json
Content-Length: 128
Date: Thu, 05 Nov 2026 09:41:37 GMT
Connection: close

{"status":400,"message":"입력값을 확인해 주세요.","fieldErrors":{"completed":"완료 여부를 입력해 주세요."}}
```

### 8. 목록 조회: 음수 page

```bash
curl -si 'http://localhost:8080/api/todos?page=-1'
```

```http
HTTP/1.1 400 
Content-Type: application/json
Content-Length: 134
Date: Thu, 05 Nov 2026 09:41:37 GMT
Connection: close

{"status":400,"message":"입력값을 확인해 주세요.","fieldErrors":{"page":"페이지 번호는 0 이상이어야 합니다."}}
```

### 9. 목록 조회: size 0

```bash
curl -si 'http://localhost:8080/api/todos?size=0'
```

```http
HTTP/1.1 400 
Content-Type: application/json
Content-Length: 134
Date: Thu, 05 Nov 2026 09:41:37 GMT
Connection: close

{"status":400,"message":"입력값을 확인해 주세요.","fieldErrors":{"size":"페이지 크기는 1 이상이어야 합니다."}}
```

### 10. 목록 조회: size 상한 초과

```bash
curl -si 'http://localhost:8080/api/todos?size=101'
```

```http
HTTP/1.1 400 
Content-Type: application/json
Content-Length: 133
Date: Thu, 05 Nov 2026 09:41:37 GMT
Connection: close

{"status":400,"message":"입력값을 확인해 주세요.","fieldErrors":{"size":"페이지 크기는 100 이하여야 합니다."}}
```

## 형식 오류

### 11. 등록: 깨진 JSON

```bash
curl -si -X POST http://localhost:8080/api/todos -H 'Content-Type: application/json' -d '{invalid'
```

```http
HTTP/1.1 400 
Content-Type: application/json
Content-Length: 94
Date: Thu, 05 Nov 2026 09:41:37 GMT
Connection: close

{"status":400,"message":"요청 값의 형식이 올바르지 않습니다.","fieldErrors":{}}
```

### 12. 목록 조회: 숫자가 아닌 page

```bash
curl -si 'http://localhost:8080/api/todos?page=abc'
```

```http
HTTP/1.1 400 
Content-Type: application/json
Content-Length: 136
Date: Thu, 05 Nov 2026 09:41:37 GMT
Connection: close

{"status":400,"message":"입력값을 확인해 주세요.","fieldErrors":{"page":"요청 값의 형식이 올바르지 않습니다."}}
```

### 13. 단건 조회: 숫자가 아닌 id

```bash
curl -si http://localhost:8080/api/todos/abc
```

```http
HTTP/1.1 400 
Content-Type: application/json
Content-Length: 94
Date: Thu, 05 Nov 2026 09:41:37 GMT
Connection: close

{"status":400,"message":"요청 값의 형식이 올바르지 않습니다.","fieldErrors":{}}
```

## 삭제

### 14. 삭제

```bash
curl -si -X DELETE http://localhost:8080/api/todos/140
```

```http
HTTP/1.1 204 
Date: Thu, 05 Nov 2026 09:41:37 GMT
```

### 15. 삭제 후 단건 조회

```bash
curl -si http://localhost:8080/api/todos/140
```

```http
HTTP/1.1 404 
Content-Type: application/json
Content-Length: 83
Date: Thu, 05 Nov 2026 09:41:37 GMT

{"status":404,"message":"Todo를 찾을 수 없습니다. id=140","fieldErrors":{}}
```

### 16. 삭제 후 수정

```bash
curl -si -X PUT http://localhost:8080/api/todos/140 -H 'Content-Type: application/json' -d '{"content":"장보기","completed":true}'
```

```http
HTTP/1.1 404 
Content-Type: application/json
Content-Length: 83
Date: Thu, 05 Nov 2026 09:41:37 GMT

{"status":404,"message":"Todo를 찾을 수 없습니다. id=140","fieldErrors":{}}
```

### 17. 삭제 후 다시 삭제

```bash
curl -si -X DELETE http://localhost:8080/api/todos/140
```

```http
HTTP/1.1 404 
Content-Type: application/json
Content-Length: 83
Date: Thu, 05 Nov 2026 09:41:37 GMT

{"status":404,"message":"Todo를 찾을 수 없습니다. id=140","fieldErrors":{}}
```
