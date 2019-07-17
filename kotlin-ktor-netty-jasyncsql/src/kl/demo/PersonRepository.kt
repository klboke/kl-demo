package kl.demo

import com.github.jasync.sql.db.Configuration
import com.github.jasync.sql.db.Connection
import com.github.jasync.sql.db.ConnectionPoolConfigurationBuilder
import com.github.jasync.sql.db.mysql.pool.MySQLConnectionFactory
import com.github.jasync.sql.db.pool.ConnectionPool
import io.ktor.http.Parameters
import me.hltj.kaggregator.demo.Person
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 *
 *
 * @author: kl @kailing.pub
 * @date: 2019/7/17
 */

val poolConfiguration = ConnectionPoolConfigurationBuilder(
        "127.0.0.1",                            // maxObjects
        3306,  // maxIdle
        "kl-demo",
        "root",
        "sasa",
        50,
        TimeUnit.SECONDS.toMillis(1),
        6
).build()

val connection: Connection = ConnectionPool(
        MySQLConnectionFactory(Configuration(
                username = "root",
                password = "sasa",
                host = "127.0.0.1",
                port = 3306,
                database = "kl-demo"
        )), poolConfiguration)

fun getPersons(parameters: Parameters): List<Person> {
    val result = ArrayList<Person>()
    connection.inTransaction {
        connection.sendQuery("select * from person").get().rows.forEach(Consumer {
            val person = it.getInt("id")?.let { it1 -> it.getString("name")?.let { it2 -> Person(it1, it.getInt("age")!!, it2) } };
            result.add(person!!)
        })
        return@inTransaction
    }
    return result
}


