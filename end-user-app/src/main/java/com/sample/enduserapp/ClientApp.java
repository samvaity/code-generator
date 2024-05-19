package com.sample.enduserapp;

import com.service.clientlibrary.ClientLibrary;
import io.clientcore.core.http.models.Response;
import io.clientcore.core.util.binarydata.BinaryData;

import java.io.IOException;

/**
 * Hello world!
 *
 */
public class ClientApp {
    public static void main( String[] args ) {
        ClientLibrary clientLibrary = new ClientLibrary();


        try(Response<Void> voidResponse = clientLibrary.createAttachment(1, "application/json", BinaryData.fromString("Hello, World!"))) {
            System.out.println("Response code: " + voidResponse.getStatusCode());
        } catch (IOException e) {
            System.out.println("Exception: " + e.getMessage());
        }

        try(BinaryData data = clientLibrary.list(1, "application/json").getValue()) {
            System.out.println("Data: " + data);
        } catch (IOException e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }
}
