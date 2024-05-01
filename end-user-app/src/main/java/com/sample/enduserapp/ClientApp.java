package com.sample.enduserapp;

import com.service.clientlibrary.ClientLibrary;
import io.clientcore.core.http.models.Response;

import java.io.IOException;
import java.util.List;

/**
 * Hello world!
 *
 */
public class ClientApp {
    public static void main( String[] args ) {
        ClientLibrary clientLibrary = new ClientLibrary();
        List<String> secretKeys = clientLibrary.getKeys("secretKeys");
        System.out.println(secretKeys);

        try(Response<Void> voidResponse = clientLibrary.testMethod()) {
            System.out.println("Response code: " + voidResponse.getStatusCode());
        } catch (IOException e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }
}
