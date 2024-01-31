package org.folio.fqm.utils;


import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ItemStatusEnumTest {

  @Test
  public void checkNumberOfItemStatuesDoNotChanged() {
    try {

      URL url = new URL("https://raw.githubusercontent.com/folio-org/mod-inventory-storage/master/ramls/item.json");

      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");

      int responseCode = connection.getResponseCode();

      if (responseCode == HttpURLConnection.HTTP_OK) {
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
          content.append(inputLine);
        }
        in.close();

        // Parse JSON content
        JSONObject json = new JSONObject(content.toString());

        // Your specific JSON content checks/assertions go here
        Assertions.assertNotNull(json);
        JSONObject properties = (JSONObject) json.get("properties");
        Assertions.assertNotNull(properties);
        JSONObject status = (JSONObject) properties.get("status");
        Assertions.assertNotNull(status);
        JSONObject statusProperties = (JSONObject) status.get("properties");
        Assertions.assertNotNull(statusProperties);
        JSONObject statusItemsNames = (JSONObject) statusProperties.get("name");
        Assertions.assertNotNull(statusItemsNames);
        JSONArray anEnum = (JSONArray) statusItemsNames.get("enum");
        Assertions.assertNotNull(anEnum);
        Assertions.assertEquals(21, anEnum.length());


      } else {
        Assertions.fail("Failed to fetch content from the external URL. Response Code: " + responseCode);
      }

      connection.disconnect();

    } catch (Exception e) {
      Assertions.fail("An exception occurred: " + e.getMessage());
    }
  }
}
