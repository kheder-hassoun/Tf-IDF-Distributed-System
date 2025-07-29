package me.zookeeper.leader_election.config;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;


@Configuration
public class ZookeeperConfig {


    @Bean
    public ZooKeeper zooKeeper(@Value("${zookeeper.connection}") String zookeeperConnection) throws IOException {

        int sessionTimeout = 3000;
        return new ZooKeeper(zookeeperConnection, sessionTimeout, watchedEvent -> {
            // Log connection status
        });
    }


}
