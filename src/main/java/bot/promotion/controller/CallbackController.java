package bot.promotion.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CallbackController {

    @GetMapping("/callback")
    public String handleAliexpressCallback(@RequestParam("code") String authorizationCode) {
        System.out.println("Callback received! authorizationCode: " + authorizationCode);
        return "success";
    }
}
