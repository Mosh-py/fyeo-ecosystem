package io.fyeo.starter;

import io.fyeo.starter.kms.KmsKeyProvider;
import io.fyeo.starter.kms.LocalKmsKeyProvider;
import io.fyeo.starter.properties.FyeoProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 3 Auto-Configuration for F.Y.E.O Document Vault.
 * 
 * Provides pure, headless cryptographic service beans without imposing UI or template bloat.
 */
@AutoConfiguration
@EnableConfigurationProperties(FyeoProperties.class)
@ConditionalOnProperty(prefix = "fyeo.vault", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FyeoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KmsKeyProvider kmsKeyProvider(FyeoProperties properties) {
        return new LocalKmsKeyProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public FyeoVaultService fyeoVaultService(FyeoProperties properties, KmsKeyProvider kmsKeyProvider) {
        return new FyeoVaultService(properties, kmsKeyProvider);
    }
}
