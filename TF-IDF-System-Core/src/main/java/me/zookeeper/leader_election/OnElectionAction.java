package me.zookeeper.leader_election;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

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
            // Get the current server port dynamically
            int runningPort = Integer.parseInt(environment.getProperty("local.server.port"));

            String leaderInfoData = String.valueOf(runningPort);

            // Create the znode if it doesn't exist, or update it if it does
            if (zooKeeper.exists(leaderInfoPath, false) == null) {
                zooKeeper.create(
                        leaderInfoPath,
                        leaderInfoData.getBytes(),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.EPHEMERAL
                );
            } else {
                zooKeeper.setData(leaderInfoPath, leaderInfoData.getBytes(), -1);
            }
        } catch (KeeperException | InterruptedException | NumberFormatException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onWorker() {
        try {
            // Get the current server port dynamically
            int runningPort = Integer.parseInt(environment.getProperty("local.server.port"));

            String currentServerAddress =
                    String.format("http://%s:%d", InetAddress.getLocalHost().getCanonicalHostName(), runningPort);

            serviceRegistry.registerToCluster(currentServerAddress);
        } catch (InterruptedException | UnknownHostException | KeeperException | NumberFormatException e) {
            e.printStackTrace();
        }
    }
}
