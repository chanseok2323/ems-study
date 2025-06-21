package com.chanseok.emsstudy;

import com.chanseok.emsstudy.provider.ApplicationContextProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class EmsStudyApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmsStudyApplication.class, args);
    }

    @Bean
    public ApplicationContextProvider applicationContextProvider(ApplicationContext context) {
        ApplicationContextProvider provider = new ApplicationContextProvider();
        provider.setApplicationContext(context);
        return provider;
    }
}
