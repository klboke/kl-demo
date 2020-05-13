package kl.demo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

/**
 * @author: kl @kailing.pub
 * @date: 2019/7/16
 */
@Service
public interface PersonRepository extends CrudRepository<Person, Long> {

    @Query("FROM Person p WHERE p.name = :name")
    Person findByName(@Param("name") String name);
}
