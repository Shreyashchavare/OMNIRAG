package com.omragul.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IdentityServiceApplication {

	public static void main(String[] args) {
        System.out.println("user.timezone = " +
                System.getProperty("user.timezone"));

        System.out.println("default timezone = " +
                java.util.TimeZone.getDefault().getID());

        SpringApplication.run(IdentityServiceApplication.class, args);
	}

}
