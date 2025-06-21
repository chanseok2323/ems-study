package com.chanseok.emsstudy.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class LoggingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("========= Logging Interceptor preHandle START =========");
        log.info("Request URL: {}", request.getRequestURL());
        log.info("========= Logging Interceptor preHandle END =========");
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        log.info("========= Logging Interceptor postHandle START =========");
        log.info("Response Status: {}", response.getStatus());
        log.info("========= Logging Interceptor postHandle END =========");
    }
}
