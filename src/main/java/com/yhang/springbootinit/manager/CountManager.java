package com.yhang.springbootinit.manager;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.IntegerCodec;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

//实现频率统计、限流
@Component
@Slf4j
public class CountManager {
    @Resource
    public RedissonClient redissonClient;
    public long incrAndGetCount(String key, int timeInterval, TimeUnit timeUnit)
    {
        long expirationTimeInSecond;
        switch (timeUnit){
            case SECONDS:
                expirationTimeInSecond= timeInterval;
                break;
            case MINUTES:
                expirationTimeInSecond= timeInterval*60;
                break;
            case HOURS:
                expirationTimeInSecond= timeInterval*3600;
                break;
            default:
                throw new IllegalArgumentException("不支持单位");
        }
        return incrAndGetCount(key,timeInterval,timeUnit,expirationTimeInSecond);
    }
    public long incrAndGetCount(String key, int timeInterval, TimeUnit timeUnit,long expirationTimeInSecond)
    {
        if(StrUtil.isBlank(key))
        {
            return 0;
        }
        long timeFactory;
        switch (timeUnit){
            case SECONDS:
                timeFactory= Instant.now().getEpochSecond()/timeInterval;
                break;
            case MINUTES:
                timeFactory= Instant.now().getEpochSecond()/timeInterval/60;
                break;
            case HOURS:
                timeFactory= Instant.now().getEpochSecond()/timeInterval/3600;
                break;
            default:
                throw new IllegalArgumentException("不支持单位");
        }
        String redis_key=key+":"+timeFactory;
        String luaScript =
                "if redis.call('exists', KEYS[1]) == 1 then " +
                        "  return redis.call('incr', KEYS[1]); " +
                        "else " +
                        "  redis.call('set', KEYS[1], 1); " +
                        "  redis.call('expire', KEYS[1], 180); " +  // 设置 180 秒过期时间
                        "  return 1; " +
                        "end";
        RScript script = redissonClient.getScript(IntegerCodec.INSTANCE);
        Object countObj = script.eval(RScript.Mode.READ_WRITE, luaScript, RScript.ReturnType.INTEGER, Collections.singletonList(redis_key), Collections.singletonList(expirationTimeInSecond));
        Long count=(Long) countObj;
        return count;
    }
}
