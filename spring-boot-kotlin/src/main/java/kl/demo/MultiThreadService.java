package kl.demo;

import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

/**
 * @author: kl @kailing.pub
 * @date: 2019/7/12
 */
@Service
public class MultiThreadService {

    @Autowired
    private KotlinApplication application;

    @Autowired
    private GetWeatherService getWeatherService;

    private String weatherUrl = "http://localhost:8080/demo/mockWeatherApi?city=";

    public JSONObject getHuNanWeather(){
        JSONObject result = new JSONObject();
        CountDownLatch downLatch = new CountDownLatch(application.weatherContext.size());
        application.weatherContext.forEach((key,val)->{
            String url = weatherUrl + key;
            ForkJoinPool.commonPool().submit(()->{
                try {
                    result.put(key.toString(),getWeatherService.get(url));
                }catch (Exception ex){
                   ex.printStackTrace();
                }
               downLatch.countDown();
            });
        });
        try {
            downLatch.await();
        }catch (InterruptedException ex){ ex.printStackTrace(); }
        return result;
    }
}
