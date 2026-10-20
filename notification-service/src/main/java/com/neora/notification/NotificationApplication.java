package com.neora.notification;
import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"com.neora.notification","com.neora.shared"})public class NotificationApplication{public static void main(String[]a){SpringApplication.run(NotificationApplication.class,a);}}
