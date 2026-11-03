
package com.campus.continuity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class ContinuityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContinuityApplication.class, args);
    }
}
