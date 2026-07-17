local metaKey = '__faithlog_198_snapshot_meta'
local valuesKey = '__faithlog_198_snapshot_values'
local ttlsKey = '__faithlog_198_snapshot_ttls'
local sentinelField = '__faithlog_198_snapshot_empty_sentinel'
assert(redis.call('EXISTS', metaKey, valuesKey, ttlsKey) == 0,
	'snapshot staging keys already exist in the workload database')
local keys = redis.call('KEYS', '*')
table.sort(keys)
local records = {}
local fingerprintParts = {}
local ttlIntentParts = {}
for _, key in ipairs(keys) do
	assert(key ~= sentinelField, 'workload key collides with snapshot sentinel')
	local serialized = redis.call('DUMP', key)
	local ttl = redis.call('PTTL', key)
	assert(serialized and (ttl == -1 or ttl > 0), 'captured key has invalid TTL intent')
	table.insert(records, {key, serialized, ttl})
	table.insert(fingerprintParts, redis.sha1hex(key) .. ':' .. redis.sha1hex(serialized))
	table.insert(ttlIntentParts, redis.sha1hex(key) .. ':' .. redis.sha1hex(serialized) .. ':' .. tostring(ttl))
end
local stateSha1 = redis.sha1hex(table.concat(fingerprintParts, '|'))
local ttlIntentSha1 = redis.sha1hex(table.concat(ttlIntentParts, '|'))

redis.call('HSET', metaKey,
	'keyCount', tostring(#records),
	'stateSha1', stateSha1,
	'ttlIntentSha1', ttlIntentSha1)
redis.call('HSET', valuesKey, sentinelField, '1')
redis.call('HSET', ttlsKey, sentinelField, '1')
for _, record in ipairs(records) do
	redis.call('HSET', valuesKey, record[1], record[2])
	redis.call('HSET', ttlsKey, record[1], tostring(record[3]))
end
return cjson.encode({keyCount=tostring(#records), stateSha1=stateSha1, ttlIntentSha1=ttlIntentSha1})
