package org.dedkot;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import org.dedkot.database.Day;
import org.dedkot.database.PassengerQuery;

import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

public class UpdateDbThread extends Thread {

    private static UpdateDbThread instance;

    private static final String PAS_NOTIFICATION_MESSAGE = "По вашей подписке появились новые записи";

    private VkApiClient vk;
    private GroupActor groupActor;
    private Statement statmt;
    private Semaphore sem;

    private UpdateDbThread(VkApiClient client, GroupActor actor, Statement stat, Semaphore semaphore) {
        vk = client;
        groupActor = actor;
        statmt = stat;
        sem = semaphore;
        setName("UpdateThread");
    }

    public void run() {
        while(true) {
            try {
                sem.acquire();
                try {
                    // УДАЛЕНИЕ СТАРЫХ ЗАПИСЕЙ
                    ResultSet resSet = statmt.executeQuery("SELECT id, day FROM route;");
                    while (resSet.next()) {
                        Date today = new SimpleDateFormat("d.M.yyyy").parse(Day.getDay());
                        Date route = new SimpleDateFormat("d.M.yyyy").parse(resSet.getString("day"));
                        if (today.after(route)) {
                            statmt.executeUpdate("DELETE FROM route WHERE id = " + resSet.getString("id") + ";");
                            resSet = statmt.executeQuery("SELECT id, day FROM route;");
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
                }
                catch (Exception e) {
                    System.out.println(new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss | ").format(new Date()) + "Проблема в нитке обновления" + e.getMessage());
                }
                sem.release();
                System.out.println(new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss | ").format(new Date()) + "UpdateDb success");
                sleep(3600000);
            }
            catch (InterruptedException e) {
                System.out.println(new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss | ").format(new Date()) + "UpdateThread can not fall asleep");
            }
        }
    }

    public void setVkClient(VkApiClient client) {
        vk = client;
    }

    public void setSemaphore(Semaphore semaphore) {
        sem = semaphore;
    }

    public static UpdateDbThread getInstance(VkApiClient client, GroupActor actor, Statement stat, Semaphore semaphore) {
        if (instance == null)
            instance = new UpdateDbThread(client, actor, stat, semaphore);
        return instance;
    }

}
