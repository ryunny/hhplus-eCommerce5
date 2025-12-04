-- move_to_processing.lua
-- 대기열에서 사용자를 제거하고 처리 중 상태로 원자적으로 이동
--
-- KEYS[1]: queue_key (대기열 Sorted Set)
-- KEYS[2]: processing_key (처리 중 Set)
-- ARGV[1]: batch_size (처리할 개수)
--
-- Returns: 제거된 사용자 목록 (member, score 쌍의 배열)

local queue_key = KEYS[1]
local processing_key = KEYS[2]
local batch_size = tonumber(ARGV[1])

-- 1. 대기열에서 상위 N명을 제거 (ZPOPMIN: 가장 낮은 score부터)
local members = redis.call('ZPOPMIN', queue_key, batch_size)

-- 2. 처리 중 상태로 추가
for i = 1, #members, 2 do
    local member = members[i]
    redis.call('SADD', processing_key, member)
end

-- 3. 제거된 사용자 목록 반환
return members