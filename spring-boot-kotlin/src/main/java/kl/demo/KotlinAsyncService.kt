package kl.demo

import com.alibaba.fastjson.JSONObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.concurrent.CountDownLatch

/**
 *
 * @author: kl @kailing.pub
 * @date: 2019/7/12
 */
@Service
class KotlinAsyncService(private val weatherService: GetWeatherService, private val demoApplication: KotlinApplication){
    private val weatherUrl = "http://localhost:8080/demo/mockWeatherApi?city="
    fun getHuNanWeather(): JSONObject{
        val result = JSONObject()
        val count = CountDownLatch(demoApplication.weatherContext.size)
        for (city in demoApplication.weatherContext){
            val url = weatherUrl + city.key
            GlobalScope.launch {
                result[city.key.toString()] = weatherService.get(url)
                count.countDown()
            }
        }
        count.await()
        return result
    }
}