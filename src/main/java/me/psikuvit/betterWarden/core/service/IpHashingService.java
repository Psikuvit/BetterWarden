package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.util.IpHasher;
import org.springframework.stereotype.Service;

@Service
public class IpHashingService {

    private final CoreConfig config;

    public IpHashingService(CoreConfig config) {
        this.config = config;
    }

    public String hash(String ip) {
        return IpHasher.hash(ip, config.getSecurity().getIpSalt());
    }
}
