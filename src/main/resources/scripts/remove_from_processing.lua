-- remove_from_processing.lua
-- 처리 완료 후 처리 중 상태에서 제거
--
-- KEYS[1]: processing_key (처리 중 Set)
-- ARGV[1]: member (제거할 사용자)
--
-- Returns: 제거된 개수 (1 또는 0)

local processing_key = KEYS[1]
local member = ARGV[1]

-- 처리 중 Set에서 제거
return redis.call('SREM', processing_key, member)