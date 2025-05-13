package com.github.hyuno.spring_playground.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "ncp")
class NcpProperties {
    lateinit var endpoint: String
    lateinit var regionName: String
    lateinit var accessKey: String
    lateinit var secretKey: String
}

@Configuration
class NcpS3Config(@Autowired private val ncpProperties: NcpProperties) {
    @Bean
    fun assetNcpS3Client(): AmazonS3 {
        return AmazonS3ClientBuilder.standard()
            .withEndpointConfiguration(
                AwsClientBuilder.EndpointConfiguration(
                    ncpProperties.endpoint,
                    ncpProperties.regionName
                )
            )
            .withCredentials(
                AWSStaticCredentialsProvider(
                    BasicAWSCredentials(
                        ncpProperties.accessKey,
                        ncpProperties.secretKey
                    )
                )
            )
            .build()
    }
}