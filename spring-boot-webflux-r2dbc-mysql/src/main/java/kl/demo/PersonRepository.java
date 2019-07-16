package kl.demo;

import org.springframework.data.r2dbc.repository.query.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * @author: kl @kailing.pub
 * @date: 2019/7/16
 */
@Service
public interface PersonRepository extends ReactiveCrudRepository<Person, Long> {

    @Query("SELECT * FROM person WHERE name = :name")
    Mono<Person> findByName(String name);
}