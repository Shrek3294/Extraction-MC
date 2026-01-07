package com.raidextraction.raid;

public enum RaidState {
    LOBBY,
    DEPLOYING,
    IN_RAID,
    EXTRACTING,
    ENDED;

    public boolean canTransitionTo(RaidState next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case LOBBY -> next == DEPLOYING || next == ENDED;
            case DEPLOYING -> next == IN_RAID || next == ENDED;
            case IN_RAID -> next == EXTRACTING || next == ENDED;
            case EXTRACTING -> next == ENDED;
            case ENDED -> false;
        };
    }
}
