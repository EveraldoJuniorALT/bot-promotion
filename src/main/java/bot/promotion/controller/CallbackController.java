package bot.promotion.controller;

import bot.promotion.service.AliexpressAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

        authService.exchangeCodeForToken(authorizationCode);
        return "Authentication in progress, check the server log";
    }


    /*
    I'll make request to this endpoint every 2 minutes
    so that the application doesn't shut down
     */
    @PostMapping("/refresh")
    public void testeGenerateLink() {
    }
}
