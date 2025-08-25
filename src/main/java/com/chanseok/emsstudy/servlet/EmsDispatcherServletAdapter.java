package com.chanseok.emsstudy.servlet;

import com.chanseok.emsstudy.web.EmsHttpServletRequest;
import com.chanseok.emsstudy.web.EmsHttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.ServletContext;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmsDispatcherServletAdapter {
    private final DispatcherServlet servlet;
    private final ServletContext servletContext;

    public void dispatch(String url, Object body) {
        EmsHttpServletRequest request = new EmsHttpServletRequest(servletContext);
        request.setMethod("POST");
        request.setRequestURI(url);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.toString().getBytes(StandardCharsets.UTF_8));

        EmsHttpServletResponse response = new EmsHttpServletResponse();

        log.info("url = {}", request.getRequestURL());
        log.info("uri = {}", request.getRequestURI());
        log.info("method = {}", request.getMethod());
        log.info("contentType = {}", request.getContentType());
        log.info("content = {}", new String(request.getContentAsByteArray(), StandardCharsets.UTF_8));

        try {
            servlet.service(request, response);

            if(response.getStatus() != 200) {
                throw new RuntimeException("EMS Processing Failed: " + response.getStatus() + " - " + response.getErrorMessage());
            }

            byte[] contentAsByteArray = response.getContentAsByteArray();
            String returnBody = new String(contentAsByteArray, StandardCharsets.UTF_8);
            log.info("returnBody = {}", returnBody);
        } catch (Exception e) {
            throw new RuntimeException("Dispatcher Call Failed", e);
        }

    }

}
