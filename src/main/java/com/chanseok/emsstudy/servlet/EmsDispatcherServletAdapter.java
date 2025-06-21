package com.chanseok.emsstudy.servlet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmsDispatcherServletAdapter {
    private final DispatcherServlet servlet;
    private final ServletContext servletContext;
    private final DispatcherServlet dispatcherServlet;


    public void dispatch(String url, Object body) {
        if (dispatcherServlet.getServletConfig() == null) {
            log.info("DispatcherServlet is not initialized. Cannot dispatch request.");
            try {

                dispatcherServlet.init(new MockServletConfig(servletContext, "dispatcherServlet"));
            } catch (ServletException e) {
                throw new RuntimeException(e);
            }
        }

        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setMethod("POST");
        request.setRequestURI(url);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.toString().getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();

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
        } catch (Exception e) {
            throw new RuntimeException("Dispatcher Call Failed", e);
        }

    }

}
