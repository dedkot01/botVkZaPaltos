package database;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Context extends UserDB {

    public static final int START = 0;

    public static final int PAS_CHOICE_DAY = 100;
    public static final int PAS_CHOICE_TARGET = 101;
    public static final int PAS_CHOICE_TIME = 102;
    public static final int PAS_CHOICE_DRIVER = 103;

    public static final int DR_MENU = 150;

    public static final int DR_PAGE = 151;
    public static final int DR_PAGE_NICKNAME = 152;
    public static final int DR_PAGE_INDEX_CAR = 153;
    public static final int DR_PAGE_MODEL_CAR = 154;
    public static final int DR_PAGE_DESCRIPTION = 155;

    public static final int DR_ROUTE = 200;
    public static final int DR_ROUTE_NEW_DAY = 201;
    public static final int DR_ROUTE_NEW_TIME_UN = 202;
    public static final int DR_ROUTE_NEW_COUNT_UN = 203;
    public static final int DR_ROUTE_NEW_TIME_CT = 204;
    public static final int DR_ROUTE_NEW_COUNT_CT = 205;

    public static final int DR_ROUTE_CHANGE = 249;
    public static final int DR_ROUTE_CHANGE_MENU = 250;
    public static final int DR_ROUTE_CHANGE_DAY = 251;
    public static final int DR_ROUTE_CHANGE_TIME_UN = 252;
    public static final int DR_ROUTE_CHANGE_COUNT_UN = 253;
    public static final int DR_ROUTE_CHANGE_TIME_CT = 254;
    public static final int DR_ROUTE_CHANGE_COUNT_CT = 255;

    private Integer id;
    private String date;

    public Context(ResultSet rs) throws SQLException {
        super(rs.getInt("userId"));
        id = rs.getInt("contextId");
        date = rs.getString("date");
    }

    public Integer getId() {
        return id;
    }

    public String getDate() {
        return date;
    }
}
