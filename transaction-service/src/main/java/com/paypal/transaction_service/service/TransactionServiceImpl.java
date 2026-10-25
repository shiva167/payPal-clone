////package com.paypal.transaction_service.service;
////
////import com.fasterxml.jackson.databind.ObjectMapper;
////import com.paypal.transaction_service.entity.Transaction;
////import com.paypal.transaction_service.kafka.KafkaEventProducer;
////import com.paypal.transaction_service.repository.TransactionRepository;
////import org.springframework.stereotype.Service;
////
////import java.time.LocalDateTime;
////import java.util.List;
////@Service
////public class TransactionServiceImpl implements TransactionService {
////
//// private final TransactionRepository repository;
//// private final ObjectMapper objectMapper;
////    private final KafkaEventProducer kafkaEventProducer;
////
////    public TransactionServiceImpl(TransactionRepository repository, ObjectMapper objectMapper, KafkaEventProducer kafkaEventProducer) {
////        this.repository = repository;
////        this.objectMapper = objectMapper;
////        this.kafkaEventProducer = kafkaEventProducer;
////    }
////
////    @Override
////    public Transaction createTransaction(Transaction request) {
////        System.out.println("🚀 Entered createTransaction()");
////
////        Long senderId = request.getSenderId();
////        Long receiverId = request.getReceiverId();
////        Double amount = request.getAmount();
////
////
////
////
////        Transaction transaction = new Transaction();
////        transaction.setSenderId(senderId);
////        transaction.setReceiverId(receiverId);
////
////        transaction.setTimestamp(LocalDateTime.now());
////        transaction.setSenderName(request.getSenderName());
////        transaction.setReceiverName(request.getReceiverName());
////
////        transaction.setAmount(request.getAmount());
////        transaction.setStatus("SUCCESS");
////
////        System.out.println("📥 Incoming Transaction object: " + transaction);
////
////        Transaction saved = repository.save(transaction);
////        System.out.println("Saved Transaction from DB: " + saved);
////        try {
//////            String eventPayload= objectMapper.writeValueAsString(saved);
//////            String key= String.valueOf(saved.getId());
//////            kafkaEventProducer.sendTransactionEvent(key,eventPayload);
////            String key=String.valueOf(saved.getId());
////            kafkaEventProducer.sendTransactionEvent(key,saved);
////            System.out.println("Kafka message sent");
////        }catch (Exception ex){
////            System.err.println("Failed to send kafka event: "+ ex.getMessage());
////            ex.printStackTrace();
////        }
////        return saved;
////    }
////
////    @Override
////    public List<Transaction> getAllTransations() {
////        return repository.findAll();
////    }
////}
//package com.paypal.transaction_service.service;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.paypal.transaction_service.dto.TransferRequest;
//import com.paypal.transaction_service.entity.Transaction;
//import com.paypal.transaction_service.kafka.KafkaEventProducer;
//import com.paypal.transaction_service.repository.TransactionRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.*;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.HttpClientErrorException;
//import org.springframework.web.client.RestTemplate;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Service
//public class TransactionServiceImpl implements TransactionService {
//
//    private final TransactionRepository repository;
//    private final ObjectMapper objectMapper;
//    private final KafkaEventProducer kafkaEventProducer;
//
//    @Autowired
//    private RestTemplate restTemplate;
//
//    public TransactionServiceImpl(TransactionRepository repository,
//                                  KafkaEventProducer kafkaEventProducer,
//                                  ObjectMapper objectMapper) {
//        this.repository = repository;
//        this.objectMapper = objectMapper;
//        this.kafkaEventProducer = kafkaEventProducer;
//    }
//
//
//
//    @Override
//    public Transaction createTransaction(Transaction request) {
//        System.out.println("🚀 Entered createTransaction()");
//
//        Long senderId = request.getSenderId();
//        Long receiverId = request.getReceiverId();
//        Double amount = request.getAmount();
//
//        // Step 0: Mark transaction as PENDING
//        request.setStatus("PENDING");
//        request.setTimestamp(LocalDateTime.now());
//        Transaction savedTransaction = repository.save(request);
//        System.out.println("📥 Transaction PENDING saved: " + savedTransaction);
//
//        String walletServiceUrl = "http://localhost:8088/api/v1/wallets"; // wallet service base URL
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Content-Type", "application/json");
//
//        String holdReference = null;
//        boolean captured = false; // whether capture (actual debit) completed
//
//        try {
//            // Step 1: Place hold on sender wallet
//            String holdJson = String.format("{\"userId\": %d, \"currency\": \"INR\", \"amount\": %.2f}", senderId, amount);
//            HttpEntity<String> holdEntity = new HttpEntity<>(holdJson, headers);
//            ResponseEntity<String> holdResponse = restTemplate.postForEntity(walletServiceUrl + "/hold", holdEntity, String.class);
//
//            if (!holdResponse.getStatusCode().is2xxSuccessful() || holdResponse.getBody() == null) {
//                throw new RuntimeException("Failed to place hold: status=" + holdResponse.getStatusCode());
//            }
//
//            // Extract hold reference from response safely
//            JsonNode holdNode = objectMapper.readTree(holdResponse.getBody());
//            if (holdNode.get("holdReference") == null) {
//                throw new RuntimeException("Hold response missing holdReference: " + holdResponse.getBody());
//            }
//            holdReference = holdNode.get("holdReference").asText();
//            System.out.println("🛑 Hold placed: " + holdReference);
//
//            // NEW: check receiver wallet exists BEFORE capture
//            try {
//                ResponseEntity<String> receiverCheck = restTemplate.getForEntity(walletServiceUrl + "/" + receiverId, String.class);
//                if (!receiverCheck.getStatusCode().is2xxSuccessful()) {
//                    // release hold and fail the transaction
//                    tryReleaseHold(walletServiceUrl, holdReference, headers);
//                    System.out.println("🔄 Receiver wallet missing → hold released: " + holdReference);
//                    savedTransaction.setStatus("FAILED");
//                    savedTransaction = repository.save(savedTransaction);
//                    System.out.println("❌ Transaction FAILED (receiver wallet missing): " + savedTransaction);
//                    return savedTransaction;
//                }
//            } catch (HttpClientErrorException hx) {
//                // receiver not found or other 4xx
//                System.err.println("❌ Receiver wallet check failed: " + hx.getResponseBodyAsString());
//                tryReleaseHold(walletServiceUrl, holdReference, headers);
//                savedTransaction.setStatus("FAILED");
//                savedTransaction = repository.save(savedTransaction);
//                System.out.println("❌ Transaction FAILED (receiver check error): " + savedTransaction);
//                return savedTransaction;
//            }
//
//            // Step 2: Capture hold → debit sender wallet
//            String captureJson = String.format("{\"holdReference\": \"%s\"}", holdReference);
//            HttpEntity<String> captureEntity = new HttpEntity<>(captureJson, headers);
//            ResponseEntity<String> captureResponse = restTemplate.postForEntity(walletServiceUrl + "/capture", captureEntity, String.class);
//
//            if (!captureResponse.getStatusCode().is2xxSuccessful()) {
//                // If capture failed, release hold and fail
//                System.err.println("❌ Capture failed: status=" + captureResponse.getStatusCode() + " body=" + captureResponse.getBody());
//                tryReleaseHold(walletServiceUrl, holdReference, headers);
//                savedTransaction.setStatus("FAILED");
//                savedTransaction = repository.save(savedTransaction);
//                System.out.println("❌ Transaction FAILED (capture failed): " + savedTransaction);
//                return savedTransaction;
//            }
//            captured = true;
//            System.out.println("💸 Hold captured → sender debited");
//
//            // Step 3: Credit receiver wallet
//            String creditJson = String.format("{\"userId\": %d, \"currency\": \"INR\", \"amount\": %.2f}", receiverId, amount);
//            HttpEntity<String> creditEntity = new HttpEntity<>(creditJson, headers);
//            try {
//                ResponseEntity<String> creditResponse = restTemplate.postForEntity(walletServiceUrl + "/credit", creditEntity, String.class);
//                if (!creditResponse.getStatusCode().is2xxSuccessful()) {
//                    throw new RuntimeException("Failed to credit receiver: status=" + creditResponse.getStatusCode());
//                }
//                System.out.println("💰 Receiver credited successfully");
//            } catch (HttpClientErrorException creditEx) {
//                // Credit failed AFTER capture — perform compensating refund to sender
//                System.err.println("❌ Credit failed: " + creditEx.getResponseBodyAsString());
//
//                // Attempt to refund sender
//                try {
//                    String refundJson = String.format("{\"userId\": %d, \"currency\": \"INR\", \"amount\": %.2f}", senderId, amount);
//                    HttpEntity<String> refundEntity = new HttpEntity<>(refundJson, headers);
//                    ResponseEntity<String> refundResponse = restTemplate.postForEntity(walletServiceUrl + "/credit", refundEntity, String.class);
//                    if (refundResponse.getStatusCode().is2xxSuccessful()) {
//                        System.out.println("🔁 Compensating refund to sender succeeded");
//                    } else {
//                        System.err.println("❌ Compensating refund to sender returned non-2xx: " + refundResponse.getStatusCode());
//                    }
//                } catch (Exception ex) {
//                    System.err.println("❌ Compensating refund to sender failed: " + ex.getMessage());
//                }
//
//                savedTransaction.setStatus("FAILED");
//                savedTransaction = repository.save(savedTransaction);
//                System.out.println("❌ Transaction FAILED (credit failed & refunded sender): " + savedTransaction);
//                return savedTransaction;
//            }
//
//            // Step 4: Mark transaction as SUCCESS
//            savedTransaction.setStatus("SUCCESS");
//            savedTransaction = repository.save(savedTransaction);
//            System.out.println("✅ Transaction SUCCESS: " + savedTransaction);
//
//        } catch (HttpClientErrorException e) {
//            System.err.println("❌ Wallet service returned error: " + e.getResponseBodyAsString());
//            if (holdReference != null && !captured) {
//                tryReleaseHold(walletServiceUrl, holdReference, headers);
//            }
//            savedTransaction.setStatus("FAILED");
//            savedTransaction = repository.save(savedTransaction);
//            System.out.println("❌ Transaction FAILED saved (4xx): " + savedTransaction);
//            return savedTransaction;
//        } catch (Exception e) {
//            System.err.println("❌ Transaction failed: " + e.getMessage());
//            if (holdReference != null && !captured) {
//                tryReleaseHold(walletServiceUrl, holdReference, headers);
//            }
//            savedTransaction.setStatus("FAILED");
//            savedTransaction = repository.save(savedTransaction);
//            System.out.println("❌ Transaction FAILED saved: " + savedTransaction);
//            return savedTransaction;
//        }
//
//        // Step 6: Send Kafka Event
//        try {
//            String key = String.valueOf(savedTransaction.getId());
//            kafkaEventProducer.sendTransactionEvent(key, savedTransaction);
//            System.out.println("🚀 Kafka message sent");
//        } catch (Exception e) {
//            System.err.println("❌ Failed to send Kafka event: " + e.getMessage());
//            e.printStackTrace();
//        }
//
//        return savedTransaction;
//    }
//
//    // Helper: best-effort release via path-style endpoint
//    private void tryReleaseHold(String walletServiceUrl, String holdReference, HttpHeaders headers) {
//        if (holdReference == null) return;
//        try {
//            // Use path-style release (matches WalletController: POST /release/{holdReference})
//            String releaseUrl = walletServiceUrl + "/release/" + holdReference;
//            System.out.println("ℹ️ Attempting hold release via: " + releaseUrl);
//            ResponseEntity<String> releaseResp = restTemplate.postForEntity(releaseUrl, null, String.class);
//            System.out.println("ℹ️ Release response: status=" + releaseResp.getStatusCode() + " body=" + releaseResp.getBody());
//        } catch (Exception ex) {
//            // Best-effort: log and move on (we don't want the whole transaction to crash on release failure)
//            System.err.println("❌ Failed to release hold [" + holdReference + "]: " + ex.getMessage());
//        }
//    }
//
//
//    @Override
//    public Transaction getTransactionById(Long id) {
//        return repository.findById(id).orElse(null);
//    }
//
//    public List<Transaction> getTransactionsByUser(Long userId) {
//        return repository.findBySenderIdOrReceiverId(userId, userId);
//    }
//
//
//}
package com.paypal.transaction_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.transaction_service.dto.TransferRequest;
import com.paypal.transaction_service.entity.Transaction;
import com.paypal.transaction_service.kafka.KafkaEventProducer;
import com.paypal.transaction_service.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;
    private final ObjectMapper objectMapper;
    private final KafkaEventProducer kafkaEventProducer;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate; // Injected for Idempotency tracking

    private static final String IDEMPOTENCY_PREFIX = "transaction:idempotency:";

    public TransactionServiceImpl(TransactionRepository repository,
                                  KafkaEventProducer kafkaEventProducer,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.kafkaEventProducer = kafkaEventProducer;
    }

    @Override
    public Transaction createTransaction(Transaction request) {
        System.out.println("🚀 Entered createTransaction()");

        Long senderId = request.getSenderId();
        Long receiverId = request.getReceiverId();
        Double amount = request.getAmount();

        // 1. Generate a deterministic Idempotency Key based on transaction details
        // (Alternatively, you can pass an Idempotency-Key header from your controller)
        String idempotencyKey = String.format("%d-%d-%.2f", senderId, receiverId, amount);
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;

        // 2. Check Redis for existing or in-flight transaction
        String existingTransactionId = redisTemplate.opsForValue().get(redisKey);
        if (existingTransactionId != null) {
            if ("PROCESSING".equals(existingTransactionId)) {
                throw new RuntimeException("A transaction with the exact same details is already in progress.");
            }
            System.out.println("🔁 Idempotent retry detected! Returning existing transaction ID: " + existingTransactionId);
            return repository.findById(Long.valueOf(existingTransactionId)).orElse(null);
        }

        // 3. Lock this transaction key in Redis for 10 minutes to prevent duplicate parallel requests
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofMinutes(10));
        if (Boolean.FALSE.equals(isLocked)) {
            throw new RuntimeException("Concurrent transaction request detected.");
        }

        // Step 0: Mark transaction as PENDING
        request.setStatus("PENDING");
        request.setTimestamp(LocalDateTime.now());
        Transaction savedTransaction = repository.save(request);
        System.out.println("📥 Transaction PENDING saved: " + savedTransaction);

        String walletServiceUrl = "http://localhost:8088/api/v1/wallets"; // wallet service base URL
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        String holdReference = null;
        boolean captured = false; // whether capture (actual debit) completed

        try {
            // Step 1: Place hold on sender wallet
            String holdJson = String.format("{\"userId\": %d, \"currency\": \"INR\", \"amount\": %.2f}", senderId, amount);
            HttpEntity<String> holdEntity = new HttpEntity<>(holdJson, headers);
            ResponseEntity<String> holdResponse = restTemplate.postForEntity(walletServiceUrl + "/hold", holdEntity, String.class);

            if (!holdResponse.getStatusCode().is2xxSuccessful() || holdResponse.getBody() == null) {
                throw new RuntimeException("Failed to place hold: status=" + holdResponse.getStatusCode());
            }

            // Extract hold reference from response safely
            JsonNode holdNode = objectMapper.readTree(holdResponse.getBody());
            if (holdNode.get("holdReference") == null) {
                throw new RuntimeException("Hold response missing holdReference: " + holdResponse.getBody());
            }
            holdReference = holdNode.get("holdReference").asText();
            System.out.println("🛑 Hold placed: " + holdReference);

            // Check receiver wallet exists BEFORE capture
            try {
                ResponseEntity<String> receiverCheck = restTemplate.getForEntity(walletServiceUrl + "/" + receiverId, String.class);
                if (!receiverCheck.getStatusCode().is2xxSuccessful()) {
                    tryReleaseHold(walletServiceUrl, holdReference, headers);
                    savedTransaction.setStatus("FAILED");
                    savedTransaction = repository.save(savedTransaction);
                    redisTemplate.delete(redisKey); // Clear lock so user can correct and retry if needed
                    return savedTransaction;
                }
            } catch (HttpClientErrorException hx) {
                tryReleaseHold(walletServiceUrl, holdReference, headers);
                savedTransaction.setStatus("FAILED");
                savedTransaction = repository.save(savedTransaction);
                redisTemplate.delete(redisKey);
                return savedTransaction;
            }

            // Step 2: Capture hold → debit sender wallet
            String captureJson = String.format("{\"holdReference\": \"%s\"}", holdReference);
            HttpEntity<String> captureEntity = new HttpEntity<>(captureJson, headers);
            ResponseEntity<String> captureResponse = restTemplate.postForEntity(walletServiceUrl + "/capture", captureEntity, String.class);

            if (!captureResponse.getStatusCode().is2xxSuccessful()) {
                tryReleaseHold(walletServiceUrl, holdReference, headers);
                savedTransaction.setStatus("FAILED");
                savedTransaction = repository.save(savedTransaction);
                redisTemplate.delete(redisKey);
                return savedTransaction;
            }
            captured = true;

            // Step 3: Credit receiver wallet
            String creditJson = String.format("{\"userId\": %d, \"currency\": \"INR\", \"amount\": %.2f}", receiverId, amount);
            HttpEntity<String> creditEntity = new HttpEntity<>(creditJson, headers);
            try {
                ResponseEntity<String> creditResponse = restTemplate.postForEntity(walletServiceUrl + "/credit", creditEntity, String.class);
                if (!creditResponse.getStatusCode().is2xxSuccessful()) {
                    throw new RuntimeException("Failed to credit receiver");
                }
            } catch (HttpClientErrorException creditEx) {
                // Compensating refund to sender
                try {
                    String refundJson = String.format("{\"userId\": %d, \"currency\": \"INR\", \"amount\": %.2f}", senderId, amount);
                    HttpEntity<String> refundEntity = new HttpEntity<>(refundJson, headers);
                    restTemplate.postForEntity(walletServiceUrl + "/credit", refundEntity, String.class);
                } catch (Exception ignored) {}

                savedTransaction.setStatus("FAILED");
                savedTransaction = repository.save(savedTransaction);
                redisTemplate.delete(redisKey);
                return savedTransaction;
            }

            // Step 4: Mark transaction as SUCCESS
            savedTransaction.setStatus("SUCCESS");
            savedTransaction = repository.save(savedTransaction);

            // 4. Update Redis with the finalized successful transaction ID (set TTL to 24 hours)
            redisTemplate.opsForValue().set(redisKey, String.valueOf(savedTransaction.getId()), Duration.ofHours(24));

        } catch (Exception e) {
            if (holdReference != null && !captured) {
                tryReleaseHold(walletServiceUrl, holdReference, headers);
            }
            savedTransaction.setStatus("FAILED");
            savedTransaction = repository.save(savedTransaction);
            redisTemplate.delete(redisKey); // Clear lock on failure to permit safe retry
            return savedTransaction;
        }

        // Step 5: Send Kafka Event
        try {
            String key = String.valueOf(savedTransaction.getId());
            kafkaEventProducer.sendTransactionEvent(key, savedTransaction);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return savedTransaction;
    }

    private void tryReleaseHold(String walletServiceUrl, String holdReference, HttpHeaders headers) {
        if (holdReference == null) return;
        try {
            String releaseUrl = walletServiceUrl + "/release/" + holdReference;
            restTemplate.postForEntity(releaseUrl, null, String.class);
        } catch (Exception ignored) {}
    }

    @Override
    public Transaction getTransactionById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public List<Transaction> getTransactionsByUser(Long userId) {
        return repository.findBySenderIdOrReceiverId(userId, userId);
    }
}