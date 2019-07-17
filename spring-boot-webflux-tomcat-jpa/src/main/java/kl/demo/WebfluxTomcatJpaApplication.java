package kl.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.persistence.EntityManager;
import java.util.List;

/**
 * @author: kl @kailing.pub
 * @date: 2019/7/16
 */
@SpringBootApplication
@EntityScan(basePackages = {"kl.demo"})
@RequestMapping("/kl/demo")
@RestController
public class WebfluxTomcatJpaApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebfluxTomcatJpaApplication.class, args);
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PersonRepository repository;

    @GetMapping("/gets")
    public Flux<Person> querys() {
        List<Person> list = entityManager.createQuery(" FROM Person").getResultList();
        return Flux.fromIterable(list);
    }

    @GetMapping("/get")
    public Mono<Person> query(final String name) {
        return Mono.just(repository.findByName(name));
    }

    @PostMapping("/save")
    public Mono<Person> save(@RequestBody final Person person) {
        return Mono.just(repository.save(person));
    }

    @GetMapping("/delete")
    public Mono<String> delete(final Long id) {
        repository.deleteById(id);
        return Mono.just("sucess");
    }
}
