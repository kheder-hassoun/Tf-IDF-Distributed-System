package me.zookeeper.leader_election;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ZookeeperLeaderElectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZookeeperLeaderElectionApplication.class, args);
    }
}
