package kl.demo;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * @author: kl @kailing.pub
 * @date: 2019/7/16
 */
@SpringBootApplication
@RequestMapping("/kl/demo")
@RestController
public class R2dbcApplication {

    public static void main(String[] args) {
        SpringApplication.run(R2dbcApplication.class, args);
    }

    @Autowired
    private DatabaseClient client;

    @Autowired
    private PersonRepository repository;

    @GetMapping("/gets")
    public Flux<Person> querys() {
        Flux<Person> affectedRows = client.execute()
                .sql("SELECT id, name, age FROM person")
                .as(Person.class)
                .fetch().all();
        return affectedRows;
    }

    @GetMapping("/get")
    public Mono<Person> query(final String name){
        return repository.findByName(name);
    }

    @PostMapping("/save")
    public Mono<Person> save(@RequestBody final Person person){
        return repository.save(person);
    }

    @GetMapping("/delete")
    public Mono<String> delete( final Long id){
        repository.deleteById(id);
        return Mono.just("sucess");
    }

}





