package me.zookeeper.leader_election;

public interface OnElectionCallback {

    void onElectedToBeLeader();

    void onWorker();
}
