package agents;

import com.mindsmiths.ruleEngine.model.Agent;
import com.mindsmiths.telegramAdapter.TelegramAdapterAPI;
import com.mindsmiths.gpt3.GPT3AdapterAPI;
import com.mindsmiths.gpt3.completion.GPT3Completion;
import com.mindsmiths.ruleEngine.util.Log;
import java.util.ArrayList;
import java.util.List;
import lombok.*;


@Getter
@Setter
public class Carl extends Agent {
    private List<String> memory = new ArrayList<>();
    private int MAX_MEMORY = 6;

    public Carl() {
    }

    public Carl(String connectionName, String connectionId) {
        super(connectionName, connectionId);
    }

    public void sendMessage(String text) {
        String chatId = getConnections().get("telegram");
        TelegramAdapterAPI.sendMessage(chatId, text);
    }

    private void trimMemory() {
        if (memory.size() > MAX_MEMORY + 1)
            memory = memory.subList(memory.size() - 1 - MAX_MEMORY, memory.size());
    }

    public void addMessageToMemory(String sender, String text){
        memory.add(String.format("%s: %s\n", sender, text));
        trimMemory();
    }

    public void askGPT3() {
        String intro = "You are a friendly travel agent Carl. Your task is to answer questions"
                      + " about destinations, and hotels and give your honest opinions.\n" 
                      + "Human: What are the best places to visit in the Caribbean?\n"
                      + "Carl: The best places to visit in the Caribbean are the Bahamas, Jamaica,"
                      + " and the Dominican Republic.\n";
        simpleGPT3Request(intro + String.join("\n", memory) + "\nCarl:");
    }

    public void simpleGPT3Request(String prompt) {
        Log.info("Prompt for GPT-3:\n" + prompt);
        GPT3AdapterAPI.complete(
            prompt, // input prompt
            "text-davinci-001", // model
            150, // max tokens
            0.9, // temperature
            1.0, // topP
            1, // N
            null, // logprobs
            false, // echo
            List.of("Human:", "Carl:"), // STOP words
            0.6, // presence penalty
            0.0, // frequency penalty
            1, // best of
            null // logit bias
        );
    }
}