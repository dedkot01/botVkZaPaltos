package org.dedkot.database;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DriverPage extends UserDB {
    private String nickname;
    private String indexCar;
    private String modelCar;
    private String description;

    public DriverPage(ResultSet rs) throws SQLException {
        super(rs.getInt("userId"));
        nickname = rs.getString("nickname");
        indexCar = rs.getString("indexCar");
        modelCar = rs.getString("modelCar");
        description = rs.getString("description");
    }

    public String getNickname() {
        return nickname;
    }

    public String getIndexCar() {
        return indexCar;
    }

    public String getModelCar() {
        return modelCar;
    }

    public String getDescription() {
        return description;
    }

    public String toString() {
        StringBuilder strBuild = new StringBuilder();
        strBuild.append("@id").append(getUserId());
        if (!nickname.equals("-")) {
            strBuild.append("(").append(nickname).append(")");
        }
        strBuild.append("\n");

        if (!indexCar.equals("-")) {
            strBuild.append(indexCar.toUpperCase()).append(" ");
        }

        if (!modelCar.equals("-")) {
            strBuild.append(modelCar);
        }
        strBuild.append("\n");

        if (!description.equals("-")) {
            strBuild.append(description).append("\n");
        }

        return strBuild.toString();
    }
}
