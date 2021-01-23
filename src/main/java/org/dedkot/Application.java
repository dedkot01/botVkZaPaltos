package org.dedkot;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import org.dedkot.database.Day;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.Semaphore;

import static java.lang.Thread.sleep;

public class Application {

    private static final String PROPERTIES_FILE = "config.properties";

    public static Connection connDb;
    public static Statement statmt;
    public static Semaphore sem;

    public static void main(String[] args) throws IOException {
        System.out.println(new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss | ").format(new Date()) + "I'm BOTMAN!");
        Properties properties = readProperties();
        GroupActor groupActor = createGroupActor(properties);
        setDayPatternsDate();
        connectDb();
        sem = new Semaphore(1);

        HttpTransportClient httpClient = HttpTransportClient.getInstance();

        while(true) {
            try {
                VkApiClient vk = new VkApiClient(httpClient);

                UpdateDbThread updateDbThread = UpdateDbThread.getInstance(vk, groupActor, statmt, sem);
                updateDbThread.setSemaphore(sem);
                updateDbThread.setVkClient(vk);
                if (!updateDbThread.isAlive())
                    updateDbThread.start();
                System.out.println("Initialization/check UpdateThread complete!");

                CallbackApiLongPollHandler handler = new CallbackApiLongPollHandler(vk, groupActor, statmt, sem);
                System.out.println("All Initialization complete! Run.");
                handler.run();
            }
            catch (Exception e) {
                System.out.println(new SimpleDateFormat("d.M.yyyy - H:m:s | ").format(new Date()) + "ANOMALY | " + e.getMessage());
                System.out.println("Restart after 1 minute");
            }
            try {
                sleep(60000);
            }
            catch (InterruptedException e) {
                System.out.println(new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss | ").format(new Date()) + "I can not fall asleep");
            }
        }
    }

    private static GroupActor createGroupActor(Properties properties) {
        String groupId = properties.getProperty("groupId");
        String accessToken = properties.getProperty("token");
        return new GroupActor(Integer.parseInt(groupId), accessToken);
    }

    private static Properties readProperties() throws IOException {
        InputStream inputStream = Application.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE);
        if (inputStream == null) {
            throw new FileNotFoundException("property file '" + PROPERTIES_FILE + "' not found in the classpath");
        }
        try {
            Properties properties = new Properties();
            properties.load(inputStream);
            properties.setProperty("groupId", Crypt.simpleCryptId(properties.getProperty("groupId")));
            return properties;
        } catch (IOException e) {
            throw new RuntimeException("Incorrect properties file");
        } finally {
            inputStream.close();
        }
    }

    private static void setDayPatternsDate() {
        Day.addKnownPatternsDate(new SimpleDateFormat("d MMM yyyy"));
        Day.addKnownPatternsDate(new SimpleDateFormat("d MM yyyy"));
        Day.addKnownPatternsDate(new SimpleDateFormat("d M yyyy"));
        Day.addKnownPatternsDate(new SimpleDateFormat("d.M yyyy"));
    }

    private static void connectDb() {
        try {
            connDb = null;
            Class.forName("org.sqlite.JDBC");
            connDb = DriverManager.getConnection("jdbc:sqlite:org.dedkot.database.db");
            statmt = connDb.createStatement();
            try {
                statmt.executeUpdate("CREATE TABLE context (userId INTEGER PRIMARY KEY, " +
                        "contextId INTEGER, " +
                        "date TEXT);");
                System.out.println("Table context create.");
            }
            catch (SQLException e) {
                if (e.getErrorCode() == 1)
                    System.out.println("Table context read.");
                else
                    System.out.println(e.getMessage());
            }
            try {
                statmt.executeUpdate("CREATE TABLE passengerQuery (userId INTEGER PRIMARY KEY, " +
                        "day TEXT, " +
                        "target TEXT, " +
                        "time TEXT, " +
                        "cursor INTEGER, " +
                        "subscribe INTEGER);");
                System.out.println("Table passengerQuery create.");
            }
            catch (SQLException e) {
                if (e.getErrorCode() == 1)
                    System.out.println("Table passengerQuery read.");
                else
                    System.out.println(e.getMessage());
            }
            try {
                statmt.executeUpdate("CREATE TABLE driverQuery (userId INTEGER PRIMARY KEY, " +
                        "day TEXT, " +
                        "timeUn TEXT, " +
                        "countUn INTEGER, " +
                        "timeCt TEXT, " +
                        "countCt INTEGER, " +
                        "cursor INTEGER);");
                System.out.println("Table driverQuery create.");
            }
            catch (SQLException e) {
                if (e.getErrorCode() == 1)
                    System.out.println("Table driverQuery read.");
                else
                    System.out.println(e.getMessage());
            }
            try {
                statmt.executeUpdate("CREATE TABLE driverPage (userId INTEGER PRIMARY KEY, " +
                        "nickname TEXT, " +
                        "indexCar TEXT, " +
                        "modelCar TEXT, " +
                        "description TEXT);");
                System.out.println("Table driverPage create.");
            }
            catch (SQLException e) {
                if (e.getErrorCode() == 1)
                    System.out.println("Table driverPage read.");
                else
                    System.out.println(e.getMessage());
            }
            try {
                statmt.executeUpdate("CREATE TABLE route (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "userId INTEGER, " +
                        "day TEXT, " +
                        "timeUn TEXT, " +
                        "countUn INTEGER, " +
                        "timeCt TEXT, " +
                        "countCt INTEGER);");
                System.out.println("Table route create.");
            }
            catch (SQLException e) {
                if (e.getErrorCode() == 1)
                    System.out.println("Table route read.");
                else
                    System.out.println(e.getMessage());
            }
            System.out.println("Initialization org.dedkot.database complete!");
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}