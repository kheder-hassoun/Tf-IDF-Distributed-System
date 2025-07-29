package me.zookeeper.leader_election.leader;

public interface OnElectionCallback {

    void onElectedToBeLeader();

    void onWorker();
}
