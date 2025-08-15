package com.yhang.springbootinit.blackfilter;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import cn.hutool.bloomfilter.BloomFilter;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.yhang.springbootinit.config.RedissonConfig;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Slf4j
public class BlackUtils {

    private static BitMapBloomFilter bloomFilter;
    // 判断 ip 是否在黑名单内
    public static boolean isBlackIp(String ip) {
        return bloomFilter.contains(ip);
    }

    // 重建 ip 黑名单
    public static void rebuildBlackIp(String configInfo) {
        if (StrUtil.isBlank(configInfo)) {
            configInfo = "{}";
        }
        // 解析 yaml 文件
        Yaml yaml = new Yaml();
        Map map = yaml.loadAs(configInfo, Map.class);
        // 获取 ip 黑名单
        List<String> blackIpList = (List<String>) map.get("blackIpList");
        //使用redisson实现
//        RedissonConfig redissonConfig=new RedissonConfig();
//        RedissonClient redisson = redissonConfig.redissonClient();
//        RBloomFilter<String> bloomFilter = redisson.getBloomFilter("blackIpList");
//        bloomFilter.tryInit(1000000L, 0.03);  // 预期插入 100 万数据，误判率 3%
//        for (String ip : blackIpList) {
//            bloomFilter.add(ip);
//        }
//        System.out.println(bloomFilter.contains("1.1.1.1"));
        // 加锁防止并发
        synchronized (BlackUtils.class) {
            if (CollectionUtil.isNotEmpty(blackIpList)) {
                // 注意构造参数的设置
                BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(100);
                for (String ip : blackIpList) {
                    bitMapBloomFilter.add(ip);
                }
                bloomFilter = bitMapBloomFilter;
            } else {
                bloomFilter = new BitMapBloomFilter(100);
            }
        }
        System.out.println(bloomFilter.contains("1.1.1.1"));
    }
}

