import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.Thread.sleep;

public class Application {

    private static final String PROPERTIES_FILE = "config.properties";

    public static void main(String[] args) throws IOException {
        System.out.println(new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss | ").format(new Date()) + "I'm BOTMAN!");
        Properties properties = readProperties();
        GroupActor groupActor = createGroupActor(properties);
        int i = 0;

        while(true) {
            i++;
            try {
                HttpTransportClient httpClient = HttpTransportClient.getInstance();
                VkApiClient vk = new VkApiClient(httpClient);

                CallbackApiLongPollHandler handler = new CallbackApiLongPollHandler(vk, groupActor, i);
                handler.run();
            }
            catch (Exception e) {
                System.out.println(new SimpleDateFormat("d.M.yyyy - H:m:s | ").format(new Date()) + "ANOMALY\n" + e.getMessage());
            }
            try {
                sleep(60000);
            }
            catch (InterruptedException e) {
                System.out.println(new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss | ").format(new Date()) + "I can not fall asleep\n");
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
            return properties;
        } catch (IOException e) {
            throw new RuntimeException("Incorrect properties file");
        } finally {
            inputStream.close();
        }
    }
}