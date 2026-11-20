ALTER TABLE mission_claims DROP CONSTRAINT mission_claims_mission_code_check;
ALTER TABLE mission_claims ADD CONSTRAINT mission_claims_mission_code_check
 CHECK (mission_code IN ('WEEKLY_SUBMISSION','WEEKLY_VOTE','WEEKLY_NO_PENALTY','WEEKLY_PRAYER_TOPIC','MONTHLY_NO_PENALTY','MONTHLY_ALL_SUBMISSIONS'));
