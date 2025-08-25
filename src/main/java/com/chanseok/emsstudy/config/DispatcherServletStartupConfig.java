package com.chanseok.emsstudy.config;

import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletRegistrationBean;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

@Configuration
public class DispatcherServletStartupConfig {

    @Bean(name = DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_REGISTRATION_BEAN_NAME)
    public DispatcherServletRegistrationBean dispatcherServletRegistrationBean(
            DispatcherServlet dispatcherServlet,
            WebMvcProperties webMvcProperties) {

        // 매핑 경로는 spring.mvc.servlet.path 를 그대로 사용
        String mapping = webMvcProperties.getServlet().getPath(); // 보통 "/"
        DispatcherServletRegistrationBean bean =
                new DispatcherServletRegistrationBean(dispatcherServlet, mapping);

        bean.setName(DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME); // "dispatcherServlet"
        bean.setLoadOnStartup(1); // <-- 핵심
        return bean;
    }
}
