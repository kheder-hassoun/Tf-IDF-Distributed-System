package me.zookeeper.leader_election;


import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class Application implements Watcher, CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private final ZooKeeper zooKeeper;
    private final ServiceRegistry serviceRegistry;
    private final LeaderElection leaderElection;

    public Application(
            ZooKeeper zooKeeper,
            ServiceRegistry serviceRegistry,
            LeaderElection leaderElection) {
        this.zooKeeper = zooKeeper;
        this.serviceRegistry = serviceRegistry;
        this.leaderElection = leaderElection;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Connected to ZooKeeper");

        leaderElection.volunteerForLeadership();
        leaderElection.reelectLeader();

        runApplication();
    }

    private void runApplication() throws InterruptedException {
        synchronized (zooKeeper) {
            zooKeeper.wait();
        }
    }

    @Override
    public void process(WatchedEvent event) {
        switch (event.getType()) {
            case None:
                if (event.getState() == Event.KeeperState.SyncConnected) {
                    logger.info("Successfully connected to ZooKeeper");
                } else if (event.getState() == Event.KeeperState.Disconnected) {
                    logger.warn("Disconnected from ZooKeeper");
                    synchronized (zooKeeper) {
                        zooKeeper.notifyAll();
                    }
                } else if (event.getState() == Event.KeeperState.Closed) {
                    logger.info("ZooKeeper session closed");
                }
                break;
            default:
                logger.debug("Unhandled event: {}", event);
        }
    }
}


