package com.chainpay.core.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;

@Slf4j
@Configuration
public class HotWalletConfig {

    @Value("${chainpay.web3.hot-wallet-private-key}")
    private String privateKey;

    @Bean
    public Credentials hotWalletCredentials() {
        log.info("Initializing Hot Wallet Credentials from configured private key...");
        return Credentials.create(privateKey);
    }

    @Bean
    public String hotWalletAddress(Credentials hotWalletCredentials) {
        String address = hotWalletCredentials.getAddress();
        log.info("Hot Wallet Address initialized: {}", address);
        return address;
    }
}
