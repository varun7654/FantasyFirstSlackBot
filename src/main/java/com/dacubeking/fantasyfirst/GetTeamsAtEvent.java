package com.dacubeking.fantasyfirst;

import com.dacubeking.fantasyfirst.game.Game.Team;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class GetTeamsAtEvent {

    private final String apiKey;

    public GetTeamsAtEvent(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<Team> getTeamsAtEvent(String eventCode) {
        List<Team> teamNumbers = new ArrayList<>();

        try {
            // Create the URL for the API request
            String apiUrl = "https://www.thebluealliance.com/api/v3/event/" + eventCode + "/teams/simple";
            URL url = new URL(apiUrl);

            // Open a connection to the URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Java TBA Teams Fetcher");
            connection.setRequestProperty("X-TBA-Auth-Key", apiKey);

            // Get the response code
            int responseCode = connection.getResponseCode();

            // Check if the request was successful (HTTP status 200)
            if (responseCode == 200) {
                // Read the response from the API
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                StringBuilder response = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Parse the JSON response using Gson
                JsonArray jsonArray = JsonParser.parseString(response.toString()).getAsJsonArray();
                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();
                    String teamNumber = jsonObject.get("team_number").getAsString();
                    teamNumbers.add(new Team(teamNumber));
                }
            } else {
                System.err.println("Error: Unable to retrieve teams. HTTP Response Code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return teamNumbers;
    }

    public static void main(String[] args) {
        // Replace with your API key
        String apiKey = "qWWfZIXqIVGwZoLnZxgI7Jq2rBmLQD2KQZ5pm6y73pF6xN3j9F2D9lP0sdtnqBiy";

        // Replace with the event code for the event you want to retrieve teams from
        String eventCode = "2022cala";

        GetTeamsAtEvent teamsFetcher = new GetTeamsAtEvent(apiKey);
        List<Team> teams = teamsFetcher.getTeamsAtEvent(eventCode);

        if (!teams.isEmpty()) {
            System.out.println("Teams for event " + eventCode + ": " + teams);
        }
    }
}
