package com.chainpay.core.config;

import com.chainpay.core.ledger.domain.Account;
import com.chainpay.core.ledger.domain.AccountStatus;
import com.chainpay.core.ledger.domain.AccountType;
import com.chainpay.core.ledger.domain.Asset;
import com.chainpay.core.ledger.repository.AccountRepository;
import com.chainpay.core.ledger.repository.AssetRepository;
import com.chainpay.core.security.user.Role;
import com.chainpay.core.security.user.User;
import com.chainpay.core.security.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seedAssetsAndAccounts();
    }

    private void seedUsers() {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .email("admin@chainpay.io")
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build());
            log.info("Seeded default admin user (username: admin)");
        }

        if (!userRepository.existsByUsername("operator")) {
            userRepository.save(User.builder()
                    .username("operator")
                    .password(passwordEncoder.encode("operator123"))
                    .email("operator@chainpay.io")
                    .role(Role.OPERATOR)
                    .enabled(true)
                    .build());
            log.info("Seeded default operator user (username: operator)");
        }
    }

    private void seedAssetsAndAccounts() {
        if (assetRepository.count() == 0) {
            Asset usdcAsset = assetRepository.save(Asset.builder()
                    .symbol("USDC")
                    .contractAddress("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
                    .chainId(31337L)
                    .decimals(6)
                    .build());

            Asset ethAsset = assetRepository.save(Asset.builder()
                    .symbol("ETH")
                    .contractAddress("0x0000000000000000000000000000000000000000")
                    .chainId(31337L)
                    .decimals(18)
                    .build());

            log.info("Seeded default Assets: USDC (ID: {}) and Native ETH (ID: {})", usdcAsset.getId(), ethAsset.getId());

            Account customerAccount = accountRepository.save(Account.builder()
                    .accountNumber("ACC-CUSTOMER-001")
                    .accountType(AccountType.CUSTOMER_AVAILABLE)
                    .asset(usdcAsset)
                    .status(AccountStatus.ACTIVE)
                    .build());

            Account ethCustomerAccount = accountRepository.save(Account.builder()
                    .accountNumber("ACC-CUSTOMER-ETH-001")
                    .accountType(AccountType.CUSTOMER_AVAILABLE)
                    .asset(ethAsset)
                    .status(AccountStatus.ACTIVE)
                    .build());

            Account hotWalletAccount = accountRepository.save(Account.builder()
                    .accountNumber("ACC-HOTWALLET-001")
                    .accountType(AccountType.SYSTEM_HOT_WALLET)
                    .asset(usdcAsset)
                    .status(AccountStatus.ACTIVE)
                    .build());

            log.info("Seeded default Accounts: Customer USDC (ID: {}), Customer ETH (ID: {}), and Hot Wallet (ID: {})",
                    customerAccount.getId(), ethCustomerAccount.getId(), hotWalletAccount.getId());
        }
    }
}
