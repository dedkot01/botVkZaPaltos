import com.vk.api.sdk.callback.longpoll.CallbackApiLongPoll;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.*;
import database.*;
import org.apache.commons.lang3.math.NumberUtils;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallbackApiLongPollHandler extends CallbackApiLongPoll {

    private static final int LIMIT_ROUTE = 6;

    private static final String START_MESSAGE = "Выберите режим:";
    private static final String WARNING_MESSAGE = "Не то что ожидалось";
    private static final String ERROR_MESSAGE = "ОШИБКА! Что-то пошло не так т_т\n" +
            "Пожалуйста, сделайте скриншот переписки с ботом, которые привели к этой ошибке и само это сообщение.\n" +
            "Оповестив администрацию об ошибке, вы поможете её устранить)\nError: ";
    private static final String OLD_CONTEXT_MESSAGE = "Ваша сессия устарела, возвращение в меню";

    private static final String CHOICE_DAY_MESSAGE = "Выберите день:";
    private static final String CHOICE_TARGET_MESSAGE = "Куда едем?";
    private static final String CHOICE_UN_TIME_MESSAGE = "К какой паре едем?";
    private static final String CHOICE_CT_TIME_MESSAGE = "После какой пары едем?";
    private static final String DRIVER_NOT_FOUND_MESSAGE = "Нет водителей, возвращение в меню";

    private static final String ROUTE_MESSAGE = "Хотите создать маршрут или изменить существующий?";
    private static final String CHOICE_ROUTE_TO_CHANGE_MESSAGE = "Какой маршрут хотите изменить?";
    private static final String ROUTE_NOT_CREATE_MESSAGE = "У вас нет созданных маршрутов";
    private static final String THIS_ROUTE_NOT_FOUND_MESSAGE = "У вас нет такого маршрута";
    private static final String CHOICE_PARAM_ROUTE_TO_CHANGE_MESSAGE = "Что хотите изменить?";
    private static final String ROUTE_CHANGE_MESSAGE = "Маршрут изменён!\nЧто-нибудь ещё?";
    private static final String DELETE_ROUTE_MESSAGE = "Маршрут успешно удалён! Возвращение в меню";
    private static final String ROUTE_EXCEEDED_MESSAGE = "Лимитр маршрутов превышен!\nУдалите или измените текущие";
    private static final String CHOICE_COUNT_UN_MESSAGE = "Выберите кол-во мест в ПГУ";
    private static final String CHOICE_COUNT_CT_MESSAGE = "Выберите кол-во мест в Зр";
    private static final String SUCCESS_CREATE_ROUTE_MESSAGE = "Маршрут создан";
    private static final String WARNING_CREATE_ROUTE_MESSAGE = "Маршрут должен иметь хотя бы одно направление!";

    public static Connection connDb;
    public static Statement statmt;
    public static ResultSet resSet;

    private GroupActor groupActor;

    // Клавиатуры
    private Keyboard startKeyboard;
    private Keyboard driverKeyboard;
    private Keyboard choiceDriverQueryKeyboard;
    private Keyboard changeQueryDriverKeyboard;
    private Keyboard dayKeyboard;
    private Keyboard targetKeyboard;
    private Keyboard timeUnPasKeyboard;
    private Keyboard timeCtPasKeyboard;
    private Keyboard choicePasKeyboard;
    private Keyboard timeUnDrKeyboard;
    private Keyboard timeCtDrKeyboard;
    private Keyboard countKeyboard;

    public CallbackApiLongPollHandler(VkApiClient client, GroupActor actor) {
        super(client, actor);
        groupActor = actor;

        Day.addKnownPatternsDate(new SimpleDateFormat("d MMM yyyy"));
        Day.addKnownPatternsDate(new SimpleDateFormat("d M yyyy"));
        Day.addKnownPatternsDate(new SimpleDateFormat("d.M yyyy"));

        buildStartKeyboard();
        buildDriverKeyboard();
        buildChoiceDriverQueryKeyboard();
        buildDayKeyboard();
        buildTargetKeyboard();
        buildTimeUnKeyboard();
        buildTimeCtKeyboard();
        buildChoiceKeyboard();
        buildTimeUnDrKeyboard();
        buildTimeCtDrKeyboard();
        buildCountKeyboard();
        buildChangeDriverKeyboard();
        try {
            connDb = null;
            Class.forName("org.sqlite.JDBC");
            connDb = DriverManager.getConnection("jdbc:sqlite:database.db");
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
                        "cursor INTEGER);");
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
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void messageNew(Integer groupId, Message message) {
        try {
            resSet = statmt.executeQuery("SELECT * FROM context WHERE userId = '" + message.getFromId() + "';");
            // Если данный пользователь уже есть в таблице КОНТЕКСТ
            if (resSet.next()) {
                if (message.getText().equals("0") ||
                        message.getText().toLowerCase().equals("начало")) {
                    setContext(message.getFromId(), Context.START);
                    getClient().messages().send(groupActor)
                            .message(START_MESSAGE)
                            .keyboard(startKeyboard)
                            .randomId(0).peerId(message.getFromId()).execute();
                    return;
                }
                Context c = new Context(resSet);
                if (c.getDate().equals(Day.getDay()) || c.getId() == 0) {
                    switch (c.getId()) {
                        // СТАРТ
                        case Context.START:
                            switch (message.getText().toLowerCase()) {
                                case "водитель":
                                    setContext(message.getFromId(), Context.DR_ROUTE);
                                    getClient().messages().send(groupActor)
                                            .message(ROUTE_MESSAGE)
                                            .keyboard(driverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "пассажир":
                                    updateDayKeyboard();
                                    setContext(message.getFromId(), Context.PAS_CHOICE_DAY);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_DAY_MESSAGE)
                                            .keyboard(dayKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + START_MESSAGE)
                                            .keyboard(startKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ПАССАЖИР - ВЫБОР ДНЯ
                        case Context.PAS_CHOICE_DAY:
                            switch (message.getText().toLowerCase()) {
                                case "сегодня":
                                    statmt.executeUpdate("UPDATE passengerQuery SET day = '" + Day.getDay() + "' WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.PAS_CHOICE_TARGET);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_TARGET_MESSAGE)
                                            .keyboard(targetKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "завтра":
                                    statmt.executeUpdate("UPDATE passengerQuery SET day = '" + Day.getNextDay() + "' WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.PAS_CHOICE_TARGET);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_TARGET_MESSAGE)
                                            .keyboard(targetKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    Matcher m = Pattern.compile("([0-9]+{1,2} [а-я]+[.]|[0-9]+{1,2}.[0-9]+{1,2}|[0-9]+{1,2} [0-9]+{1,2})").matcher(message.getText());
                                    if (m.find()) {
                                        statmt.executeUpdate("UPDATE passengerQuery SET day = '" + Day.getDay(m.group()) + "' WHERE userId = '" + message.getFromId() + "';");
                                        setContext(message.getFromId(), Context.PAS_CHOICE_TARGET);
                                        getClient().messages().send(groupActor)
                                                .message(CHOICE_TARGET_MESSAGE)
                                                .keyboard(targetKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                                    else {
                                        updateDayKeyboard();
                                        getClient().messages().send(groupActor)
                                                .message(WARNING_MESSAGE + "\n" + CHOICE_DAY_MESSAGE)
                                                .keyboard(dayKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                            }
                            break;
                        // ПАССАЖИР - ВЫБОР МЕСТА
                        case Context.PAS_CHOICE_TARGET:
                            switch (message.getText().toLowerCase()) {
                                case "пгу":
                                    statmt.executeUpdate("UPDATE passengerQuery SET target = '" + message.getText() + "' WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.PAS_CHOICE_TIME);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_UN_TIME_MESSAGE)
                                            .keyboard(timeUnPasKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "зр":
                                    statmt.executeUpdate("UPDATE passengerQuery SET target = '" + message.getText() + "' WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.PAS_CHOICE_TIME);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_CT_TIME_MESSAGE)
                                            .keyboard(timeCtPasKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + CHOICE_TARGET_MESSAGE)
                                            .keyboard(targetKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ПАССАЖИР - ВЫБОР ВРЕМЕНИ
                        case Context.PAS_CHOICE_TIME:
                            if (message.getText().equals("8:00") || message.getText().equals("9:15")
                                    || message.getText().equals("9:50") || message.getText().equals("11:25")
                                    || message.getText().equals("11:40") || message.getText().equals("13:15")
                                    || message.getText().equals("13:45") || message.getText().equals("15:20")
                                    || message.getText().equals("15:35") || message.getText().equals("17:10")
                                    || message.getText().equals("17:25") || message.getText().equals("19:00")) {
                                statmt.executeUpdate("UPDATE passengerQuery SET time = '" + message.getText() + "', cursor = 0 WHERE userId = '" + message.getFromId() + "';");
                                setContext(message.getFromId(), Context.PAS_CHOICE_DRIVER);
                            }
                            else {
                                resSet = statmt.executeQuery("SELECT target FROM passengerQuery WHERE userId = '" + message.getFromId() + "';");
                                resSet.next();
                                if (resSet.getString("target").equals("ПГУ")) {
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + CHOICE_UN_TIME_MESSAGE)
                                            .keyboard(timeUnPasKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                }
                                else {
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + CHOICE_CT_TIME_MESSAGE)
                                            .keyboard(timeCtPasKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                }
                                break;
                            }
                        // ПАССАЖИР - ВЫБОР ВОДИТЕЛЯ
                        case Context.PAS_CHOICE_DRIVER:
                            resSet = statmt.executeQuery("SELECT * FROM passengerQuery WHERE userId = " + message.getFromId() + ";");
                            resSet.next();
                            PassengerQuery pq = new PassengerQuery(resSet);
                            if (pq.getTarget().equals("ПГУ"))
                                resSet = statmt.executeQuery("SELECT * FROM route WHERE id > " + pq.getCursor() +
                                        " AND day = '" + pq.getDay() + "' AND timeUn = '" + pq.getTime() + "';");
                            else
                                resSet = statmt.executeQuery("SELECT * FROM route WHERE id > " + pq.getCursor() +
                                        " AND day = '" + pq.getDay() + "' AND timeCt = '" + pq.getTime() + "';");

                            if (resSet.next()) {
                                Route r = new Route(resSet);
                                statmt.executeUpdate("UPDATE passengerQuery SET cursor = " + r.getId() + " WHERE userId = " + message.getFromId() + ";");
                                getClient().messages().send(groupActor)
                                        .message(r.getRouteText())
                                        .keyboard(choicePasKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            else {
                                setContext(message.getFromId(), Context.START);
                                getClient().messages().send(groupActor)
                                        .message(DRIVER_NOT_FOUND_MESSAGE)
                                        .keyboard(startKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - ВЫБОР РЕЖИМА
                        case Context.DR_ROUTE:
                            switch (message.getText().toLowerCase()) {
                                case "изменить":
                                    resSet = statmt.executeQuery("SELECT * FROM route WHERE userId = " + message.getFromId() + ";");
                                    int i = 0;
                                    StringBuilder messageText = new StringBuilder();
                                    while (resSet.next()) {
                                        i++;
                                        Route r = new Route(resSet);
                                        messageText.append(i).append(") ").append(r.toString()).append("\n");
                                    }
                                    if(i != 0) {
                                        setContext(message.getFromId(), Context.DR_ROUTE_CHANGE);
                                        getClient().messages().send(groupActor)
                                                .message(messageText.toString() + CHOICE_ROUTE_TO_CHANGE_MESSAGE)
                                                .keyboard(choiceDriverQueryKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                                    else {
                                        getClient().messages().send(groupActor)
                                                .message(ROUTE_NOT_CREATE_MESSAGE)
                                                .keyboard(driverKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                                    break;
                                case "новый":
                                    resSet = statmt.executeQuery("SELECT count(*) FROM route WHERE userId = " + message.getFromId() + ";");
                                    resSet.next();
                                    if (resSet.getInt(1) < LIMIT_ROUTE) {
                                        updateDayKeyboard();
                                        setContext(message.getFromId(), Context.DR_ROUTE_NEW_DAY);
                                        getClient().messages().send(groupActor)
                                                .message(CHOICE_DAY_MESSAGE)
                                                .keyboard(dayKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                                    else {
                                        getClient().messages().send(groupActor)
                                                .message(ROUTE_EXCEEDED_MESSAGE)
                                                .keyboard(driverKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + ROUTE_MESSAGE)
                                            .keyboard(driverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - СОЗДАТЬ - ВЫБОР ДНЯ
                        case Context.DR_ROUTE_NEW_DAY:
                            switch (message.getText().toLowerCase()) {
                                case "сегодня":
                                    statmt.executeUpdate("UPDATE driverQuery SET day = '" + Day.getDay() + "' WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.DR_ROUTE_NEW_TIME_UN);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_UN_TIME_MESSAGE)
                                            .keyboard(timeUnDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "завтра":
                                    statmt.executeUpdate("UPDATE driverQuery SET day = '" + Day.getNextDay() + "' WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.DR_ROUTE_NEW_TIME_UN);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_UN_TIME_MESSAGE)
                                            .keyboard(timeUnDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    Matcher m = Pattern.compile("([0-9]+{1,2} [а-я]+[.]|[0-9]+{1,2}.[0-9]+{1,2}|[0-9]+{1,2} [0-9]+{1,2})").matcher(message.getText());
                                    if (m.find()) {
                                        statmt.executeUpdate("UPDATE driverQuery SET day = '" + Day.getDay(m.group()) + "' WHERE userId = '" + message.getFromId() + "';");
                                        setContext(message.getFromId(), Context.DR_ROUTE_NEW_TIME_UN);
                                        getClient().messages().send(groupActor)
                                                .message(CHOICE_UN_TIME_MESSAGE)
                                                .keyboard(timeUnDrKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                                    else {
                                        updateDayKeyboard();
                                        getClient().messages().send(groupActor)
                                                .message(WARNING_MESSAGE + "\n" + CHOICE_DAY_MESSAGE)
                                                .keyboard(dayKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                            }
                            break;
                        // ВОДИТЕЛЬ - ВЫБОР ПАРЫ В УНИВЕР
                        case Context.DR_ROUTE_NEW_TIME_UN:
                            if (message.getText().equals("8:00")
                                    || message.getText().equals("9:50")
                                    || message.getText().equals("11:40")
                                    || message.getText().equals("13:45")
                                    || message.getText().equals("15:35")
                                    || message.getText().equals("17:25")) {
                                statmt.executeUpdate("UPDATE driverQuery SET timeUn = '" + message.getText() + "' WHERE userId = '" + message.getFromId() + "';");
                                setContext(message.getFromId(), Context.DR_ROUTE_NEW_COUNT_UN);
                                getClient().messages().send(groupActor)
                                        .message(CHOICE_COUNT_UN_MESSAGE)
                                        .keyboard(countKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            else if (message.getText().toLowerCase().equals("нет")) {
                                statmt.executeUpdate("UPDATE driverQuery SET timeUn = '" + message.getText() + "', " +
                                        "countUn = 0 WHERE userId = '" + message.getFromId() + "';");
                                setContext(message.getFromId(), Context.DR_ROUTE_NEW_TIME_CT);
                                getClient().messages().send(groupActor)
                                        .message(CHOICE_CT_TIME_MESSAGE)
                                        .keyboard(timeCtDrKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + CHOICE_UN_TIME_MESSAGE)
                                        .keyboard(timeCtPasKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - ВЫБОР КОЛ-ВА МЕСТ В УНИВЕР
                        case Context.DR_ROUTE_NEW_COUNT_UN:
                            if (NumberUtils.isCreatable(message.getText())) {
                                statmt.executeUpdate("UPDATE driverQuery SET countUn = " + message.getText() + " WHERE userId = '" + message.getFromId() + "';");
                                setContext(message.getFromId(), Context.DR_ROUTE_NEW_TIME_CT);
                                getClient().messages().send(groupActor)
                                        .message(CHOICE_CT_TIME_MESSAGE)
                                        .keyboard(timeCtDrKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + CHOICE_COUNT_UN_MESSAGE)
                                        .keyboard(countKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - ВЫБОР ВРЕМЕНИ В ГОРОД
                        case Context.DR_ROUTE_NEW_TIME_CT:
                            if (message.getText().equals("9:35")
                                    || message.getText().equals("11:25")
                                    || message.getText().equals("13:15")
                                    || message.getText().equals("15:20")
                                    || message.getText().equals("17:10")
                                    || message.getText().equals("19:00")) {
                                statmt.executeUpdate("UPDATE driverQuery SET timeCt = '" + message.getText() + "' WHERE userId = '" + message.getFromId() + "';");
                                setContext(message.getFromId(), Context.DR_ROUTE_NEW_COUNT_CT);
                                getClient().messages().send(groupActor)
                                        .message(CHOICE_COUNT_CT_MESSAGE)
                                        .keyboard(countKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            else if (message.getText().toLowerCase().equals("нет")) {
                                statmt.executeUpdate("UPDATE driverQuery SET timeCt = '" + message.getText() + "', " +
                                        "countCt = 0 WHERE userId = '" + message.getFromId() + "';");
                                setContext(message.getFromId(), Context.START);

                                resSet = statmt.executeQuery("SELECT * FROM driverQuery WHERE userId = " + message.getFromId());
                                resSet.next();
                                if (resSet.getString("timeUn").toLowerCase().equals("нет")
                                        && resSet.getString("timeCt").toLowerCase().equals("нет")) {
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_CREATE_ROUTE_MESSAGE + "\n" + START_MESSAGE)
                                            .keyboard(startKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                }
                                else {
                                    DriverQuery dq = new DriverQuery(resSet);
                                    statmt.execute("INSERT INTO route (userId, day, timeUn, countUn, timeCt, countCt) " +
                                            "VALUES ('" + message.getFromId() + "', '" + dq.getDay() + "', '" + dq.getTimeUn() + "', " +
                                            dq.getCountUn() + ", '" + dq.getTimeCt() + "', " + dq.getCountCt() + ");");
                                    setContext(message.getFromId(), Context.START);
                                    getClient().messages().send(groupActor)
                                            .message(SUCCESS_CREATE_ROUTE_MESSAGE + "\n" + START_MESSAGE)
                                            .keyboard(startKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                }
                            }
                            else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + CHOICE_CT_TIME_MESSAGE)
                                        .keyboard(timeCtPasKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - ВЫБОР КОЛ-ВА МЕСТ В ЗР
                        case Context.DR_ROUTE_NEW_COUNT_CT:
                            if (NumberUtils.isCreatable(message.getText())) {
                                statmt.executeUpdate("UPDATE driverQuery SET countCt = " + message.getText() + " WHERE userId = '" + message.getFromId() + "';");
                                resSet = statmt.executeQuery("SELECT * FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                resSet.next();
                                DriverQuery dq = new DriverQuery(resSet);
                                statmt.execute("INSERT INTO route (userId, day, timeUn, countUn, timeCt, countCt) " +
                                        "VALUES ('" + message.getFromId() + "', '" + dq.getDay() + "', '" + dq.getTimeUn() + "', " +
                                        dq.getCountUn() + ", '" + dq.getTimeCt() + "', " + dq.getCountCt() + ");");
                                setContext(message.getFromId(), Context.START);
                                getClient().messages().send(groupActor)
                                        .message(SUCCESS_CREATE_ROUTE_MESSAGE + "\n" + START_MESSAGE)
                                        .keyboard(startKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + CHOICE_COUNT_CT_MESSAGE)
                                        .keyboard(countKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - ИЗМЕНИТЬ - ВЫБОР МАРШРУТА
                        case Context.DR_ROUTE_CHANGE:
                            if (NumberUtils.isCreatable(message.getText()) && Integer.parseInt(message.getText()) <= LIMIT_ROUTE && Integer.parseInt(message.getText()) > 0) {
                                resSet = statmt.executeQuery("SELECT id FROM route WHERE userId = " + message.getFromId() + ";");
                                boolean isHave = true;
                                for (int i = 0; i < Integer.parseInt(message.getText()); i++) {
                                    if (!resSet.next()) {
                                        getClient().messages().send(groupActor)
                                                .message(THIS_ROUTE_NOT_FOUND_MESSAGE + "\n" + CHOICE_ROUTE_TO_CHANGE_MESSAGE)
                                                .keyboard(choiceDriverQueryKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                        isHave = false;
                                        break;
                                    }
                                }
                                if (isHave) {
                                    int id = resSet.getInt("id");
                                    statmt.executeUpdate("UPDATE driverQuery SET cursor = " + id + " WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_PARAM_ROUTE_TO_CHANGE_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                }
                            }
                            else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + CHOICE_ROUTE_TO_CHANGE_MESSAGE)
                                        .keyboard(choiceDriverQueryKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - ИЗМЕНИТЬ МАРШРУТ
                        case Context.DR_ROUTE_CHANGE_MENU:
                            switch (message.getText().toLowerCase()) {
                                case "день":
                                    updateDayKeyboard();
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_DAY);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_DAY_MESSAGE)
                                            .keyboard(dayKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "время пгу":
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_TIME_UN);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_UN_TIME_MESSAGE)
                                            .keyboard(timeUnDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "места пгу":
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_COUNT_UN);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_COUNT_UN_MESSAGE)
                                            .keyboard(countKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "время зр":
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_TIME_CT);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_CT_TIME_MESSAGE)
                                            .keyboard(timeCtDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "места зр":
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_COUNT_CT);
                                    getClient().messages().send(groupActor)
                                            .message(CHOICE_COUNT_CT_MESSAGE)
                                            .keyboard(countKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "удалить":
                                    resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                    statmt.executeUpdate("DELETE FROM route WHERE id = '" + resSet.getInt("cursor") + "';");
                                    setContext(message.getFromId(), Context.START);
                                    getClient().messages().send(groupActor)
                                            .message(DELETE_ROUTE_MESSAGE)
                                            .keyboard(startKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + CHOICE_PARAM_ROUTE_TO_CHANGE_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - ИЗМЕНИТЬ - ДЕНЬ
                        case Context.DR_ROUTE_CHANGE_DAY:
                            switch (message.getText().toLowerCase()) {
                                case "сегодня":
                                    resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                    statmt.executeUpdate("UPDATE route SET day = '" + Day.getDay() + "' WHERE id = '" + resSet.getInt("cursor") + "';");
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                    getClient().messages().send(groupActor)
                                            .message(ROUTE_CHANGE_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "завтра":
                                    resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                    statmt.executeUpdate("UPDATE route SET day = '" + Day.getNextDay() + "' WHERE id = '" + resSet.getInt("cursor") + "';");
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                    getClient().messages().send(groupActor)
                                            .message(ROUTE_CHANGE_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    Matcher m = Pattern.compile("([0-9]+{1,2} [а-я]+[.]|[0-9]+{1,2}.[0-9]+{1,2}|[0-9]+{1,2} [0-9]+{1,2})").matcher(message.getText());
                                    if (m.find()) {
                                        statmt.executeUpdate("UPDATE route SET day = '" + Day.getDay(m.group()) + "' WHERE userId = '" + message.getFromId() + "';");
                                        setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                        getClient().messages().send(groupActor)
                                                .message(ROUTE_CHANGE_MESSAGE)
                                                .keyboard(changeQueryDriverKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                                    else {
                                        updateDayKeyboard();
                                        getClient().messages().send(groupActor)
                                                .message(WARNING_MESSAGE + "\n" + CHOICE_DAY_MESSAGE)
                                                .keyboard(dayKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                            }
                            break;
                        // ВОДИТЕЛЬ - ИЗМЕНИТЬ - ВРЕМЯ ПГУ
                        case Context.DR_ROUTE_CHANGE_TIME_UN:
                            switch (message.getText().toLowerCase()) {
                                case "8:00":
                                case "9:50":
                                case "11:40":
                                case "13:45":
                                case "15:35":
                                case "17:25":
                                case "нет":
                                    resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                    statmt.executeUpdate("UPDATE route SET timeUn = '" + message.getText() + "' WHERE id = '" + resSet.getInt("cursor") + "';");
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                    getClient().messages().send(groupActor)
                                            .message(ROUTE_CHANGE_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + CHOICE_UN_TIME_MESSAGE)
                                            .keyboard(timeUnDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - ИЗМЕНИТЬ - КОЛВО ПГУ
                        case Context.DR_ROUTE_CHANGE_COUNT_UN:
                            if (NumberUtils.isCreatable(message.getText())) {
                                resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                statmt.executeUpdate("UPDATE route SET countUn = " + message.getText() + " WHERE id = '" + resSet.getInt("cursor") + "';");
                                setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                getClient().messages().send(groupActor)
                                        .message(ROUTE_CHANGE_MESSAGE)
                                        .keyboard(changeQueryDriverKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + CHOICE_COUNT_UN_MESSAGE)
                                        .keyboard(countKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - ИЗМЕНИТЬ - ВРЕМЯ ЗР
                        case Context.DR_ROUTE_CHANGE_TIME_CT:
                            switch (message.getText().toLowerCase()) {
                                case "9:35":
                                case "11:25":
                                case "13:15":
                                case "15:20":
                                case "17:10":
                                case "19:00":
                                case "нет":
                                    resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                    statmt.executeUpdate("UPDATE route SET timeCt = '" + message.getText() + "' WHERE id = '" + resSet.getInt("cursor") + "';");
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                    getClient().messages().send(groupActor)
                                            .message(ROUTE_CHANGE_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + CHOICE_CT_TIME_MESSAGE)
                                            .keyboard(timeCtDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - ИЗМЕНИТЬ - КОЛВО МЕСТ ЗР
                        case Context.DR_ROUTE_CHANGE_COUNT_CT:
                            if (NumberUtils.isCreatable(message.getText())) {
                                resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                statmt.executeUpdate("UPDATE route SET countCt = " + message.getText() + " WHERE id = '" + resSet.getInt("cursor") + "';");
                                setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                getClient().messages().send(groupActor)
                                        .message(ROUTE_CHANGE_MESSAGE)
                                        .keyboard(changeQueryDriverKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + CHOICE_COUNT_CT_MESSAGE)
                                        .keyboard(countKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        default:
                            setContext(message.getFromId(), Context.START);
                            getClient().messages().send(groupActor)
                                    .message(START_MESSAGE)
                                    .keyboard(startKeyboard)
                                    .randomId(0).peerId(message.getFromId()).execute();
                    }
                }
                else {
                    setContext(message.getFromId(), Context.START);
                    getClient().messages().send(groupActor)
                            .message(OLD_CONTEXT_MESSAGE + "\n" + START_MESSAGE)
                            .keyboard(startKeyboard)
                            .randomId(0).peerId(message.getFromId()).execute();
                }
            }
            // Если пользователь пишет нам впервые
            else {
                statmt.execute("INSERT INTO context (userId, contextId, date) " +
                        "VALUES ('" + message.getFromId() + "', 0, '" + Day.getDay() + "'); ");
                statmt.execute("INSERT INTO passengerQuery (userId) " +
                        "VALUES ('" + message.getFromId() + "'); ");
                statmt.execute("INSERT INTO driverQuery (userId) " +
                        "VALUES ('" + message.getFromId() + "'); ");
                getClient().messages().send(groupActor)
                        .message(START_MESSAGE)
                        .keyboard(startKeyboard)
                        .randomId(0).peerId(message.getFromId()).execute();
            }
            /* ***************************************
             *  ПРОИЗОШЛА ОШИБКА В РАБОТЕ БОТА!!!
             *************************************** */
        } catch (Exception e) {
            System.out.println(e.getMessage());
            try {
                getClient().messages().send(groupActor)
                        .message(ERROR_MESSAGE + e.getMessage())
                        .keyboard(startKeyboard)
                        .randomId(0).peerId(message.getFromId()).execute();
            } catch (ApiException ex) {
                System.out.println(e.getMessage());
            } catch (ClientException ex) {
                System.out.println(e.getMessage());
            }
        }
    }

    private void buildStartKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();

        KeyboardButton driverKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Водитель"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row1.add(driverKey);
        listKey.add(row1);

        KeyboardButton passengerKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Пассажир"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row2.add(passengerKey);
        listKey.add(row2);

        startKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildDayKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();
        List<KeyboardButton> row3 = new LinkedList<>();
        List<KeyboardButton> row4 = new LinkedList<>();
        List<KeyboardButton> row5 = new LinkedList<>();

        KeyboardButton moKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Пн (" + Day.getDay(Calendar.MONDAY) + ")"))
                .setColor(KeyboardButtonColor.DEFAULT);
        KeyboardButton tuKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Вт (" + Day.getDay(Calendar.TUESDAY) + ")"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row1.add(moKey);
        row1.add(tuKey);
        listKey.add(row1);

        KeyboardButton weKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Ср (" + Day.getDay(Calendar.WEDNESDAY) + ")"))
                .setColor(KeyboardButtonColor.DEFAULT);
        KeyboardButton thKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Чт (" + Day.getDay(Calendar.THURSDAY) + ")"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row2.add(weKey);
        row2.add(thKey);
        listKey.add(row2);

        KeyboardButton frKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Пт (" + Day.getDay(Calendar.FRIDAY) + ")"))
                .setColor(KeyboardButtonColor.DEFAULT);
        KeyboardButton saKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Сб (" + Day.getDay(Calendar.SATURDAY) + ")"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row3.add(frKey);
        row3.add(saKey);
        listKey.add(row3);

        KeyboardButton tomorrowKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Завтра"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row4.add(tomorrowKey);
        listKey.add(row4);

        KeyboardButton todayKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Сегодня"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row5.add(todayKey);
        listKey.add(row5);

        dayKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildTargetKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();

        KeyboardButton unKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("ПГУ"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row1.add(unKey);
        listKey.add(row1);

        KeyboardButton ctKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Зр"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row2.add(ctKey);
        listKey.add(row2);

        targetKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildTimeUnKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();
        List<KeyboardButton> row3 = new LinkedList<>();

        KeyboardButton firstKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("8:00"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton secondKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("9:50"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row1.add(firstKey);
        row1.add(secondKey);
        listKey.add(row1);

        KeyboardButton thirdKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("11:40"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton fourthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("13:45"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row2.add(thirdKey);
        row2.add(fourthKey);
        listKey.add(row2);

        KeyboardButton fifthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("15:35"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton sixthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("17:25"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row3.add(fifthKey);
        row3.add(sixthKey);
        listKey.add(row3);

        timeUnPasKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildTimeCtKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();
        List<KeyboardButton> row3 = new LinkedList<>();

        KeyboardButton firstKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("9:35"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton secondKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("11:25"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row1.add(firstKey);
        row1.add(secondKey);
        listKey.add(row1);

        KeyboardButton thirdKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("13:15"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton fourthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("15:20"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row2.add(thirdKey);
        row2.add(fourthKey);
        listKey.add(row2);

        KeyboardButton fifthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("17:10"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton sixthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("19:00"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row3.add(fifthKey);
        row3.add(sixthKey);
        listKey.add(row3);

        timeCtPasKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildChoiceKeyboard () {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();

        KeyboardButton nextKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Дальше"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row1.add(nextKey);
        listKey.add(row1);

        KeyboardButton startKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Начало"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row2.add(startKey);
        listKey.add(row2);

        choicePasKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildDriverKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();

        KeyboardButton changeKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Изменить"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row1.add(changeKey);
        listKey.add(row1);

        KeyboardButton newKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Новый"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row2.add(newKey);
        listKey.add(row2);

        driverKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildTimeUnDrKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();
        List<KeyboardButton> row3 = new LinkedList<>();
        List<KeyboardButton> row4 = new LinkedList<>();

        KeyboardButton firstKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("8:00"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton secondKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("9:50"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row1.add(firstKey);
        row1.add(secondKey);
        listKey.add(row1);

        KeyboardButton thirdKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("11:40"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton fourthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("13:45"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row2.add(thirdKey);
        row2.add(fourthKey);
        listKey.add(row2);

        KeyboardButton fifthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("15:35"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton sixthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("17:25"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row3.add(fifthKey);
        row3.add(sixthKey);
        listKey.add(row3);

        KeyboardButton noKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Нет"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row4.add(noKey);
        listKey.add(row4);

        timeUnDrKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildTimeCtDrKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();
        List<KeyboardButton> row3 = new LinkedList<>();
        List<KeyboardButton> row4 = new LinkedList<>();

        KeyboardButton firstKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("9:35"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton secondKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("11:25"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row1.add(firstKey);
        row1.add(secondKey);
        listKey.add(row1);

        KeyboardButton thirdKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("13:15"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton fourthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("15:20"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row2.add(thirdKey);
        row2.add(fourthKey);
        listKey.add(row2);

        KeyboardButton fifthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("17:10"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton sixthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("19:00"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row3.add(fifthKey);
        row3.add(sixthKey);
        listKey.add(row3);

        KeyboardButton noKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Нет"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row4.add(noKey);
        listKey.add(row4);

        timeCtDrKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildCountKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();

        KeyboardButton firstKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("1"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton secondKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("2"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row1.add(firstKey);
        row1.add(secondKey);
        listKey.add(row1);

        KeyboardButton thirdKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("3"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton fourthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("4"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row2.add(thirdKey);
        row2.add(fourthKey);
        listKey.add(row2);

        countKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildChangeDriverKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();
        List<KeyboardButton> row3 = new LinkedList<>();
        List<KeyboardButton> row4 = new LinkedList<>();
        List<KeyboardButton> row5 = new LinkedList<>();

        KeyboardButton dayKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("День"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row1.add(dayKey);
        listKey.add(row1);

        KeyboardButton unTimeKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Время ПГУ"))
                .setColor(KeyboardButtonColor.POSITIVE);
        KeyboardButton unCountKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Места ПГУ"))
                .setColor(KeyboardButtonColor.POSITIVE);
        row2.add(unTimeKey);
        row2.add(unCountKey);
        listKey.add(row2);

        KeyboardButton ctTimeKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Время Зр"))
                .setColor(KeyboardButtonColor.POSITIVE);
        KeyboardButton ctCountKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Места Зр"))
                .setColor(KeyboardButtonColor.POSITIVE);
        row3.add(ctTimeKey);
        row3.add(ctCountKey);
        listKey.add(row3);

        KeyboardButton deleteKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Удалить"))
                .setColor(KeyboardButtonColor.NEGATIVE);
        row4.add(deleteKey);
        listKey.add(row4);

        KeyboardButton startKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Начало"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row5.add(startKey);
        listKey.add(row5);

        changeQueryDriverKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildChoiceDriverQueryKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();
        List<KeyboardButton> row3 = new LinkedList<>();
        List<KeyboardButton> row4 = new LinkedList<>();

        KeyboardButton firstKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("1"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton secondKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("2"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row1.add(firstKey);
        row1.add(secondKey);
        listKey.add(row1);

        KeyboardButton thirdKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("3"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton fourthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("4"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row2.add(thirdKey);
        row2.add(fourthKey);
        listKey.add(row2);

        KeyboardButton fifthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("5"))
                .setColor(KeyboardButtonColor.PRIMARY);
        KeyboardButton sixthKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("6"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row3.add(fifthKey);
        row3.add(sixthKey);
        listKey.add(row3);

        KeyboardButton startKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Начало"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row4.add(startKey);
        listKey.add(row4);

        choiceDriverQueryKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void setContext(int userId, int contextId) throws SQLException {
        statmt.executeUpdate("UPDATE context SET contextId = " + contextId + ", date = '" + Day.getDay() + "' WHERE userId = '" + userId + "';");
    }

    private void updateDayKeyboard() {
        dayKeyboard.getButtons().get(0).get(0).getAction().setLabel("Пн (" + Day.getDay(Calendar.MONDAY) + ")");
        dayKeyboard.getButtons().get(0).get(1).getAction().setLabel("Вт (" + Day.getDay(Calendar.TUESDAY) + ")");
        dayKeyboard.getButtons().get(1).get(0).getAction().setLabel("Ср (" + Day.getDay(Calendar.WEDNESDAY) + ")");
        dayKeyboard.getButtons().get(1).get(1).getAction().setLabel("Чт (" + Day.getDay(Calendar.THURSDAY) + ")");
        dayKeyboard.getButtons().get(2).get(0).getAction().setLabel("Пт (" + Day.getDay(Calendar.FRIDAY) + ")");
        dayKeyboard.getButtons().get(2).get(1).getAction().setLabel("Сб (" + Day.getDay(Calendar.SATURDAY) + ")");
    }

}