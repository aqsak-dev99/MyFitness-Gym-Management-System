package com.gymmanagement.model.membership;

public enum BootcampType {

    FAT_BURN              ("Fat Burn"),
    FITNESS_AND_ENDURANCE ("Fitness & Endurance"),
    FULL_BODY             ("Full Body");

    private final String displayName;

    BootcampType(String displayName) { this.displayName = displayName; }

    public String getDisplayName() { return displayName; }
}
