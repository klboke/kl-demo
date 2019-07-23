package kl.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.web.bind.annotation.*;


/**
 * @author: kl @kailing.pub
 * @date: 2019/7/16
 */
@SpringBootApplication
@EntityScan(basePackages = {"kl.demo"})
@RequestMapping("/kl/demo")
@RestController
public class JetcdApplication {

    public static void main(String[] args) {
        SpringApplication.run(JetcdApplication.class, args);
    }
}
