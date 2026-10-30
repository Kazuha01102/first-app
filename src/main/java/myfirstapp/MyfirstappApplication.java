package myfirstapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MyfirstappApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyfirstappApplication.class, args);
    }
}
