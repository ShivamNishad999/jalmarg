package org.growforge.jalmarg.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * AWS SDK clients. Credentials come from the default chain:
 * the EC2 / App Runner instance role in AWS, or ~/.aws/credentials / env vars locally.
 * Creating a client does not need credentials; they are only resolved on the first call,
 * so the app still starts on a laptop with AWS features switched off.
 */
@Configuration
public class AwsConfig {

    @Value("${jalmarg.aws.region}")
    private String region;

    @Value("${jalmarg.ai.region}")
    private String bedrockRegion;

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder().region(Region.of(bedrockRegion)).build();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder().region(Region.of(region)).build();
    }

    @Bean
    public SnsClient snsClient() {
        return SnsClient.builder().region(Region.of(region)).build();
    }
}
