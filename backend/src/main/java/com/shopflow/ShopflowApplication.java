package com.shopflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. UserDetailsServiceAutoConfiguration is excluded because we authenticate
 * with our own JWT filter; otherwise Spring Boot would create an in-memory user with a
 * random password and print it to the log, which is confusing and pointless here.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ShopflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopflowApplication.class, args);
    }
}
