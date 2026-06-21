package com.meddelivery.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                cache("insuranceProviders",    60),
                cache("pharmacies",             5),
                cache("activePharmacies",       5),
                cache("pharmacyInventory",      5),
                cache("insuranceCards",        10),
                cache("pharmacists",           10),
                cache("pharmacist",            10),
                cache("patientProfile",         5),
                cache("patientInsuranceCards",  5),
                cache("patientLocations",       5),
                cache("prescriptions",          5),
                cache("prescription",           5),
                cache("medicineRequests",       2),
                cache("medicineRequest",        2),
                cache("substitutions",          2)
        ));
        return manager;
    }

    private CaffeineCache cache(String name, int ttlMinutes) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(500)
                .build());
    }
}
