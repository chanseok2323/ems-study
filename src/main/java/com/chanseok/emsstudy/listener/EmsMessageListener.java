package com.chanseok.emsstudy.listener;

import com.chanseok.emsstudy.servlet.EmsDispatcherServletAdapter;
import com.chanseok.emsstudy.utility.BeanUtils;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

@Component
public class EmsMessageListener implements MessageListener {
    @Override
    public void onMessage(Message message) {
        try {
            String payload = ((TextMessage) message).getText();

            EmsDispatcherServletAdapter dispatcherServletAdapter = (EmsDispatcherServletAdapter) BeanUtils.getBean(EmsDispatcherServletAdapter.class);
            dispatcherServletAdapter.dispatch("/ems/test", payload);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }

    }
}
