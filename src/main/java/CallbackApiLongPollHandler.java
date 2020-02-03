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
import java.util.Date;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallbackApiLongPollHandler extends CallbackApiLongPoll {

    private static final int LIMIT_ROUTE = 6;

    private static final String START_MESSAGE = "Главное меню: выберите набор функций";
    private static final String WARNING_MESSAGE = "Неккоректный ответ, попробуйте ещё раз";
    private static final String ERROR_MESSAGE = "ОШИБКА! Что-то пошло не так т_т\n" +
            "Пожалуйста, сделайте скриншот переписки с ботом и этого сообщения.\n" +
            "Оповестив администрацию об ошибке, вы поможете её устранить)\nError: ";
    private static final String OLD_CONTEXT_MESSAGE = "Ваша сессия устарела, возвращение в главное меню";

    private static final String WARNING_UNSUBSCRIBE_MESSAGE = "(Если ранее подписывались на какой-либо маршрут, то после этого действия подписка будет отменена)";
    private static final String DAY_MESSAGE = "День поездки:";
    private static final String PAS_CHOICE_TARGET_MESSAGE = "Пункт назначения:";
    private static final String PAS_CHOICE_TIME_UN_MESSAGE = "Время пары:";
    private static final String PAS_CHOICE_TIME_CT_MESSAGE = "Время после пары:";
    private static final String PAS_DRIVER_NOT_FOUND_MESSAGE = "Нет водителей";
    private static final String PAS_SUBSCRIBE_MESSAGE = "Если отправите \"Подписаться\", я оповещу вас о новых водителях по вашему запросу";
    private static final String PAS_SUBSCRIBE_SUCCESS_MESSAGE = "Вы подписались на заданный маршрут";
    private static final String PAS_DRIVER_SEND_QUERY_MESSAGE = "Запрос отправлен";
    private static final String PAS_DRIVER_RECIVED_QUERY_MESSAGE = "Этот пассажир не может с вами связаться";

    private static final String DR_MENU_MESSAGE = "Меню водителя: вы можете изменить свою страничку или изменить/создать новый маршрут";
    private static final String DR_PAGE_MESSAGE = "Что хотите изменить?";
    private static final String DR_PAGE_CHANGE_MESSAGE = "Отправьте мне новое значение:";
    private static final String DR_PAGE_CHANGE_SUCCESS_MESSAGE = "Изменения сохранены";
    private static final String DR_PAGE_CLEAR_MESSAGE = "Страница была очищена";
    private static final String DR_ROUTE_CHANGE_MESSAGE = "Какой маршрут хотите изменить?";
    private static final String DR_ROUTE_CHANGE_NOT_ROUTES_MESSAGE = "У вас нет созданных маршрутов";
    private static final String DR_ROUTE_CHANGE_THIS_ROUTE_NOT_FOUND_MESSAGE = "У вас нет такого маршрута";
    private static final String DR_ROUTE_CHANGE_MENU_MESSAGE = "Что хотите изменить?";
    private static final String DR_ROUTE_CHANGE_SUCCESS_MESSAGE = "Маршрут изменён!\nЧто-нибудь ещё?";
    private static final String DR_ROUTE_CHANGE_DELETE_MESSAGE = "Маршрут успешно удалён! Возвращение в главное меню";
    private static final String DR_ROUTE_NEW_EXCEEDED_MESSAGE = "Лимитр маршрутов превышен!\nУдалите или измените существующие";
    private static final String DR_ROUTE_NEW_COUNT_UN_MESSAGE = "Выберите кол-во мест в ПГУ";
    private static final String DR_ROUTE_NEW_COUNT_CT_MESSAGE = "Выберите кол-во мест в Зр";
    private static final String DR_ROUTE_NEW_SUCCESS_MESSAGE = "Маршрут создан";
    private static final String DR_ROUTE_NEW_ERROR_MESSAGE = "Маршрут должен иметь хотя бы один пункт назначения!";

    public static Connection connDb;
    public static Statement statmt;
    public static ResultSet resSet;
    public static Semaphore sem;

    private GroupActor groupActor;

    // Клавиатуры
    private Keyboard startKeyboard;
    private Keyboard driverKeyboard;
    private Keyboard driverPageKeyboard;
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
    private Keyboard subscribeKeyboard;

    public CallbackApiLongPollHandler(VkApiClient client, GroupActor actor, int indexUpdTh) {
        super(client, actor);
        groupActor = actor;

        Day.addKnownPatternsDate(new SimpleDateFormat("d MMM yyyy"));
        Day.addKnownPatternsDate(new SimpleDateFormat("d MM yyyy"));
        Day.addKnownPatternsDate(new SimpleDateFormat("d M yyyy"));
        Day.addKnownPatternsDate(new SimpleDateFormat("d.M yyyy"));

        buildStartKeyboard();
        buildDriverKeyboard();
        buildDriverPageKeyboard();
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
        buildSubscribeKeyboard();
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

            sem = new Semaphore(1);
            UpdateDbThread updateDbThread = new UpdateDbThread(getClient(), actor, statmt, sem);
            updateDbThread.setName("UpdateThread" + String.valueOf(indexUpdTh));
            updateDbThread.start();

            System.out.println("Initialization complete! Run.\n");
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void messageNew(Integer groupId, Message message) {
        try {
            sem.acquire();
            if (message.getText().equals("0") ||
                    message.getText().toLowerCase().equals("начало")) {
                setContext(message.getFromId(), Context.START);
                getClient().messages().send(groupActor)
                        .message(START_MESSAGE)
                        .keyboard(startKeyboard)
                        .randomId(0).peerId(message.getFromId()).execute();
                sem.release();
                return;
            }
            resSet = statmt.executeQuery("SELECT * FROM context WHERE userId = '" + message.getFromId() + "';");
            // Если данный пользователь уже есть в таблице КОНТЕКСТ
            if (resSet.next()) {
                Context c = new Context(resSet);
                if (c.getDate().equals(Day.getDay()) || c.getId() == 0) {
                    switch (c.getId()) {
                        // СТАРТ
                        case Context.START:
                            switch (message.getText().toLowerCase()) {
                                case "водитель":
                                    setContext(message.getFromId(), Context.DR_MENU);
                                    getClient().messages().send(groupActor)
                                            .message(DR_MENU_MESSAGE)
                                            .keyboard(driverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "пассажир":
                                    updateDayKeyboard();
                                    setContext(message.getFromId(), Context.PAS_CHOICE_DAY);
                                    getClient().messages().send(groupActor)
                                            .message(DAY_MESSAGE + "\n " + WARNING_UNSUBSCRIBE_MESSAGE)
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
                                    statmt.executeUpdate("UPDATE passengerQuery SET subscribe = 0, day = '" + Day.getDay() + "' WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.PAS_CHOICE_TARGET);
                                    getClient().messages().send(groupActor)
                                            .message(PAS_CHOICE_TARGET_MESSAGE)
                                            .keyboard(targetKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "завтра":
                                    statmt.executeUpdate("UPDATE passengerQuery SET subscribe = 0, day = '" + Day.getNextDay() + "' WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.PAS_CHOICE_TARGET);
                                    getClient().messages().send(groupActor)
                                            .message(PAS_CHOICE_TARGET_MESSAGE)
                                            .keyboard(targetKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    Matcher m = Pattern.compile("([0-9]+{1,2} [а-я]+|[0-9]+{1,2}.[0-9]+{1,2}|[0-9]+{1,2} [0-9]+{1,2})").matcher(message.getText());
                                    if (m.find()) {
                                        statmt.executeUpdate("UPDATE passengerQuery SET subscribe = 0, day = '" + Day.getDay(m.group()) + "' WHERE userId = '" + message.getFromId() + "';");
                                        setContext(message.getFromId(), Context.PAS_CHOICE_TARGET);
                                        getClient().messages().send(groupActor)
                                                .message(PAS_CHOICE_TARGET_MESSAGE)
                                                .keyboard(targetKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    } else {
                                        updateDayKeyboard();
                                        getClient().messages().send(groupActor)
                                                .message(WARNING_MESSAGE + "\n" + DAY_MESSAGE)
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
                                            .message(PAS_CHOICE_TIME_UN_MESSAGE)
                                            .keyboard(timeUnPasKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "зр":
                                    statmt.executeUpdate("UPDATE passengerQuery SET target = '" + message.getText() + "' WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.PAS_CHOICE_TIME);
                                    getClient().messages().send(groupActor)
                                            .message(PAS_CHOICE_TIME_CT_MESSAGE)
                                            .keyboard(timeCtPasKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + PAS_CHOICE_TARGET_MESSAGE)
                                            .keyboard(targetKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ПАССАЖИР - ВЫБОР ВРЕМЕНИ
                        case Context.PAS_CHOICE_TIME:
                            if (message.getText().equals("8:00") || message.getText().equals("9:35")
                                    || message.getText().equals("9:50") || message.getText().equals("11:25")
                                    || message.getText().equals("11:40") || message.getText().equals("13:15")
                                    || message.getText().equals("13:45") || message.getText().equals("15:20")
                                    || message.getText().equals("15:35") || message.getText().equals("17:10")
                                    || message.getText().equals("17:25") || message.getText().equals("19:00")) {
                                statmt.executeUpdate("UPDATE passengerQuery SET time = '" + message.getText() + "', cursor = 0 WHERE userId = '" + message.getFromId() + "';");
                                setContext(message.getFromId(), Context.PAS_CHOICE_DRIVER);
                            } else {
                                resSet = statmt.executeQuery("SELECT target FROM passengerQuery WHERE userId = '" + message.getFromId() + "';");
                                resSet.next();
                                if (resSet.getString("target").equals("ПГУ")) {
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + PAS_CHOICE_TIME_UN_MESSAGE)
                                            .keyboard(timeUnPasKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                } else {
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + PAS_CHOICE_TIME_CT_MESSAGE)
                                            .keyboard(timeCtPasKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                }
                                break;
                            }
                            // ПАССАЖИР - ВЫБОР ВОДИТЕЛЯ
                        case Context.PAS_CHOICE_DRIVER:
                            if (message.getText().toLowerCase().equals("отправить запрос")) {
                                resSet = statmt.executeQuery("SELECT userId FROM route WHERE id = " +
                                        "(SELECT cursor FROM passengerQuery WHERE userId = " + message.getFromId() + ");");
                                int idDriver = resSet.getInt("userId");
                                getClient().messages().send(groupActor)
                                        .message(PAS_DRIVER_RECIVED_QUERY_MESSAGE + "\n@id" + message.getFromId())
                                        .randomId(0).peerId(idDriver).execute();
                                getClient().messages().send(groupActor)
                                        .message(PAS_DRIVER_SEND_QUERY_MESSAGE)
                                        .keyboard(choicePasKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            } else {
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
                                    resSet = statmt.executeQuery("SELECT * FROM driverPage WHERE userId = " + r.getUserId() + ";");
                                    DriverPage dp = new DriverPage(resSet);
                                    statmt.executeUpdate("UPDATE passengerQuery SET cursor = " + r.getId() + " WHERE userId = " + message.getFromId() + ";");
                                    getClient().messages().send(groupActor)
                                            .message(dp.toString() + "\n" + r.getRouteText())
                                            .keyboard(choicePasKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                } else {
                                    statmt.executeUpdate("UPDATE passengerQuery SET cursor = 0 WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.PAS_DRIVER_NOT_FOUND);
                                    getClient().messages().send(groupActor)
                                            .message(PAS_DRIVER_NOT_FOUND_MESSAGE + "\n" + PAS_SUBSCRIBE_MESSAGE)
                                            .keyboard(subscribeKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                }
                            }
                            break;
                        // ПАССАЖИР - НЕТ ВОДИТЕЛЕЙ
                        case Context.PAS_DRIVER_NOT_FOUND:
                            switch (message.getText().toLowerCase()) {
                                case "подписаться":
                                    statmt.executeUpdate("UPDATE passengerQuery SET subscribe = 1 WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.START);
                                    getClient().messages().send(groupActor)
                                            .message(PAS_SUBSCRIBE_SUCCESS_MESSAGE + "\n" + START_MESSAGE)
                                            .keyboard(startKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + PAS_SUBSCRIBE_MESSAGE)
                                            .keyboard(subscribeKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                            }
                            break;
                        // ВОДИТЕЛЬ
                        case Context.DR_MENU:
                            switch (message.getText().toLowerCase()) {
                                case "страница":
                                    resSet = statmt.executeQuery("SELECT * FROM driverPage WHERE userId = " + message.getFromId() + ";");
                                    resSet.next();
                                    DriverPage dp = new DriverPage(resSet);
                                    setContext(message.getFromId(), Context.DR_PAGE);
                                    getClient().messages().send(groupActor)
                                            .message(dp.toString() + "\n\n" + DR_PAGE_MESSAGE)
                                            .keyboard(driverPageKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "изменить":
                                    resSet = statmt.executeQuery("SELECT * FROM route WHERE userId = " + message.getFromId() + ";");
                                    int i = 0;
                                    StringBuilder messageText = new StringBuilder();
                                    while (resSet.next()) {
                                        i++;
                                        Route r = new Route(resSet);
                                        messageText.append(i).append(") ").append(r.toString()).append("\n");
                                    }
                                    if (i != 0) {
                                        setContext(message.getFromId(), Context.DR_ROUTE_CHANGE);
                                        getClient().messages().send(groupActor)
                                                .message(messageText.toString() + DR_ROUTE_CHANGE_MESSAGE)
                                                .keyboard(choiceDriverQueryKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    } else {
                                        getClient().messages().send(groupActor)
                                                .message(DR_ROUTE_CHANGE_NOT_ROUTES_MESSAGE)
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
                                                .message(DAY_MESSAGE)
                                                .keyboard(dayKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    } else {
                                        getClient().messages().send(groupActor)
                                                .message(DR_ROUTE_NEW_EXCEEDED_MESSAGE)
                                                .keyboard(driverKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + DR_MENU_MESSAGE)
                                            .keyboard(driverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - СТРАНИЧКА
                        case Context.DR_PAGE:
                            if (message.getText().toLowerCase().equals("очистить")) {
                                statmt.executeUpdate("UPDATE driverPage SET nickname = '-', indexCar = '-', modelCar = '-', " +
                                        "description = '-' WHERE userId = " + message.getFromId() + ";");
                                setContext(message.getFromId(), Context.START);
                                getClient().messages().send(groupActor)
                                        .message(DR_PAGE_CLEAR_MESSAGE + "\n" + START_MESSAGE)
                                        .keyboard(startKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            } else {
                                getClient().messages().send(groupActor)
                                        .message(DR_PAGE_CHANGE_MESSAGE)
                                        .randomId(0).peerId(message.getFromId()).execute();
                                switch (message.getText().toLowerCase()) {
                                    case "имя":
                                        setContext(message.getFromId(), Context.DR_PAGE_NICKNAME);
                                        break;
                                    case "номер машины":
                                        setContext(message.getFromId(), Context.DR_PAGE_INDEX_CAR);
                                        break;
                                    case "модель машины":
                                        setContext(message.getFromId(), Context.DR_PAGE_MODEL_CAR);
                                        break;
                                    case "описание":
                                        setContext(message.getFromId(), Context.DR_PAGE_DESCRIPTION);
                                        break;
                                    default:
                                        getClient().messages().send(groupActor)
                                                .message(WARNING_MESSAGE + "\n" + DR_PAGE_MESSAGE)
                                                .keyboard(driverPageKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                }
                            }
                            break;
                        // ВОДИТЕЛЬ - СТРАНИЧКА - ИМЯ
                        case Context.DR_PAGE_NICKNAME:
                            statmt.executeUpdate("UPDATE driverPage SET nickname = '" + message.getText() +
                                    "' WHERE userId = " + message.getFromId() + ";");
                            setContext(message.getFromId(), Context.DR_PAGE);
                            resSet = statmt.executeQuery("SELECT * FROM driverPage WHERE userId = " + message.getFromId() + ";");
                            resSet.next();
                            getClient().messages().send(groupActor)
                                    .message(DR_PAGE_CHANGE_SUCCESS_MESSAGE + "\n\n" + new DriverPage(resSet).toString() + "\n" + DR_PAGE_MESSAGE)
                                    .keyboard(driverPageKeyboard)
                                    .randomId(0).peerId(message.getFromId()).execute();
                            break;
                        // ВОДИТЕЛЬ - СТРАНИЧКА - НОМЕР МАШИНЫ
                        case Context.DR_PAGE_INDEX_CAR:
                            statmt.executeUpdate("UPDATE driverPage SET indexCar = '" + message.getText() +
                                    "' WHERE userId = " + message.getFromId() + ";");
                            setContext(message.getFromId(), Context.DR_PAGE);
                            resSet = statmt.executeQuery("SELECT * FROM driverPage WHERE userId = " + message.getFromId() + ";");
                            resSet.next();
                            getClient().messages().send(groupActor)
                                    .message(DR_PAGE_CHANGE_SUCCESS_MESSAGE + "\n\n" + new DriverPage(resSet).toString() + "\n" + DR_PAGE_MESSAGE)
                                    .keyboard(driverPageKeyboard)
                                    .randomId(0).peerId(message.getFromId()).execute();
                            break;
                        // ВОДИТЕЛЬ - СТРАНИЧКА - МОДЕЛЬ МАШИНЫ
                        case Context.DR_PAGE_MODEL_CAR:
                            statmt.executeUpdate("UPDATE driverPage SET modelCar = '" + message.getText() +
                                    "' WHERE userId = " + message.getFromId() + ";");
                            setContext(message.getFromId(), Context.DR_PAGE);
                            resSet = statmt.executeQuery("SELECT * FROM driverPage WHERE userId = " + message.getFromId() + ";");
                            resSet.next();
                            getClient().messages().send(groupActor)
                                    .message(DR_PAGE_CHANGE_SUCCESS_MESSAGE + "\n\n" + new DriverPage(resSet).toString() + "\n" + DR_PAGE_MESSAGE)
                                    .keyboard(driverPageKeyboard)
                                    .randomId(0).peerId(message.getFromId()).execute();
                            break;
                        // ВОДИТЕЛЬ - СТРАНИЧКА - ОПИСАНИЕ
                        case Context.DR_PAGE_DESCRIPTION:
                            statmt.executeUpdate("UPDATE driverPage SET description = '" + message.getText() +
                                    "' WHERE userId = " + message.getFromId() + ";");
                            setContext(message.getFromId(), Context.DR_PAGE);
                            resSet = statmt.executeQuery("SELECT * FROM driverPage WHERE userId = " + message.getFromId() + ";");
                            resSet.next();
                            getClient().messages().send(groupActor)
                                    .message(DR_PAGE_CHANGE_SUCCESS_MESSAGE + "\n\n" + new DriverPage(resSet).toString() + "\n" + DR_PAGE_MESSAGE)
                                    .keyboard(driverPageKeyboard)
                                    .randomId(0).peerId(message.getFromId()).execute();
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - СОЗДАТЬ - ВЫБОР ДНЯ
                        case Context.DR_ROUTE_NEW_DAY:
                            switch (message.getText().toLowerCase()) {
                                case "сегодня":
                                    statmt.executeUpdate("UPDATE driverQuery SET day = '" + Day.getDay() + "' WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.DR_ROUTE_NEW_TIME_UN);
                                    getClient().messages().send(groupActor)
                                            .message(PAS_CHOICE_TIME_UN_MESSAGE)
                                            .keyboard(timeUnDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "завтра":
                                    statmt.executeUpdate("UPDATE driverQuery SET day = '" + Day.getNextDay() + "' WHERE userId = '" + message.getFromId() + "';");
                                    setContext(message.getFromId(), Context.DR_ROUTE_NEW_TIME_UN);
                                    getClient().messages().send(groupActor)
                                            .message(PAS_CHOICE_TIME_UN_MESSAGE)
                                            .keyboard(timeUnDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    Matcher m = Pattern.compile("([0-9]+{1,2} [а-я]+|[0-9]+{1,2}.[0-9]+{1,2}|[0-9]+{1,2} [0-9]+{1,2})").matcher(message.getText());
                                    if (m.find()) {
                                        statmt.executeUpdate("UPDATE driverQuery SET day = '" + Day.getDay(m.group()) + "' WHERE userId = '" + message.getFromId() + "';");
                                        setContext(message.getFromId(), Context.DR_ROUTE_NEW_TIME_UN);
                                        getClient().messages().send(groupActor)
                                                .message(PAS_CHOICE_TIME_UN_MESSAGE)
                                                .keyboard(timeUnDrKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    } else {
                                        updateDayKeyboard();
                                        getClient().messages().send(groupActor)
                                                .message(WARNING_MESSAGE + "\n" + DAY_MESSAGE)
                                                .keyboard(dayKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                            }
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - ВЫБОР ПАРЫ В УНИВЕР
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
                                        .message(DR_ROUTE_NEW_COUNT_UN_MESSAGE)
                                        .keyboard(countKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            } else if (message.getText().toLowerCase().equals("нет")) {
                                statmt.executeUpdate("UPDATE driverQuery SET timeUn = '" + message.getText() + "', " +
                                        "countUn = 0 WHERE userId = '" + message.getFromId() + "';");
                                setContext(message.getFromId(), Context.DR_ROUTE_NEW_TIME_CT);
                                getClient().messages().send(groupActor)
                                        .message(PAS_CHOICE_TIME_CT_MESSAGE)
                                        .keyboard(timeCtDrKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            } else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + PAS_CHOICE_TIME_UN_MESSAGE)
                                        .keyboard(timeCtPasKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - ВЫБОР КОЛ-ВА МЕСТ В УНИВЕР
                        case Context.DR_ROUTE_NEW_COUNT_UN:
                            if (NumberUtils.isCreatable(message.getText())) {
                                statmt.executeUpdate("UPDATE driverQuery SET countUn = " + message.getText() + " WHERE userId = '" + message.getFromId() + "';");
                                setContext(message.getFromId(), Context.DR_ROUTE_NEW_TIME_CT);
                                getClient().messages().send(groupActor)
                                        .message(PAS_CHOICE_TIME_CT_MESSAGE)
                                        .keyboard(timeCtDrKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            } else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + DR_ROUTE_NEW_COUNT_UN_MESSAGE)
                                        .keyboard(countKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - ВЫБОР ВРЕМЕНИ В ГОРОД
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
                                        .message(DR_ROUTE_NEW_COUNT_CT_MESSAGE)
                                        .keyboard(countKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            } else if (message.getText().toLowerCase().equals("нет")) {
                                statmt.executeUpdate("UPDATE driverQuery SET timeCt = '" + message.getText() + "', " +
                                        "countCt = 0 WHERE userId = '" + message.getFromId() + "';");
                                setContext(message.getFromId(), Context.START);

                                resSet = statmt.executeQuery("SELECT * FROM driverQuery WHERE userId = " + message.getFromId());
                                resSet.next();
                                if (resSet.getString("timeUn").toLowerCase().equals("нет")
                                        && resSet.getString("timeCt").toLowerCase().equals("нет")) {
                                    getClient().messages().send(groupActor)
                                            .message(DR_ROUTE_NEW_ERROR_MESSAGE + "\n" + START_MESSAGE)
                                            .keyboard(startKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                } else {
                                    DriverQuery dq = new DriverQuery(resSet);
                                    statmt.execute("INSERT INTO route (userId, day, timeUn, countUn, timeCt, countCt) " +
                                            "VALUES ('" + message.getFromId() + "', '" + dq.getDay() + "', '" + dq.getTimeUn() + "', " +
                                            dq.getCountUn() + ", '" + dq.getTimeCt() + "', " + dq.getCountCt() + ");");
                                    setContext(message.getFromId(), Context.START);
                                    getClient().messages().send(groupActor)
                                            .message(DR_ROUTE_NEW_SUCCESS_MESSAGE + "\n" + START_MESSAGE)
                                            .keyboard(startKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                }
                            } else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + PAS_CHOICE_TIME_CT_MESSAGE)
                                        .keyboard(timeCtPasKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - ВЫБОР КОЛ-ВА МЕСТ В ЗР
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
                                        .message(DR_ROUTE_NEW_SUCCESS_MESSAGE + "\n" + START_MESSAGE)
                                        .keyboard(startKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            } else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + DR_ROUTE_NEW_COUNT_CT_MESSAGE)
                                        .keyboard(countKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - ИЗМЕНИТЬ - ВЫБОР МАРШРУТА
                        case Context.DR_ROUTE_CHANGE:
                            if (NumberUtils.isCreatable(message.getText()) && Integer.parseInt(message.getText()) <= LIMIT_ROUTE && Integer.parseInt(message.getText()) > 0) {
                                resSet = statmt.executeQuery("SELECT id FROM route WHERE userId = " + message.getFromId() + ";");
                                boolean isHave = true;
                                for (int i = 0; i < Integer.parseInt(message.getText()); i++) {
                                    if (!resSet.next()) {
                                        getClient().messages().send(groupActor)
                                                .message(DR_ROUTE_CHANGE_THIS_ROUTE_NOT_FOUND_MESSAGE + "\n" + DR_ROUTE_CHANGE_MESSAGE)
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
                                            .message(DR_ROUTE_CHANGE_MENU_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                }
                            } else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + DR_ROUTE_CHANGE_MESSAGE)
                                        .keyboard(choiceDriverQueryKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - ИЗМЕНИТЬ МАРШРУТ
                        case Context.DR_ROUTE_CHANGE_MENU:
                            switch (message.getText().toLowerCase()) {
                                case "день":
                                    updateDayKeyboard();
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_DAY);
                                    getClient().messages().send(groupActor)
                                            .message(DAY_MESSAGE)
                                            .keyboard(dayKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "время пгу":
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_TIME_UN);
                                    getClient().messages().send(groupActor)
                                            .message(PAS_CHOICE_TIME_UN_MESSAGE)
                                            .keyboard(timeUnDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "места пгу":
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_COUNT_UN);
                                    getClient().messages().send(groupActor)
                                            .message(DR_ROUTE_NEW_COUNT_UN_MESSAGE)
                                            .keyboard(countKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "время зр":
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_TIME_CT);
                                    getClient().messages().send(groupActor)
                                            .message(PAS_CHOICE_TIME_CT_MESSAGE)
                                            .keyboard(timeCtDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "места зр":
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_COUNT_CT);
                                    getClient().messages().send(groupActor)
                                            .message(DR_ROUTE_NEW_COUNT_CT_MESSAGE)
                                            .keyboard(countKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "удалить":
                                    resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                    statmt.executeUpdate("DELETE FROM route WHERE id = '" + resSet.getInt("cursor") + "';");
                                    setContext(message.getFromId(), Context.START);
                                    getClient().messages().send(groupActor)
                                            .message(DR_ROUTE_CHANGE_DELETE_MESSAGE)
                                            .keyboard(startKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + DR_ROUTE_CHANGE_MENU_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - ИЗМЕНИТЬ - ДЕНЬ
                        case Context.DR_ROUTE_CHANGE_DAY:
                            switch (message.getText().toLowerCase()) {
                                case "сегодня":
                                    resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                    statmt.executeUpdate("UPDATE route SET day = '" + Day.getDay() + "' WHERE id = '" + resSet.getInt("cursor") + "';");
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                    getClient().messages().send(groupActor)
                                            .message(DR_ROUTE_CHANGE_SUCCESS_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                case "завтра":
                                    resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                    statmt.executeUpdate("UPDATE route SET day = '" + Day.getNextDay() + "' WHERE id = '" + resSet.getInt("cursor") + "';");
                                    setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                    getClient().messages().send(groupActor)
                                            .message(DR_ROUTE_CHANGE_SUCCESS_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    Matcher m = Pattern.compile("([0-9]+{1,2} [а-я]+|[0-9]+{1,2}.[0-9]+{1,2}|[0-9]+{1,2} [0-9]+{1,2})").matcher(message.getText());
                                    if (m.find()) {
                                        statmt.executeUpdate("UPDATE route SET day = '" + Day.getDay(m.group()) + "' WHERE userId = '" + message.getFromId() + "';");
                                        setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                        getClient().messages().send(groupActor)
                                                .message(DR_ROUTE_CHANGE_SUCCESS_MESSAGE)
                                                .keyboard(changeQueryDriverKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    } else {
                                        updateDayKeyboard();
                                        getClient().messages().send(groupActor)
                                                .message(WARNING_MESSAGE + "\n" + DAY_MESSAGE)
                                                .keyboard(dayKeyboard)
                                                .randomId(0).peerId(message.getFromId()).execute();
                                    }
                            }
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - ИЗМЕНИТЬ - ВРЕМЯ ПГУ
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
                                            .message(DR_ROUTE_CHANGE_SUCCESS_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + PAS_CHOICE_TIME_UN_MESSAGE)
                                            .keyboard(timeUnDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - ИЗМЕНИТЬ - КОЛВО ПГУ
                        case Context.DR_ROUTE_CHANGE_COUNT_UN:
                            if (NumberUtils.isCreatable(message.getText())) {
                                resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                statmt.executeUpdate("UPDATE route SET countUn = " + message.getText() + " WHERE id = '" + resSet.getInt("cursor") + "';");
                                setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                getClient().messages().send(groupActor)
                                        .message(DR_ROUTE_CHANGE_SUCCESS_MESSAGE)
                                        .keyboard(changeQueryDriverKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            } else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + DR_ROUTE_NEW_COUNT_UN_MESSAGE)
                                        .keyboard(countKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - ИЗМЕНИТЬ - ВРЕМЯ ЗР
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
                                            .message(DR_ROUTE_CHANGE_SUCCESS_MESSAGE)
                                            .keyboard(changeQueryDriverKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                                    break;
                                default:
                                    getClient().messages().send(groupActor)
                                            .message(WARNING_MESSAGE + "\n" + PAS_CHOICE_TIME_CT_MESSAGE)
                                            .keyboard(timeCtDrKeyboard)
                                            .randomId(0).peerId(message.getFromId()).execute();
                            }
                            break;
                        // ВОДИТЕЛЬ - МАРШРУТ - ИЗМЕНИТЬ - КОЛВО МЕСТ ЗР
                        case Context.DR_ROUTE_CHANGE_COUNT_CT:
                            if (NumberUtils.isCreatable(message.getText())) {
                                resSet = statmt.executeQuery("SELECT cursor FROM driverQuery WHERE userId = " + message.getFromId() + ";");
                                statmt.executeUpdate("UPDATE route SET countCt = " + message.getText() + " WHERE id = '" + resSet.getInt("cursor") + "';");
                                setContext(message.getFromId(), Context.DR_ROUTE_CHANGE_MENU);
                                getClient().messages().send(groupActor)
                                        .message(DR_ROUTE_CHANGE_SUCCESS_MESSAGE)
                                        .keyboard(changeQueryDriverKeyboard)
                                        .randomId(0).peerId(message.getFromId()).execute();
                            } else {
                                getClient().messages().send(groupActor)
                                        .message(WARNING_MESSAGE + "\n" + DR_ROUTE_NEW_COUNT_CT_MESSAGE)
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
                } else {
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
                statmt.execute("INSERT INTO driverPage " +
                        "VALUES ('" + message.getFromId() + "', '-', '-', '-', '-'); ");
                getClient().messages().send(groupActor)
                        .message(START_MESSAGE)
                        .keyboard(startKeyboard)
                        .randomId(0).peerId(message.getFromId()).execute();
            }
            sem.release();
            /* ***************************************
             *  ПРОИЗОШЛА ОШИБКА В РАБОТЕ БОТА!!!
             *************************************** */
        }
        catch (InterruptedException e) {
            System.out.println(new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss | ").format(new Date()) + "Главная ветка бесконечно долго ждёт семафор\n" + e.getMessage());
        }
        catch (Exception e) {
            sem.release();
            System.out.println(e.getMessage());
            try {
                setContext(message.getFromId(), Context.START);
                getClient().messages().send(groupActor)
                        .message(ERROR_MESSAGE + e.getMessage())
                        .keyboard(startKeyboard)
                        .randomId(0).peerId(message.getFromId()).execute();
            } catch (ApiException ex) {
                System.out.println(new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss | ").format(new Date()) + e.getMessage());
            } catch (ClientException ex) {
                System.out.println(new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss | ").format(new Date()) + e.getMessage());
            } catch (SQLException ex) {
                ex.printStackTrace();
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
        List<KeyboardButton> row3 = new LinkedList<>();

        KeyboardButton sendQueryKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Отправить запрос"))
                .setColor(KeyboardButtonColor.NEGATIVE);
        row1.add(sendQueryKey);
        listKey.add(row1);

        KeyboardButton nextKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Дальше"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row2.add(nextKey);
        listKey.add(row2);

        KeyboardButton startKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Начало"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row3.add(startKey);
        listKey.add(row3);

        choicePasKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildDriverKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();
        List<KeyboardButton> row3 = new LinkedList<>();

        KeyboardButton pageKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Страница"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row1.add(pageKey);
        listKey.add(row1);

        KeyboardButton changeKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Изменить"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row2.add(changeKey);
        listKey.add(row2);

        KeyboardButton newKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Новый"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row3.add(newKey);
        listKey.add(row3);

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

    private void buildDriverPageKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();
        List<KeyboardButton> row3 = new LinkedList<>();
        List<KeyboardButton> row4 = new LinkedList<>();
        List<KeyboardButton> row5 = new LinkedList<>();

        KeyboardButton nicknameKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Имя"))
                .setColor(KeyboardButtonColor.PRIMARY);
        row1.add(nicknameKey);
        listKey.add(row1);

        KeyboardButton indexCarKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Номер машины"))
                .setColor(KeyboardButtonColor.POSITIVE);
        KeyboardButton modelCarKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Модель машины"))
                .setColor(KeyboardButtonColor.POSITIVE);
        row2.add(indexCarKey);
        row2.add(modelCarKey);
        listKey.add(row2);

        KeyboardButton ctTimeKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Описание"))
                .setColor(KeyboardButtonColor.POSITIVE);
        row3.add(ctTimeKey);
        listKey.add(row3);

        KeyboardButton clearKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Очистить"))
                .setColor(KeyboardButtonColor.NEGATIVE);
        row4.add(clearKey);
        listKey.add(row4);

        KeyboardButton startKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Начало"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row5.add(startKey);
        listKey.add(row5);

        driverPageKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    private void buildSubscribeKeyboard() {
        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();
        List<KeyboardButton> row2 = new LinkedList<>();

        KeyboardButton subscribeKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Подписаться"))
                .setColor(KeyboardButtonColor.POSITIVE);
        row1.add(subscribeKey);
        listKey.add(row1);

        KeyboardButton startKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("Начало"))
                .setColor(KeyboardButtonColor.DEFAULT);
        row2.add(startKey);
        listKey.add(row2);

        subscribeKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
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