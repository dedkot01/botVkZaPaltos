package org.dedkot.database;

import lombok.Getter;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
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
