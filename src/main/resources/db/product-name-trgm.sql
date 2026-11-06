-- 상품명 부분 검색(lower(name) LIKE '%검색어%')용 trigram 인덱스.
-- JPA ddl-auto로는 만들 수 없어 직접 실행한다. 여러 번 실행해도 안전하다.
-- Supabase는 확장을 extensions 스키마에 설치하는 것을 권장하므로 연산자 클래스도 스키마를 붙여 쓴다.

create extension if not exists pg_trgm with schema extensions;

-- ProductRepository.findAllWithMainImage의 조건(lower(p.name) like ...)과 같은 식으로 만들어야 인덱스를 탄다.
create index if not exists idx_product_name_trgm
    on public.product using gin (lower(name) extensions.gin_trgm_ops);
