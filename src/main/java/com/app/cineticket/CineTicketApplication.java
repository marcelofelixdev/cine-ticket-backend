package com.app.cineticket;

import com.app.cineticket.config.AppTimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CineTicketApplication {

    public static void main(String[] args) {
        AppTimeZone.apply();
        SpringApplication.run(CineTicketApplication.class, args);
    }

}
