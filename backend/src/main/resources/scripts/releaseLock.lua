local currentUser = redis.call('GET', KEYS[1])

if currentUser == ARGV[1] then
    return redis.call('DEL', KEYS[1])
else 
    return 0 
end