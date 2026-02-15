package ai.teraunit.core.api;

@org.springframework.stereotype.Controller
public class HomeRedirectController {
    @org.springframework.web.bind.annotation.GetMapping("/")
    public String index() {
        return "redirect:/swagger-ui.html";
    }
}
