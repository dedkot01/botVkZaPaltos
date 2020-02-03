package database;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;

public class Day {

    private static LinkedList<SimpleDateFormat> knownPatternsDate = new LinkedList<SimpleDateFormat>();

    public static void addKnownPatternsDate(SimpleDateFormat sdf) {
        knownPatternsDate.add(sdf);
    }

    public static String getNextDay() {
        GregorianCalendar gc = new GregorianCalendar();
        gc.add(Calendar.DAY_OF_MONTH, 1);
        return new SimpleDateFormat("d.M.yyyy").format(gc.getTime());
    }

    public static String getDay() {
        return new SimpleDateFormat("d.M.yyyy").format(new Date());
    }

    public static String getDay(String value) {
        value += " " + new GregorianCalendar().get(Calendar.YEAR);
        for (SimpleDateFormat pattern : knownPatternsDate) {
            try {
                return new SimpleDateFormat("d.M.yyyy").format(pattern.parse(value));
            } catch (ParseException pe) {}
        }
        System.err.println("No known Date format found: " + value);
        return null;
    }

    public static String getDay(int value) {
        GregorianCalendar gc = new GregorianCalendar();

        gc.setFirstDayOfWeek(1);
        if (gc.get(Calendar.DAY_OF_WEEK) <= value) {
            gc.set(Calendar.DAY_OF_WEEK, value);
            return new SimpleDateFormat("d MMM").format(gc.getTime());
        }
        else {
            gc.add(Calendar.DAY_OF_MONTH, 7);
            gc.set(Calendar.DAY_OF_WEEK, value);
            return new SimpleDateFormat("d MMM").format(gc.getTime());
        }
    }
}
