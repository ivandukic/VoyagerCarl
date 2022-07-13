package agents;

import com.mindsmiths.ruleEngine.model.Agent;
import com.mindsmiths.telegramAdapter.TelegramAdapterAPI;
import com.mindsmiths.gpt3.GPT3AdapterAPI;
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
import java.util.Date;

@Getter
@Setter
public class Carl extends Agent {
    private List<String> memory = new ArrayList<>();
    private int MAX_MEMORY = 6;
    private boolean requestMode = false;
    private String rapidApiKey = "";
    private HashMap<String, Double> oldResults = new HashMap<String, Double>();
    private String savedRequest = "";
    private boolean redoRequestFlag = false;
    private Date lastRequestTime;

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
//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

    public void helpMessage() {
        sendMessage("- Conversation mode = in this mode you can talk with me and ask me anything you want.\n"
                   +"- Request mode = in this mode you can only send me your requests and I will provide"
                      +" you with a list of available accommodation."
                      +" If you want to go back to Conversation mode type \"$exit\".\n"
                    +"\n"
                    +"OPTIONS:\n"
                    +"- $new-request = activates Request mode.\n"
                    +"- $exit = leaves Request mode and returns back to Conversation mode.\n"
                    +"- $help = displays this message."
                    +"- $stop = stops tracking your request");
    }

    public void newRequestIntro(){
        sendMessage("-> Request mode activated.\n" 
                   +"(Type \"$exit\" to go back to Conversation mode.)\n"
                   +"All parameters are required! If no child is traveling, enter \"0\" for"
                   +" \"Number of children\" and \"Children ages\" parameters.\n"
                   +"\n"
                   +"Please enter your request in the following form:\n"
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
            System.out.println("getRapidApiKey error!");
            return "0";
          }
        return rapidApiKey;
    }

    public String getDestinationId(String userDest){
        String destId = "";
        String url;
        String rapidApiKey = getRapidApiKey();
        JSONArray locationJson = new JSONArray();

        if (rapidApiKey.equals("0")){
            return "0";
        }
    
        url = String.format("https://booking-com.p.rapidapi.com/v1/hotels/locations?name=%s&locale=en-gb", userDest);
    
        HttpRequest httpRequest = HttpRequest.newBuilder()
		    .uri(URI.create(url))
		    .header("X-RapidAPI-Key", rapidApiKey)
		    .header("X-RapidAPI-Host", "booking-com.p.rapidapi.com")
		    .method("GET", HttpRequest.BodyPublishers.noBody())
		    .build();
    
        try{
            HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            locationJson = new JSONArray(response.body());
        }
        catch(Exception e){
            System.out.println("getDestinationId error: exception!");
            return "0";
        }
    
        if (locationJson.length() == 0){
            System.out.println("getDestinationId error: destination not found!");
            return "0";
        }
    
        for (int i = 0; i < locationJson.length(); i++){
            JSONObject tempObj;
            tempObj = locationJson.getJSONObject(i);

            if (tempObj.getString("dest_type").trim().toLowerCase().equals("city") && tempObj.getString("city_name").trim().toLowerCase().equals(userDest)){
                destId = tempObj.getString("dest_id");
            }
        }
        return destId;
    }

