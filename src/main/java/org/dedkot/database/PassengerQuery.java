package org.dedkot.database;

import lombok.Getter;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
public class PassengerQuery extends UserDB {

    private String day;
    private String target;
    private String time;
    private Integer cursor;

    public PassengerQuery(ResultSet resSet) throws SQLException {
        super(resSet.getInt("userId"));
        day = resSet.getString("day");
        target = resSet.getString("target");
        time = resSet.getString("time");
        cursor = resSet.getInt("cursor");
    }

    public String toString() {
        return ("@id" + getUserId() + " " + day + " " + target + " " + time);
    }

}
