package org.dedkot.database;

import lombok.Getter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Getter
public class Route extends UserDB {

    private Integer id;
    private String day;
    private String timeUn;
    private Integer countUn;
    private String timeCt;
    private Integer countCt;

    public Route (ResultSet rs) throws SQLException {
        super(rs.getInt("userId"));
        id = rs.getInt("id");
        day = rs.getString("day");
        timeUn = rs.getString("timeUn");
        countUn = rs.getInt("countUn");
        timeCt = rs.getString("timeCt");
        countCt = rs.getInt("countCt");
    }

    public String getRouteText() throws ParseException {
        StringBuilder strBuild = new StringBuilder();
        Date d = new SimpleDateFormat("d.M.yyyy").parse(day);
        strBuild.append(new SimpleDateFormat("EEEE (d MMM)").format(d));
        if (!timeUn.equals("Нет")) {
            strBuild.append("\nПГУ - ").append(timeUn).append(" - ").append(countUn);
            switch (Math.abs(countUn)) {
                case 1:
                    strBuild.append(" место");
                    break;
                case 2:
                case 3:
                case 4:
                    strBuild.append(" места");
                    break;
                default:
                    strBuild.append(" мест");
            }
        }
        if (!timeCt.equals("Нет")) {
            strBuild.append("\nЗр - ").append(timeCt).append(" - ").append(countCt);
            switch (Math.abs(countCt)) {
                case 1:
                    strBuild.append(" место");
                    break;
                case 2:
                case 3:
                case 4:
                    strBuild.append(" места");
                    break;
                default:
                    strBuild.append(" мест ");
            }
        }
        return strBuild.toString();
    }

    public String toString() {
        StringBuilder strBuild = new StringBuilder();
        strBuild.append(day).append(" ");
        if (!timeUn.equals("Нет")) {
            strBuild.append("ПГУ - ").append(timeUn).append(" - ").append(countUn);
            switch (Math.abs(countUn)) {
                case 1:
                    strBuild.append(" место ");
                    break;
                case 2:
                case 3:
                case 4:
                    strBuild.append(" места ");
                    break;
                default:
                    strBuild.append(" мест ");
            }
        }
        if (!timeCt.equals("Нет")) {
            strBuild.append("Зр - ").append(timeCt).append(" - ").append(countCt);
            switch (Math.abs(countCt)) {
                case 1:
                    strBuild.append(" место ");
                    break;
                case 2:
                case 3:
                case 4:
                    strBuild.append(" места ");
                    break;
                default:
                    strBuild.append(" мест ");
            }
        }
        return strBuild.toString();
    }

}