    public HashMap<String,String> createUserMap (String request){
        HashMap<String, String> userMap = new HashMap<String, String>();
        HashMap<String, String> emptyMap = new HashMap<String, String>();
        int numOfParams = 9;
        char lastChar = request.charAt(request.length()-1);
            
        boolean condition1 = (lastChar == ';'); 
        boolean condition2 = (request.length() - request.replace(";","").length()) == numOfParams;
        
        if ((condition1 && condition2) == false){
            System.out.println("createUserMap erorr: conditions not satisfied!");
            return emptyMap; 
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
                System.out.print("createUserMap erorr: exception!");
                return emptyMap;
            }
        }
        return userMap;
    }

    public String createRequestUrl(String request){
        String requestUrl = "";
        int numOfChildren;
        HashMap<String, String> userMap = createUserMap(request);
        
        if (userMap.isEmpty()){
            return "0";
        }
        String destId = getDestinationId(userMap.get("city"));

        if (destId.equals("0")){
            return "0";
        }

        try{
            requestUrl = String.format("https://booking-com.p.rapidapi.com/v1/hotels/search?room_number=%s&checkin_date=%s&filter_by_currency=%s&order_by=popularity&adults_number=%s&locale=en-gb&dest_type=city&dest_id=%s&units=metric&checkout_date=%s", userMap.get("number of rooms"),userMap.get("checkin date"), userMap.get("currency").toUpperCase(),userMap.get("number of adults"),destId,userMap.get("checkout date"));
            numOfChildren = Integer.parseInt(userMap.get("number of children"));
            String childString;
            
            if (numOfChildren>0){
                String[] childrenAges = userMap.get("children ages").split(",",0);
                childString = "&children_ages=" + childrenAges[0];
                for (int i = 1; i < numOfChildren; i++){
                    childString += String.format("%%2C%s", childrenAges[i]);
                }
                
                childString += String.format("&children_number=%s", numOfChildren);
                requestUrl += childString;
            }
        }
        catch (Exception e){
            System.out.println("createRequestUrl error: exception!");
            return "0";
        }
        return requestUrl;
    }

    public String getDataString(String request){
        String requestUrl = createRequestUrl(request);
        String rapidApiKey = getRapidApiKey();
        String dataString = "";

        if (requestUrl.equals("0") || rapidApiKey.equals("0")){
            return "0";
        }
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
		.uri(URI.create(requestUrl))
		.header("X-RapidAPI-Key", rapidApiKey)
		.header("X-RapidAPI-Host", "booking-com.p.rapidapi.com")
		.method("GET", HttpRequest.BodyPublishers.noBody())
		.build();
        
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            dataString = response.body();
        }
        catch(Exception e) {
            System.out.println("getDataString error: exception!");
            return "0";
        }
        return dataString;
    }
   
    public void newRequest(String request){
        savedRequest = request;
        oldResults.clear();
        HashMap<String, String> userMap = createUserMap(request);

        if (userMap.isEmpty()){
            sendMessage("Please check your input.");
            return;
        }

        Double maxPrice = Double.parseDouble(userMap.get("max budget"));
        String dataString = getDataString(request);

        if (dataString.equals("0")){
            sendMessage("Please check your input.");
            return;
        }

        JSONObject dataJson = new JSONObject(dataString);    
        JSONArray resultArray = dataJson.getJSONArray("result");  
        
        for (int i = 0; i < resultArray.length(); i++){
            JSONObject tempObj = resultArray.getJSONObject(i);
            Double grossPrice = tempObj.getJSONObject("price_breakdown").getDouble("all_inclusive_price");
            String hotelId = String.valueOf(tempObj.getInt("hotel_id"));

            if (Double.compare(grossPrice, maxPrice) < 0){
                oldResults.put(hotelId, grossPrice);

                String hotelName = tempObj.getString("hotel_name");
                String hotelUrl = tempObj.getString("url");
                String result = "Accommodation: " + hotelName + "\n"
                                 +"Price: " + Double.toString(grossPrice) + " " + userMap.get("currency").toUpperCase() + "\n"
                                 +"URL: " + hotelUrl;
                sendMessage(result);
            }
        }
    }

    public void redoRequest(){
        HashMap<String, String> userMap = createUserMap(savedRequest);

        if (userMap.isEmpty()){
            sendMessage("Something went wrong.");
            return;
        }

        Double maxPrice = Double.parseDouble(userMap.get("max budget"));
        String dataString = getDataString(savedRequest);

        if (dataString.equals("0")){
            sendMessage("Something went wrong!");
            return;
        }

        JSONObject dataJson = new JSONObject(dataString);    
        JSONArray resultArray = dataJson.getJSONArray("result");

        for (int i = 0; i < resultArray.length(); i++){
            JSONObject tempObj = resultArray.getJSONObject(i);
            Double grossPrice = tempObj.getJSONObject("price_breakdown").getDouble("all_inclusive_price");
            String hotelId = String.valueOf(tempObj.getInt("hotel_id"));
            
            if (oldResults.containsKey(hotelId) && (Double.compare(oldResults.get(hotelId), grossPrice)==0)){
                sendMessage("Same.");
                continue;
            }

            else if (oldResults.containsKey(hotelId)){
                oldResults.put(hotelId, grossPrice);
                String hotelName = tempObj.getString("hotel_name");
                String hotelUrl = tempObj.getString("url");
                String result = hotelName + "has new price!\n"
                +"New price : " + Double.toString(grossPrice) + " " + userMap.get("currency").toUpperCase() + "\n"
                +"Old price : " + Double.toString(oldResults.get(hotelId)) + " " + userMap.get("currency").toUpperCase() + "\n"
                +"URL: " + hotelUrl;
                sendMessage(result);
                continue;
            }

            else if (Double.compare(grossPrice, maxPrice) < 0){
                oldResults.put(hotelId, grossPrice);
                String hotelName = tempObj.getString("hotel_name");
                String hotelUrl = tempObj.getString("url");
                String result = "New accommodation found!\n"
                                 +"Accommodation: " + hotelName + "\n"
                                 +"Price: " + Double.toString(grossPrice) + " " + userMap.get("currency").toUpperCase() + "\n"
                                 +"URL: " + hotelUrl;
                sendMessage(result);
            }
        }
    }
}