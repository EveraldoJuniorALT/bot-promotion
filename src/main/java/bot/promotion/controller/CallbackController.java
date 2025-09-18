package bot.promotion.controller;

import bot.promotion.service.AliexpressAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CallbackController {

    private final AliexpressAuthService authService;

    @Autowired
    public CallbackController(AliexpressAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/callback")
    public String handleAliexpressCallback(@RequestParam("code") String authorizationCode) {
        System.out.println("Callback received! authorizationCode: " + authorizationCode);

        String callbackUrl = "https://bot-promotion.onrender.com/callback";

        authService.exchangeCodeForToken(authorizationCode, callbackUrl);
        return "Authentication in progress, check the server log";
    }
}
