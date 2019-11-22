package database;

import java.sql.ResultSet;
import java.sql.SQLException;

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

    public String getDay() {
        return day;
    }

    public String getTarget() {
        return target;
    }

    public String getTime() {
        return time;
    }

    public Integer getCursor() {
        return cursor;
    }

    public String toString() {
        return ("@id" + getUserId() + " " + day + " " + target + " " + time);
    }
}
