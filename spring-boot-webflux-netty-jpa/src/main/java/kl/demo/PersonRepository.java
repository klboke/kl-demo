package kl.demo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;

/**
 * @author: kl @kailing.pub
 * @date: 2019/7/16
 */
@Service
public interface PersonRepository extends CrudRepository<Person, Long> {

    @Modifying
    @Query("FROM Person p WHERE p.name = :name")
    Person findByName(String name);
}