package kl.demo;

import io.netty.channel.epoll.EpollEventLoopGroup;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.stereotype.Service;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.LoopResources;

/**
 * @author: kl @kailing.pub
 * @date: 2019/7/17
 */
@Service
public class myNettyServer implements NettyServerCustomizer {
    @Override
    public HttpServer apply(HttpServer httpServer) {
        httpServer.tcpConfiguration(tcpServer -> {
            LoopResources resources = LoopResources.create("kl-webflux");
             resources.onServerSelect(true);
            EpollEventLoopGroup group =  new EpollEventLoopGroup(200);

            tcpServer.runOn(resources);
            return tcpServer;
        });
        return httpServer;
    }
}
