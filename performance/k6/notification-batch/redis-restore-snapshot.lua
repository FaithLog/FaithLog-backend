local metaKey = '__faithlog_198_snapshot_meta'
local valuesKey = '__faithlog_198_snapshot_values'
local ttlsKey = '__faithlog_198_snapshot_ttls'
local sentinelField = '__faithlog_198_snapshot_empty_sentinel'
local expectedCount = redis.call('HGET', metaKey, 'keyCount')
local expectedState = redis.call('HGET', metaKey, 'stateSha1')
local expectedTtlIntent = redis.call('HGET', metaKey, 'ttlIntentSha1')
assert(expectedCount and expectedState and expectedTtlIntent, 'snapshot metadata is missing')
assert(redis.call('HGET', valuesKey, sentinelField) == '1'
	and redis.call('HGET', ttlsKey, sentinelField) == '1', 'snapshot sentinel metadata is missing')
local rawKeys = redis.call('HKEYS', valuesKey)
local keys = {}
for _, key in ipairs(rawKeys) do
	if key ~= sentinelField then table.insert(keys, key) end
end
table.sort(keys)
assert(#keys == tonumber(expectedCount), 'snapshot key cardinality drifted')
assert(redis.call('HLEN', valuesKey) == #keys + 1, 'snapshot value metadata cardinality drifted')
assert(redis.call('HLEN', ttlsKey) == #keys + 1, 'snapshot TTL metadata cardinality drifted')
local fingerprintParts = {}
local ttlIntentParts = {}
local records = {}
for _, key in ipairs(keys) do
	local serialized = redis.call('HGET', valuesKey, key)
	local ttlText = redis.call('HGET', ttlsKey, key)
	local ttl = tonumber(ttlText)
	assert(serialized and ttlText and ttl and (ttl == -1 or ttl > 0),
		'snapshot value or TTL metadata is missing or invalid')
	table.insert(records, {key, serialized, ttl})
	table.insert(fingerprintParts, redis.sha1hex(key) .. ':' .. redis.sha1hex(serialized))
	table.insert(ttlIntentParts, redis.sha1hex(key) .. ':' .. redis.sha1hex(serialized) .. ':' .. tostring(ttl))
end
assert(redis.sha1hex(table.concat(fingerprintParts, '|')) == expectedState, 'snapshot payload hash drifted')
assert(redis.sha1hex(table.concat(ttlIntentParts, '|')) == expectedTtlIntent,
	'snapshot TTL intent hash drifted')

local liveKeys = redis.call('KEYS', '*')
for _, key in ipairs(liveKeys) do
	if key ~= metaKey and key ~= valuesKey and key ~= ttlsKey then
		redis.call('DEL', key)
	end
end
for _, record in ipairs(records) do
	local ttl = record[3]
	if ttl == -1 then ttl = 0 end
	redis.call('RESTORE', record[1], ttl, record[2], 'REPLACE')
end
redis.call('DEL', metaKey, valuesKey, ttlsKey)
return cjson.encode({keyCount=expectedCount, stateSha1=expectedState, ttlIntentSha1=expectedTtlIntent})
