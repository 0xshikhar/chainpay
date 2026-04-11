package com.chainpay.core.blockchain.client;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
public class MultiRpcFailoverProvider {

    private final List<Web3j> clientPool;
    private final List<String> rpcUrls;

    public MultiRpcFailoverProvider(@Value("${chainpay.web3.rpc-url}") String primaryRpcUrl) {
        this.rpcUrls = List.of(primaryRpcUrl, "http://127.0.0.1:8545");
        this.clientPool = new ArrayList<>();
        
        for (String url : rpcUrls) {
            try {
                log.info("[RPC-FAILOVER] Initializing pooled Web3j client for endpoint: {}", url);
                Web3j client = Web3j.build(new HttpService(url));
                this.clientPool.add(client);
            } catch (Exception e) {
                log.warn("[RPC-FAILOVER] Failed to pre-build Web3j client for {}: {}", url, e.getMessage());
            }
        }
    }

    public <T> T executeWithFailover(Function<Web3j, T> rpcCall) {
        Exception lastException = null;
        for (int i = 0; i < clientPool.size(); i++) {
            Web3j client = clientPool.get(i);
            String url = rpcUrls.get(i);
            try {
                return rpcCall.apply(client);
            } catch (Exception ex) {
                log.warn("[RPC-FAILOVER] Call failed on node {}: {}. Trying next endpoint...", url, ex.getMessage());
                lastException = ex;
            }
        }
        throw new RuntimeException("All RPC nodes in failover pool failed!", lastException);
    }

    @PreDestroy
    public void shutdown() {
        for (Web3j client : clientPool) {
            try {
                client.shutdown();
            } catch (Exception ignored) {}
        }
    }
}
