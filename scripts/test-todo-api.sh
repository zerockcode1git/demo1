#!/usr/bin/env bash
#
# Todo REST API를 curl로 테스트한다.
#
# 사용법:
#   ./gradlew bootRun --args='--spring.profiles.active=local'   # 다른 터미널에서 서버 실행
#   ./scripts/test-todo-api.sh
#   BASE_URL=http://localhost:9090 ./scripts/test-todo-api.sh   # 주소를 바꿀 때
#
# 서버가 연결된 DB에 Todo를 1개 만들고, 끝나면 삭제한다.
# 모든 요청이 기대한 상태 코드를 받으면 종료 코드 0, 아니면 1로 끝난다.

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/todos"

passed=0
failed=0
todo_id=""
body=""

# request <설명> <기대 상태 코드> <curl 인자...>
request() {
    local description="$1" expected="$2"
    shift 2

    local output
    if ! output=$(curl -sS -w '\n%{http_code}' "$@"); then
        echo "서버에 연결할 수 없습니다: $BASE_URL" >&2
        exit 1
    fi

    local status="${output##*$'\n'}"
    body="${output%$'\n'*}"

    if [ "$status" = "$expected" ]; then
        passed=$((passed + 1))
        echo "[PASS] $description ($status)"
    else
        failed=$((failed + 1))
        echo "[FAIL] $description (기대 $expected, 실제 $status)"
    fi
    if [ -n "$body" ]; then
        echo "       $body"
    fi
}

post_json() { request "$1" "$2" -X POST "$API" -H 'Content-Type: application/json' -d "$3"; }
put_json() { request "$1" "$2" -X PUT "$API/$3" -H 'Content-Type: application/json' -d "$4"; }

# 중간에 실패하거나 중단되어도 만든 데이터를 남기지 않는다.
cleanup() {
    if [ -n "$todo_id" ]; then
        curl -sS -o /dev/null -X DELETE "$API/$todo_id"
    fi
}
trap cleanup EXIT

echo "== 정상 요청 =="
post_json "등록" 201 '{"content":"curl 테스트"}'
todo_id=$(printf '%s' "$body" | sed -nE 's/.*"id":([0-9]+).*/\1/p')
if [ -z "$todo_id" ]; then
    echo "등록 응답에서 id를 찾지 못해 중단합니다." >&2
    exit 1
fi

request "단건 조회" 200 "$API/$todo_id"
request "목록 조회 (기본값)" 200 "$API"
request "목록 조회 (page=0, size=5)" 200 "$API?page=0&size=5"
put_json "수정" 200 "$todo_id" '{"content":"curl 테스트 수정","completed":true}'

echo
echo "== 잘못된 요청 =="
post_json "등록: 공백 내용" 400 '{"content":"   "}'
post_json "등록: 깨진 JSON" 400 '{invalid'
put_json "수정: completed 누락" 400 "$todo_id" '{"content":"curl 테스트"}'
request "목록: 음수 page" 400 "$API?page=-1"
request "목록: size 상한 초과" 400 "$API?size=101"
request "목록: 숫자가 아닌 page" 400 "$API?page=abc"
request "단건 조회: 숫자가 아닌 id" 400 "$API/abc"

echo
echo "== 삭제 =="
request "삭제" 204 -X DELETE "$API/$todo_id"
deleted_id="$todo_id"
todo_id=""
request "삭제 후 조회" 404 "$API/$deleted_id"
put_json "삭제 후 수정" 404 "$deleted_id" '{"content":"curl 테스트","completed":true}'
request "삭제 후 다시 삭제" 404 -X DELETE "$API/$deleted_id"

echo
echo "== Swagger =="
request "OpenAPI 문서" 200 -o /dev/null "$BASE_URL/v3/api-docs"
request "Swagger UI" 200 -o /dev/null "$BASE_URL/swagger-ui/index.html"

echo
echo "결과: 성공 $passed, 실패 $failed"
[ "$failed" -eq 0 ]
