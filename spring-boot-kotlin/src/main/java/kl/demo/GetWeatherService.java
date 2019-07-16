package kl.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;

/**
 * @author: kl @kailing.pub
 * @date: 2019/7/12
 */
@Service
public class GetWeatherService {

    @Autowired
    private RestTemplateBuilder builder;


    /**
     * 获取天气信息
     * @param url
     * @return
     */
    public String get(String url) {
        return builder.build().getForObject(url,String.class);
    }
}
