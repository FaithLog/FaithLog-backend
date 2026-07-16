local keys = redis.call('KEYS', '*')
table.sort(keys)
local fingerprintParts = {}
for _, key in ipairs(keys) do
	local serialized = redis.call('DUMP', key)
	table.insert(fingerprintParts, redis.sha1hex(key) .. ':' .. redis.sha1hex(serialized))
end
return cjson.encode({
	keyCount=tostring(#keys),
	stateSha1=redis.sha1hex(table.concat(fingerprintParts, '|'))
})
