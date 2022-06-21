package agents;

import com.mindsmiths.ruleEngine.model.Agent;
import com.mindsmiths.telegramAdapter.TelegramAdapterAPI;
import com.mindsmiths.gpt3.GPT3AdapterAPI;
import com.mindsmiths.gpt3.completion.GPT3Completion;
import com.mindsmiths.ruleEngine.util.Log;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import java.net.http.*;
import java.net.URI;
import org.json.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class Carl extends Agent {
    private List<String> memory = new ArrayList<>();
    private int MAX_MEMORY = 6;
    private boolean requestMode = false;
    private String rapidApiKey = "";

    public Carl() {
    }

    public Carl(String connectionName, String connectionId) {
        super(connectionName, connectionId);
    }

    public void sendMessage(String text) {
        String chatId = getConnections().get("telegram");
        TelegramAdapterAPI.sendMessage(chatId, text);
    }

    public void initMessage() {
        sendMessage("Travel agent VoyagerCarl, or just Carl, will make your trip planning"
                    +" as fast as possible so that you can concentrate on exciting stuff" 
                    +" and have a great time visiting new places :).\n"
                    +"\n"
                    +"Type \" $help \" (in Conversation mode) to list Carl's avaible features.\n"
                    +"(You are currently in Conversation mode.)");
    }

    public void helpMessage() {
        sendMessage("-> Conversation mode = in this mode you can talk with Carl and ask him anything you want."
                    +" This mode is active by default.\n"
                    +"\n"
                    +"-> Request mode = in this mode Carl will first send you request form and provide"
                    +" you with all informations on how to get your list of avaible accommodation."
                    +" Note that in this mode you can't talk to Carl. If you want to get back to Conversation"
                    +" mode type \"$exit\".\n"
                    +"\n"
                    +"OPTIONS:\n"
                    +"-> $new-request = opens Request mode.\n"
                    +"-> $exit = leaves Request mode returns back to Conversation mode.\n"
                    +"-> $help = displays this message.");
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

    public void sendRequest(){
        HttpRequest request = HttpRequest.newBuilder()
		.uri(URI.create("https://booking-com.p.rapidapi.com/v1/hotels/search?room_number=1&checkin_date=2022-07-20&filter_by_currency=EUR&order_by=popularity&adults_number=2&locale=en-gb&dest_type=city&dest_id=-82860&units=metric&checkout_date=2022-07-30&page_number=0"))
		.header("X-RapidAPI-Key", "")
		.header("X-RapidAPI-Host", "booking-com.p.rapidapi.com")
		.method("GET", HttpRequest.BodyPublishers.noBody())
		.build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            sendMessage("Everything is ok!");
        }
        catch(Exception e) {
            sendMessage("Something went wrong. Please check your input.");
        }
    }

    public void newRequestIntro(){
        sendMessage("(Request mode entered! Type \"$exit\" to go back to Conversation mode.)\n"
                    +"All parameters are required. If no child is traveling, enter \"0\" for"
                    +" \"Number of children\" and \"Children ages\" parameters.\n"
                    +"Please enter your requests in the following form:\n"
                    +"City:Dubrovnik;\n"
                    +"Checkin date:YEAR-MM-DD;\n"
                    +"Checkout date:YEAR-MM-DD;\n"
                    +"Currency:EUR;\n"
                    +"Max budget:1000;\n"
                    +"Number of rooms:1;\n"
                    +"Number of adults:2;\n"
                    +"Number of children:2;\n"
                    +"Children ages:5,1;");
    }

    public String getRapidApiKey(){
        try {
            File myObj = new File("/app/.rapidApiKey");
            Scanner myReader = new Scanner(myObj);
            rapidApiKey = myReader.nextLine();
            myReader.close();
          } 
          catch (FileNotFoundException e) {
            sendMessage("An error occurred.");
            e.printStackTrace();
          }
        return rapidApiKey;
    }

    public String cityLocation(String userCity){
        String destId = "";
        String url;
        String rapidApiKey = getRapidApiKey();
        JSONArray locationJson = new JSONArray();
    
        url = String.format("https://booking-com.p.rapidapi.com/v1/hotels/locations?name=%s&locale=en-gb", userCity);
    
        HttpRequest request = HttpRequest.newBuilder()
		    .uri(URI.create(url))
		    .header("X-RapidAPI-Key", rapidApiKey)
		    .header("X-RapidAPI-Host", "booking-com.p.rapidapi.com")
		    .method("GET", HttpRequest.BodyPublishers.noBody())
		    .build();
    
        try{
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            locationJson = new JSONArray(response.body());
        }
        catch(Exception e){
            sendMessage("Ups! Something went wrong!");
        }
    
        if (locationJson.length() == 0){
            sendMessage(String.format("We couldn't find \"%s\" city. Please check your input.", userCity));
        }
    
        for (int i = 0; i < locationJson.length(); i++){
            JSONObject tempObj;
            tempObj = locationJson.getJSONObject(i);

            if (tempObj.getString("dest_type").trim().toLowerCase().equals("city") && tempObj.getString("city_name").trim().toLowerCase().equals(userCity)){
                destId = tempObj.getString("dest_id");
            }
        }
        return destId;
    }

    public Map<String,String> createUserMap (String request){
        HashMap<String, String> userMap = new HashMap<String, String>();
        int numOfChildren;
        int numOfParams = 9;
        char lastChar = request.charAt(request.length()-1);
            
        boolean condition1 = (lastChar == ';'); 
        boolean condition2 = (request.length() - request.replace(";","").length()) == numOfParams;
        
        if ( (condition1 && condition2) == false){
            sendMessage("Please check your input.");
            return userMap;
        }
        
        String[] requestSplitted = request.split(";", numOfParams);
        
        for (String param : requestSplitted){
            param = param.replace(";","");
            param = param.replace("\n","");
            String[] paramSplitted = param.split(":",2);
            try{
                userMap.put(paramSplitted[0].trim().toLowerCase(),paramSplitted[1].trim().toLowerCase());
            }
            catch (Exception e){
                sendMessage("Please check your input!");
            }
        }
        return userMap;
    }
}