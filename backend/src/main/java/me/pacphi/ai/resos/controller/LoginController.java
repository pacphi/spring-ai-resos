package me.pacphi.ai.resos.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for authentication-related pages.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home() {
        // Redirect to Swagger UI as the default home page
        return "redirect:/swagger-ui.html";
    }
}
