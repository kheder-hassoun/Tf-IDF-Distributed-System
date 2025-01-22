package web.server.IDF_web_server;

import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class IdfWebServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdfWebServerApplication.class, args);
	}
}

@Configuration
class ZooKeeperConfig {

	@Value("${zookeeper.connection}")
	private String zookeeperConnection;

	@Bean
	public ZooKeeper zooKeeper() throws IOException {
		// Connect to ZooKeeper with a session timeout of 3000ms
		return new ZooKeeper(zookeeperConnection, 3000, event -> {
			// Handle ZooKeeper events if needed
			System.out.println("ZooKeeper Event: " + event.getType());
		});
	}
}
