import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import database.Day;
import database.PassengerQuery;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

public class UpdateDbThread extends Thread {

    public static final String PAS_NOTIFICATION_MESSAGE = "По вашей подписке появились новые записи";

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
        while(true) {
            try {
                sleep(30000);
            } catch (InterruptedException e) {
                System.out.println("Проблема в нитке обновления со сном\n" + e.getMessage());
            }
            try {
                // УДАЛЕНИЕ СТАРЫХ ЗАПИСЕЙ
                resSet = statmt.executeQuery("SELECT id, day FROM route");
                while (resSet.next()) {
                    Date today = new SimpleDateFormat("d.M.yyyy").parse(Day.getDay());
                    Date route = new SimpleDateFormat("d.M.yyyy").parse(resSet.getString("day"));
                    if (today.after(route)) {
                        statmt.executeUpdate("DELETE FROM route WHERE id = " + resSet.getString("id") + ";");
                    }
                }
                // ОПОВЕЩЕНИЕ ПОДПИСЧИКОВ
                LinkedList<PassengerQuery> listPas = new LinkedList<>();
                resSet = statmt.executeQuery("SELECT * FROM passengerQuery WHERE subscribe = 1;");
                while (resSet.next()) {
                    listPas.add(new PassengerQuery(resSet));
                }
                for (PassengerQuery pq : listPas) {
                    resSet = statmt.executeQuery("SELECT count(*) FROM route WHERE day = '" + pq.getDay() + "' " +
                            "AND (timeUn = '" + pq.getTime() + "' OR timeCt = '" + pq.getTime() + "');");
                    if (resSet.getInt(1) != 0) {
                        statmt.executeUpdate("UPDATE passengerQuery SET subscribe = 0 WHERE userId = " + pq.getUserId() + ";");
                        vk.messages().send(groupActor)
                                .message(PAS_NOTIFICATION_MESSAGE)
                                .randomId(0).peerId(pq.getUserId()).execute();
                    }
                }
            } catch (Exception e) {
                System.out.println("Проблема в нитке обновления\n" + e.getMessage());
            }
        }
    }
}
