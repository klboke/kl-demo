package kl.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.web.bind.annotation.*;

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
public class WebmvcJpaApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebmvcJpaApplication.class, args);
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
   private PersonRepository repository;

    @GetMapping("/gets")
    public List<Person> querys() {
       return entityManager.createQuery(" FROM Person").getResultList();
    }

    @GetMapping("/get")
    public Person query(final String name){
        return repository.findByName(name);
    }

    @PostMapping("/save")
    public Person save(@RequestBody final Person person){
         return repository.save(person);
    }

    @GetMapping("/delete")
    public String delete( final Long id){
        repository.deleteById(id);
        return "sucess";
    }
}
