package com.github.hyuno.spring_playground.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "s3")
class S3Properties {
    lateinit var regionName: String
    lateinit var accessKey: String
    lateinit var secretKey: String
}

@Configuration
class S3Config(@Autowired private val s3Properties: S3Properties) {
    @Bean
    fun assetS3Client(): AmazonS3 {
        return AmazonS3ClientBuilder.standard()
            .withRegion(
                s3Properties.regionName
            )
            .withCredentials(
                AWSStaticCredentialsProvider(
                    BasicAWSCredentials(
                        s3Properties.accessKey,
                        s3Properties.secretKey
                    )
                )
            )
            .build()
    }
}