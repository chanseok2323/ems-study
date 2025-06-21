package com.chanseok.emsstudy;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

@SpringBootTest
public class EmsIntegrationTest {

    @Test
    public void testSendMessageToActiveMQ() throws Exception {
        Thread.sleep(1000);

        ConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        Connection connection = factory.createConnection("admin", "admin");
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = session.createQueue("ems.queue");
        MessageProducer producer = session.createProducer(destination);

        String payload = "{\"msg\":\"Hello from JMS Test\"}";
        TextMessage message = session.createTextMessage(payload);
        producer.send(message);

        producer.close();
        session.close();
        connection.close();

        Thread.sleep(1000); // Wait for the message to be processed
    }

}
