package database;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DriverQuery extends UserDB {
    private String day;
    private String timeUn;
    private Integer countUn;
    private String timeCt;
    private Integer countCt;
    private Integer cursor;

    public DriverQuery (ResultSet rs) throws SQLException {
        super(rs.getInt("userId"));
        day = rs.getString("day");
        timeUn = rs.getString("timeUn");
        countUn = rs.getInt("countUn");
        timeCt = rs.getString("timeCt");
        countCt = rs.getInt("countCt");
        cursor = rs.getInt("cursor");
    }

    public String getDay() {
        return day;
    }

    public String getTimeUn() {
        return timeUn;
    }

    public Integer getCountUn() {
        return countUn;
    }

    public String getTimeCt() {
        return timeCt;
    }

    public Integer getCountCt() {
        return countCt;
    }

    public Integer getCursor() {
        return cursor;
    }

}
