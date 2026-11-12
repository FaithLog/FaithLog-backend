package com.faithlog.missions;

public enum PeriodMission {
 WEEKLY_SUBMISSION(100), WEEKLY_VOTE(50), WEEKLY_NO_PENALTY(50),
 MONTHLY_NO_PENALTY(200), MONTHLY_ALL_SUBMISSIONS(300);
 private final long points;
 PeriodMission(long points) {this.points=points;}
 public long points() {return points;}
 public boolean monthly() {return this==MONTHLY_NO_PENALTY||this==MONTHLY_ALL_SUBMISSIONS;}
 public boolean noPenalty() {return this==WEEKLY_NO_PENALTY||this==MONTHLY_NO_PENALTY;}
 public boolean fullMembershipRequired() {return monthly()||this==WEEKLY_NO_PENALTY;}
}
