package org.dedkot.database;

import lombok.Getter;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
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

}
