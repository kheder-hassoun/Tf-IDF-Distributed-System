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




//import org.apache.zookeeper.KeeperException;
//import org.apache.zookeeper.WatchedEvent;
//import org.apache.zookeeper.Watcher;
//import org.apache.zookeeper.ZooKeeper;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//
//@Component
//public class Application implements Watcher, CommandLineRunner {
//
//    private static final Logger logger = LoggerFactory.getLogger(Application.class);
//
//    private final ZooKeeper zooKeeper;
//    private final ServiceRegistry serviceRegistry;
//    private final LeaderElection leaderElection;
//
//    @Value("${server.port:8080}") // Default port is 8080 if not provided
//    private int serverPort;
//
//    @Autowired
//    public Application(ZooKeeper zooKeeper,
//                       ServiceRegistry serviceRegistry,
//                       @Value("${server.port:8080}") int serverPort,
//                       OnElectionAction onElectionAction) {
//        this.zooKeeper = zooKeeper;
//        this.serviceRegistry = serviceRegistry;
//
//
//        this.leaderElection = new LeaderElection(zooKeeper, onElectionAction);
//    }
//
//    @Override
//    public void run(String... args) throws Exception {
//        logger.info("Connected to ZooKeeper");
//
//        // Volunteer for leadership and reelect a leader
//        leaderElection.volunteerForLeadership();
//        leaderElection.reelectLeader();
//
//        // Run the application
//        runApplication();
//    }
//
//    private void runApplication() throws InterruptedException {
//        synchronized (zooKeeper) {
//            zooKeeper.wait(); // Keep the application running
//        }
//    }
//
//    @Override
//    public void process(WatchedEvent event) {
//        switch (event.getType()) {
//            case None:
//                if (event.getState() == Event.KeeperState.SyncConnected) {
//                    logger.info("Successfully connected to ZooKeeper");
//                } else if (event.getState() == Event.KeeperState.Disconnected) {
//                    logger.warn("Disconnected from ZooKeeper");
//                    synchronized (zooKeeper) {
//                        zooKeeper.notifyAll();
//                    }
//                } else if (event.getState() == Event.KeeperState.Closed) {
//                    logger.info("ZooKeeper session closed");
//                }
//                break;
//            default:
//                logger.debug("Unhandled event: {}", event);
//        }
//    }
//}



//package me.zookeeper.leader_election;
//
//import org.apache.zookeeper.KeeperException;
//import org.apache.zookeeper.WatchedEvent;
//import org.apache.zookeeper.Watcher;
//import org.apache.zookeeper.ZooKeeper;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.IOException;
//
//public class Application implements Watcher {
//
//    private static final Logger logger = LoggerFactory.getLogger(Application.class);
//
//    private static final String address = "172.29.3.101:2181";
//    private static final int SESSION_TIMEOUT = 3000; //dead client
//    private static final int DEFAULT_PORT = 8080;
//    private ZooKeeper zooKeeper;
//
//    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
//
//        int currentServerPort = args.length == 1 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
//        Application application = new Application();
//        ZooKeeper zooKeeper = application.connectToZookeeper();
//
//        logger.info("Connected");
//
//        ServiceRegistry serviceRegistry = new ServiceRegistry(zooKeeper);
//
//        OnElectionAction onElectionAction = new OnElectionAction(serviceRegistry, currentServerPort);
//
//        LeaderElection leaderElection = new LeaderElection(zooKeeper, onElectionAction);
//        leaderElection.volunteerForLeadership();
//        leaderElection.reelectLeader();
//
//        application.run();
//        application.close();
//
//
//    }
//
//    public ZooKeeper connectToZookeeper() throws IOException {
//        this.zooKeeper = new ZooKeeper(address, SESSION_TIMEOUT, this);
//        return zooKeeper;
//    }
//
//    public void run() throws InterruptedException {
//        synchronized (zooKeeper) {
//            zooKeeper.wait();
//        }
//    }
//
//    private void close() throws InterruptedException {
//        this.zooKeeper.close();
//    }
//
//    @Override
//    public void process(WatchedEvent watchedEvent) {
//        switch (watchedEvent.getType()) {
//            case None:
//                if (watchedEvent.getState() == Event.KeeperState.SyncConnected) {
////                    System.out.println("Successfully connected to Zookeeper");
//                    logger.debug("Successfully connected to Zookeeper");
//                } else if (watchedEvent.getState() == Event.KeeperState.Disconnected) {
//                    synchronized (zooKeeper) {
////                        System.out.println("Disconnected from Zookeeper");
//                        logger.debug("Disconnected from Zookeeper");
//                        zooKeeper.notifyAll();
//                    }
//                } else if (watchedEvent.getState() == Event.KeeperState.Closed) {
////                    System.out.println("Closed Successfully");
//                    logger.debug("Closed Successfully");
//                }
//                break;
//        }
//    }
//}
