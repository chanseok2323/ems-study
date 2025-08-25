package com.chanseok.emsstudy.config;

import com.chanseok.emsstudy.listener.EmsMessageListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmsJmsRunner {
    private final EmsMessageListener messageListener;

    @EventListener(ApplicationReadyEvent.class)
    public void statJmsListener() throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        Connection connection = factory.createConnection("admin", "admin");
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = session.createQueue("ems.queue");
        MessageConsumer consumer = session.createConsumer(destination);

        consumer.setMessageListener(messageListener);
    }

}
