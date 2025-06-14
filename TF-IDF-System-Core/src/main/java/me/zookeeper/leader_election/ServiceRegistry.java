package me.zookeeper.leader_election;

import jakarta.annotation.PostConstruct;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ServiceRegistry implements Watcher {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);

    private static final String REGISTRY_ZNODE = "/service_registry";
    private final ZooKeeper zooKeeper;

    private String currentZnode = null;
    private List<String> allServiceAddresses = null;
    @Autowired
    public ServiceRegistry(ZooKeeper zooKeeper) {
        this.zooKeeper = zooKeeper;
        createServiceRegistryZnode();
    }
    @PostConstruct
    public void init() {
        createServiceRegistryZnode();
    }

    private void createServiceRegistryZnode() {
        try {
            // Attempt to create the node directly
            zooKeeper.create(REGISTRY_ZNODE, new byte[]{},
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            logger.info("Created service registry znode: {}", REGISTRY_ZNODE);
        } catch (KeeperException.NodeExistsException e) {
            logger.debug("Service registry znode already exists: {}", REGISTRY_ZNODE);
        } catch (KeeperException | InterruptedException e) {
            logger.error("Critical error creating service registry znode", e);
            // Handle critical failure appropriately
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // Restore interrupt status
            }
            throw new RuntimeException("Failed to initialize service registry", e);
        }
    }


    public void registerToCluster(String metadata) throws KeeperException, InterruptedException {
        if (this.currentZnode != null) {
//            System.out.println("Already registered to service registry");
            logger.debug("Already registered to service registry");
            return;
        }
        this.currentZnode = zooKeeper.create(REGISTRY_ZNODE + "/n_", metadata.getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
//        System.out.println("Registered to service registry");
        logger.debug("Registered to service registry");
    }

    public void registerForUpdates() {
        try {
            updateAddresses();
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void unregisterFromCluster() {
        try {
            if (currentZnode != null && zooKeeper.exists(currentZnode, false) != null) {
                zooKeeper.delete(currentZnode, -1);
            }
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public List<String> getAllServiceAddresses() {
        return allServiceAddresses;
    }

    private synchronized void updateAddresses() throws KeeperException, InterruptedException {
        List<String> workerZnodes = zooKeeper.getChildren(REGISTRY_ZNODE, this);

        List<String> addresses = new ArrayList<>(workerZnodes.size());

        for (String workerZnode : workerZnodes) {
            String workerFullPath = REGISTRY_ZNODE + "/" + workerZnode;
            Stat stat = zooKeeper.exists(workerFullPath, false);
            if (stat == null) {
                continue;
            }

            byte[] addressBytes = zooKeeper.getData(workerFullPath, false, stat);
            String address = new String(addressBytes);
            addresses.add(address);
        }

        this.allServiceAddresses = Collections.unmodifiableList(addresses);
//        System.out.println("The cluster addresses are: " + this.allServiceAddresses);
        logger.info("The cluster addresses are: {}",this.allServiceAddresses);
    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        try {
            updateAddresses();
        } catch (KeeperException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
