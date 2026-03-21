package com.chainpay.core.blockchain.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
public class MultiRpcFailoverProvider {

    private final List<String> rpcUrls;

    public MultiRpcFailoverProvider(@Value("${chainpay.web3.rpc-url}") String primaryRpcUrl) {
        this.rpcUrls = List.of(primaryRpcUrl, "http://localhost:8545");
    }

    public <T> T executeWithFailover(Function<Web3j, T> rpcCall) {
        Exception lastException = null;
        for (String url : rpcUrls) {
            try {
                Web3j client = Web3j.build(new HttpService(url));
                return rpcCall.apply(client);
            } catch (Exception ex) {
                log.warn("RPC call failed on node {}: {}. Attempting fallback...", url, ex.getMessage());
                lastException = ex;
            }
        }
        throw new RuntimeException("All RPC nodes in failover pool failed!", lastException);
    }
}
