package com.paypal.user_service.controller;

import com.paypal.user_service.entity.User;
import com.paypal.user_service.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/api/users/")
public class UserController {
private UserService userService;
public UserController(UserService userService){
    this.userService=userService;
}
@PostMapping("/create")
    public ResponseEntity<User> createUser(@RequestBody User user){
    return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(user));
}
@GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id){
    return userService.getUserById((id)).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());

}
@GetMapping("/all")
    public ResponseEntity<List<User>> getAllUsers(){
    return ResponseEntity.ok(userService.getAllUsers());
}
}
