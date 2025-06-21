package com.chanseok.emsstudy.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ems/test")
public class EmsTestController {

    @PostMapping
    public void test(@RequestBody Map<String, Object> message) {
        System.out.println("message = " + message);
    }

}
