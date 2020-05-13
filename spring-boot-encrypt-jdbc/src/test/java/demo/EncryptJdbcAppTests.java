package demo;

import kl.demo.EncryptJdbcApp;
import kl.demo.Person;
import kl.demo.PersonRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = EncryptJdbcApp.class)
@Transactional
@Rollback(false)
public class EncryptJdbcAppTests {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PersonRepository personRepository;

    @Test
    public void test() {
        Person person = personRepository.findByName("陈某某1");
        System.err.println(person.getName());
    }

    @Test
    public void contextLoads() {
        Person person = new Person();
        person.setAge(2);
        person.setName("陈某某1");
        entityManager.persist(person);
    }

}
