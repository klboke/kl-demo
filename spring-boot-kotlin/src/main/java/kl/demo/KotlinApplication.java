package kl.demo;


import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;


@SpringBootApplication
@RequestMapping("/kl/demo")
@RestController
public class KotlinApplication {

    public static void main(String[] args) {
        SpringApplication.run(KotlinApplication.class, args);
    }


    @Autowired
    private KotlinAsyncService asyncService;

    @Autowired
    private MultiThreadService threadService;

    @GetMapping("/java")
    public JSONObject javaApi() throws Exception{
        System.out.println("java========================>");
        return threadService.getHuNanWeather();
    }

    @GetMapping("/kotlin")
    public JSONObject kotlinApi(){
        System.out.println("kotlin========================>");
        return asyncService.getHuNanWeather();
    }

    public HashMap weatherContext = new HashMap() {{
        put("changsha", "晴");
        put("zhuzhou", "阴");
        put("xiangtan", "暴雨");
        put("liuyang", "暴雨");
        put("liling", "多云");
    }};

    /**
     * 模拟天气服务api
     *
     * @param city
     * @return
     * @throws InterruptedException
     */
    @GetMapping("/mockWeatherApi")
    public Object mockWeatherApi(String city) throws InterruptedException {
        /**
         * 模拟服务耗时50ms
         */
        TimeUnit.MILLISECONDS.sleep(50);
        return weatherContext.get(city);
    }

}





