package com.paypal.notification_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paypal.notification_service.entity.Notification;
import com.paypal.notification_service.entity.Transaction;
import com.paypal.notification_service.repository.NotificationRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class NotificationConsumer {
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    public NotificationConsumer(NotificationRepository notificationRepository,ObjectMapper objectMapper){
        this.notificationRepository=notificationRepository;
        this.objectMapper= new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
//   @KafkaListener(topics= "txt-initiated", groupId = "notification-group")
//    public void listener(String  message) throws JsonProcessingException{
//       Transaction txt=objectMapper.readValue(message, Transaction.class);
//       System.out.println("Deserialized transaction : "+txt);
//       Notification notification= new Notification();
//       System.out.println("Created new Notification object ");
//       String recieverId=txt.getReceiverId().toString();
//       notification.setUserId(txt.getReceiverId());
//       System.out.println(" set reciever user id "+recieverId);
//       String senderId=txt.getSenderId().toString();
//
//       String notifyMessage="$ " + txt.getAmount() + "recieve from "+ txt.getSenderId().toString();
//       notification.setMessage(notifyMessage);
//       System.out.println("Set notification message "+ notifyMessage);
//       LocalDateTime now =LocalDateTime.now();
//       notification.setSentAt(now);
//       System.out.println("set sentAt timestamp "+now);
//
//       System.out.println("Saving notification: "+now);
//       notificationRepository.save(notification);
//    }
    @KafkaListener(topics = "txn-initiated", groupId = "notification-group")
    public void consumeTransaction(Transaction transaction) {
        System.out.println("📥 Received transaction: " + transaction);

        Notification notification = new Notification();
        notification.setUserId(transaction.getSenderId());
        notification.setMessage("💰 ₹" + transaction.getAmount() + " received from user " + transaction.getSenderId());
        notification.setSentAt(LocalDateTime.now());

        notificationRepository.save(notification);
        System.out.println("✅ Notification saved: " + notification);
    }




}
