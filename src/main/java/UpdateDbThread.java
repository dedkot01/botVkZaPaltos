import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import database.Day;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UpdateDbThread extends Thread {

    private VkApiClient vk;
    private GroupActor groupActor;

    public static Connection connDb;
    public static Statement statmt;
    public static ResultSet resSet;

    public UpdateDbThread(VkApiClient client, GroupActor actor) throws ClassNotFoundException, SQLException {
        vk = client;
        groupActor = actor;

        connDb = null;
        Class.forName("org.sqlite.JDBC");
        connDb = DriverManager.getConnection("jdbc:sqlite:database.db");
        statmt = connDb.createStatement();
    }

    public void run() {
        try {
            resSet = statmt.executeQuery("SELECT id, day FROM route");
            while (resSet.next()) {
                Date today = new SimpleDateFormat("d.M.yyyy").parse(Day.getDay());
                Date route = new SimpleDateFormat("d.M.yyyy").parse(resSet.getString("day"));
                if (today.after(route)) {
                    statmt.executeUpdate("DELETE FROM route WHERE id = " + resSet.getString("id") + ";");
                }
            }
        } catch (Exception e) {
            System.out.println("Проблема в нитке обновления\n" + e.getMessage());
        }
        try {
            sleep(3600000);
        } catch (InterruptedException e) {
            System.out.println("Проблема в нитке обновления со сном\n" + e.getMessage());
        }
    }
}
