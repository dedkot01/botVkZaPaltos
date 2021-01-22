package org.dedkot.database;

public abstract class UserDB {
    private Integer userId;

    public UserDB(Integer id) {
        userId = id;
    }

    public Integer getUserId() {
        return userId;
    }
}
