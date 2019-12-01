import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.objects.messages.*;
import java.util.LinkedList;
import java.util.List;

public class UpdateDbThread extends Thread {

    private VkApiClient vk;
    private GroupActor groupActor;

    private Keyboard updateKeyboard;

    public UpdateDbThread(VkApiClient client, GroupActor actor) {
        vk = client;
        groupActor = actor;

        List<List<KeyboardButton>> listKey = new LinkedList();
        List<KeyboardButton> row1 = new LinkedList<>();

        KeyboardButton sendQueryKey = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT)
                        .setLabel("ОБНОВИТЬ"))
                .setColor(KeyboardButtonColor.POSITIVE);
        row1.add(sendQueryKey);
        listKey.add(row1);

        updateKeyboard = new Keyboard().setOneTime(true).setButtons(listKey);
    }

    public void run() {
        while(true) {
            try {
                vk.messages().send(groupActor)
                        .message("ОБНОВИ МЕНЯ!")
                        .keyboard(updateKeyboard)
                        .randomId(0).peerId(71990175).execute();
                sleep(3600000);
            } catch (Exception e) {
                System.out.println("Проблема в нитке обновления со сном\n" + e.getMessage());
            }

        }
    }
}
