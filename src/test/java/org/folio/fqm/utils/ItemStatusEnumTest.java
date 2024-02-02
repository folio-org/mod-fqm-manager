package org.folio.fqm.utils;


import org.apache.commons.io.input.AutoCloseInputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

class ItemStatusEnumTest {

  @Test
  void checkNumberOfItemStatuesDoNotChanged() {
    String content = null;
    try {

      URL url = new URL("https://raw.githubusercontent.com/folio-org/mod-inventory-storage/master/ramls/item.json");

      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(2000);

      int responseCode = connection.getResponseCode();
      //Make sure that the service returns content; otherwise, skip the test and do not mark it as failed
      Assumptions.assumeTrue(responseCode == HttpURLConnection.HTTP_OK);

      InputStream in = AutoCloseInputStream.builder().setInputStream(connection.getInputStream()).get();
      content = new String(in.readAllBytes(), StandardCharsets.UTF_8);

      connection.disconnect();
    } catch (IOException e) {
      Assumptions.abort("The test is aborted because https://raw.githubusercontent.com/ is not reachable " + e.getMessage());
    }

    // Parse JSON content

    try {
      JSONObject json = new JSONObject(content);

      // Check that the number of statuses has not changed.
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

    } catch (JSONException e) {
      Assumptions.abort("The test is aborted because https://raw.githubusercontent.com/ did not return proper JSON content " + e.getMessage());
    }
  }
}
