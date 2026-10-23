package myfirstapp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/multiply")
    public String multiply(@RequestParam int a, @RequestParam int b) {
        int ans = a * b;
        return "計算結果:" + ans;
    }

    @GetMapping("/hello")
    public String greeting(@RequestParam String name) {
        return "こんにちは、" + name + "さん！";
    }

    @GetMapping("/test")
    public String test() {
        return "これはテストページです!";
    }

    @GetMapping("/hello-world")
    public String hello() {
        return "hello world! Spring bootから表示しています!";
    }

    @GetMapping("/add")
    public String add(@RequestParam int a, @RequestParam int b) {
        int ans = a + b;
        return "計算結果:" + ans;
    }

    @GetMapping("/calculate")
    public String calculate(@RequestParam double a, @RequestParam double b, @RequestParam String op) {
        double ans;
        switch (op) {
            case "add":
                ans = a + b;
                break;
            case "subtract":
                ans = a - b;
                break;
            case "multiply":
                ans = a * b;
                break;
            case "divide":
                if (b == 0) {
                    return "0では割れません";
                }
                ans = a / b;
                break;
            default:
                return "計算方法が正しくありません";
        }
        return "計算結果:" + ans;
    }
}
