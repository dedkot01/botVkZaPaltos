import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static java.lang.Thread.sleep;

public class Application {

    private static final String PROPERTIES_FILE = "config.properties";

    public static void main(String[] args) throws IOException, ClientException, ApiException {
        System.out.println("I'm BOTMAN!");
        Properties properties = readProperties();
        GroupActor groupActor = createGroupActor(properties);

        while(true) {
            try {
                HttpTransportClient httpClient = HttpTransportClient.getInstance();
                VkApiClient vk = new VkApiClient(httpClient);

                CallbackApiLongPollHandler handler = new CallbackApiLongPollHandler(vk, groupActor);
                handler.run();
            }
            catch (Exception e) {
                System.out.println("ANOMALY\n" + e.getMessage());
            }
            try {
                sleep(60000);
            }
            catch (InterruptedException e) {
                System.out.println("I can not fall asleep\n");
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