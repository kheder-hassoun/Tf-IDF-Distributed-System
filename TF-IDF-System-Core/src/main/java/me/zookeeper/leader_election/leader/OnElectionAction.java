package me.zookeeper.leader_election.leader;

import me.zookeeper.leader_election.registry.ServiceRegistry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class OnElectionAction implements OnElectionCallback {

    private final ServiceRegistry serviceRegistry;
    private final ZooKeeper zooKeeper;
    private final Environment environment; // Inject Spring Environment

    @Autowired
    public OnElectionAction(ServiceRegistry serviceRegistry, ZooKeeper zooKeeper, Environment environment) {
        this.serviceRegistry = serviceRegistry;
        this.zooKeeper = zooKeeper;
        this.environment = environment;
    }

    @Override
    public void onElectedToBeLeader() {
        serviceRegistry.unregisterFromCluster();
        serviceRegistry.registerForUpdates();

        String leaderInfoPath = "/leader_info";

        try {

            String podIp = System.getenv("POD_IP");
            int port     = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8085"));
            String currentServerAddress = "http://" + podIp + ":" + port;




           // String leaderInfoData = String.valueOf(runningPort);

            // Create the znode if it doesn't exist, or update it if it does
            if (zooKeeper.exists(leaderInfoPath, false) == null) {
                zooKeeper.create(
                        leaderInfoPath,
                        currentServerAddress.getBytes(),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.EPHEMERAL
                );
            } else {
                zooKeeper.setData(leaderInfoPath, currentServerAddress.getBytes(), -1);
            }
        } catch (InterruptedException | KeeperException | NumberFormatException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onWorker() {
        try {
            // 1) Get the pod’s IP from the Downward‑API env var
            String podIp = System.getenv("POD_IP");          // set in Deployment
            System.out.println(" debuuuuuuug  IP : "+ podIp);
            // 2) Get the port; fall back to 8085
            int port = Integer.parseInt(
                    environment.getProperty("server.port", "8085"));

            // 3) Register IP:port, not hostname
            String currentServerAddress = String.format("http://%s:%d", podIp, port);

            serviceRegistry.registerToCluster(currentServerAddress);
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
    }
}
