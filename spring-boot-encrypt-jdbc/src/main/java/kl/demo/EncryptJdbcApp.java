package kl.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author: kl @kailing.pub
 * @date: 2020/5/11
 */
@SpringBootApplication
@EntityScan(basePackages = {"kl.demo"})
@RequestMapping("/kl/demo")
@RestController
public class EncryptJdbcApp {

    public static void main(String[] args) {
        SpringApplication.run(EncryptJdbcApp.class, args);
    }

}
